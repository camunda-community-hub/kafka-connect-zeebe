/*
 * Copyright © 2019 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.kafka.connect.source;

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.response.ActivatedJob;
import io.zeebe.client.api.worker.JobClient;
import io.zeebe.client.api.worker.JobWorker;
import io.zeebe.kafka.connect.util.VersionInfo;
import io.zeebe.kafka.connect.util.ZeebeClientConfigDef;
import io.zeebe.protocol.Protocol;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.source.SourceRecord;
import org.apache.kafka.connect.source.SourceTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Source task for Zeebe which activates jobs, publishes results, and completes jobs */
public class ZeebeSourceTask extends SourceTask {
  private static final Logger LOGGER = LoggerFactory.getLogger(ZeebeSourceTask.class);
  private static final int JOB_QUEUE_TIMEOUT_MS = 5000;
  private static final int JOBS_QUEUE_CAPACITY = 10_000;

  private final AtomicReference<TaskState> taskState;
  private final BlockingQueue<ActivatedJob> jobs;

  private String jobHeaderTopic;
  private ZeebeClient client;
  private List<JobWorker> workers;
  private int maxJobsToActivate;

  public ZeebeSourceTask() {
    this.taskState = new AtomicReference<>(TaskState.STOPPED);
    this.jobs = new ArrayBlockingQueue<>(JOBS_QUEUE_CAPACITY);
  }

  @Override
  public void start(final Map<String, String> props) {
    final ZeebeSourceConnectorConfig config = new ZeebeSourceConnectorConfig(props);
    final List<String> jobTypes = config.getList(ZeebeSourceConnectorConfig.JOB_TYPES_CONFIG);

    maxJobsToActivate = config.getInt(ZeebeSourceConnectorConfig.MAX_JOBS_TO_ACTIVATE_CONFIG);
    client = buildClient(config);
    workers =
        jobTypes.stream().map(type -> this.newWorker(config, type)).collect(Collectors.toList());

    if (!taskState.compareAndSet(TaskState.STOPPED, TaskState.STARTED)) {
      throw new IllegalArgumentException(
          String.format(
              "Expected task to be stopped before start() is called, but current state is '%s'",
              taskState.get()));
    }
  }

  @SuppressWarnings("squid:S1168")
  @Override
  public List<SourceRecord> poll() throws InterruptedException {
    final List<SourceRecord> records = new ArrayList<>();
    if (taskState.compareAndSet(TaskState.STARTED, TaskState.POLLING)) {
      drainQueueBlocking().stream().map(this::transformJob).forEach(records::add);
      taskState.compareAndSet(TaskState.POLLING, TaskState.STARTED);
    }

    // close all resources in case we're already stopped
    closeIfStopped();

    // poll interface specifies to return null instead of empty
    if (records.isEmpty()) {
      return null;
    }

    LOGGER.debug("Publishing {} source records", records.size());
    return records;
  }

  @Override
  public void stop() {
    taskState.updateAndGet(this::closeUnlessPolling);
  }

  @Override
  public void commitRecord(final SourceRecord record) throws InterruptedException {
    final long key = (Long) record.sourceOffset().get("key");
    try {
      client.newCompleteCommand(key).send().join();
    } catch (final CancellationException e) {
      LOGGER.debug("Complete command cancelled probably because task is stopping", e);
    }
  }

  @Override
  public String version() {
    return VersionInfo.getVersion();
  }

  private void closeIfStopped() {
    if (taskState.get() == TaskState.STOPPED) {
      close();
    }
  }

  private TaskState closeUnlessPolling(final TaskState currentState) {
    if (currentState != TaskState.POLLING) {
      close();
    }

    return TaskState.STOPPED;
  }

  private void close() {
    LOGGER.debug("Closing resources...");
    workers.forEach(JobWorker::close);
    workers.clear();

    if (client != null) {
      client.close();
      client = null;
    }
  }

  private List<ActivatedJob> drainQueueBlocking() throws InterruptedException {
    final List<ActivatedJob> activatedJobs = new ArrayList<>();

    // if no jobs available, block a few seconds until we receive one
    if (jobs.isEmpty()) {
      LOGGER.trace("No jobs available, block for {}ms", JOB_QUEUE_TIMEOUT_MS);
      final ActivatedJob job = jobs.poll(JOB_QUEUE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
      if (job != null) {
        activatedJobs.add(job);
      }
    }

    if (!activatedJobs.isEmpty()) {
      jobs.drainTo(activatedJobs, maxJobsToActivate - 1);
    }

    return activatedJobs;
  }

  private ZeebeClient buildClient(final ZeebeSourceConnectorConfig config) {
    return ZeebeClient.newClientBuilder()
        .brokerContactPoint(config.getString(ZeebeClientConfigDef.BROKER_CONTACTPOINT_CONFIG))
        .numJobWorkerExecutionThreads(1)
        .build();
  }

  private SourceRecord transformJob(final ActivatedJob job) {
    final String topic = job.getCustomHeaders().get(jobHeaderTopic);
    final Map<String, Integer> sourcePartition =
        Collections.singletonMap("partitionId", Protocol.decodePartitionId(job.getKey()));
    // a better sourceOffset would be the position but we don't have it here unfortunately
    // key is however a monotonically increasing value, so in a sense it can provide a good
    // approximation of an offset
    final Map<String, Long> sourceOffset = Collections.singletonMap("key", job.getKey());
    return new SourceRecord(
        sourcePartition,
        sourceOffset,
        topic,
        Schema.INT64_SCHEMA,
        job.getKey(),
        Schema.STRING_SCHEMA,
        job.toJson());
  }

  private boolean isJobInvalid(final ActivatedJob job) {
    final String topic = job.getCustomHeaders().get(jobHeaderTopic);
    return topic == null || topic.isEmpty();
  }

  // eventually allow this behaviour here to be configurable: whether to ignore, fail, or
  // throw an exception here on invalid jobs
  // should we block until the request is finished?
  private void handleInvalidJob(final JobClient client, final ActivatedJob job) {
    LOGGER.debug("Failing invalid job: {}", job);
    client
        .newFailCommand(job.getKey())
        .retries(job.getRetries() - 1)
        .errorMessage(
            String.format(
                "Expected a kafka topic to be defined as a custom header with key '%s', but none found",
                jobHeaderTopic))
        .send();
  }

  private JobWorker newWorker(final ZeebeSourceConnectorConfig config, final String type) {
    jobHeaderTopic = config.getString(ZeebeSourceConnectorConfig.JOB_HEADER_TOPICS_CONFIG);
    final List<String> jobVariables =
        config.getList(ZeebeSourceConnectorConfig.JOB_VARIABLES_CONFIG);
    final Duration jobTimeout =
        Duration.ofMillis(config.getLong(ZeebeSourceConnectorConfig.JOB_TIMEOUT_CONFIG));
    final Duration requestTimeout =
        Duration.ofMillis(config.getLong(ZeebeClientConfigDef.REQUEST_TIMEOUT_CONFIG));
    final Duration pollInterval =
        Duration.ofMillis(config.getLong(ZeebeSourceConnectorConfig.POLL_INTERVAL_CONFIG));
    final String workerName = config.getString(ZeebeSourceConnectorConfig.WORKER_NAME_CONFIG);

    return client
        .newWorker()
        .jobType(type)
        .handler(this::onJobActivated)
        .name(workerName)
        .maxJobsActive(maxJobsToActivate)
        .requestTimeout(requestTimeout)
        .timeout(jobTimeout)
        .pollInterval(pollInterval)
        .fetchVariables(jobVariables)
        .open();
  }

  private void onJobActivated(final JobClient client, final ActivatedJob job)
      throws InterruptedException {
    if (isJobInvalid(job)) {
      handleInvalidJob(client, job);
    } else {
      LOGGER.trace("Activating job {}", job);
      try {
        jobs.put(job);
        LOGGER.trace("Activated jobs: {}", jobs.size());
      } catch (final RuntimeException e) {
        LOGGER.error("Failed to append job {} to jobs queue", job, e);
      }
    }
  }

  private enum TaskState {
    STARTED,
    POLLING,
    STOPPED
  }
}
