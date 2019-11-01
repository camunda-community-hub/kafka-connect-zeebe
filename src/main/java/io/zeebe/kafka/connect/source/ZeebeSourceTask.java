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
import io.zeebe.kafka.connect.util.ManagedClient;
import io.zeebe.kafka.connect.util.ManagedClient.AlreadyClosedException;
import io.zeebe.kafka.connect.util.VersionInfo;
import io.zeebe.kafka.connect.util.ZeebeClientConfigDef;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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

  private final BlockingQueue<ActivatedJob> jobs;

  private String jobHeaderTopic;
  private ManagedClient managedClient;
  private List<JobWorker> workers;
  private int maxJobsToActivate;

  public ZeebeSourceTask() {
    this.jobs = new ArrayBlockingQueue<>(JOBS_QUEUE_CAPACITY);
  }

  @Override
  public void start(final Map<String, String> props) {
    final ZeebeSourceConnectorConfig config = new ZeebeSourceConnectorConfig(props);
    final List<String> jobTypes = config.getList(ZeebeSourceConnectorConfig.JOB_TYPES_CONFIG);
    final ZeebeClient client = buildClient(config);

    maxJobsToActivate = config.getInt(ZeebeSourceConnectorConfig.MAX_JOBS_TO_ACTIVATE_CONFIG);
    managedClient = new ManagedClient(client);
    workers =
        jobTypes
            .stream()
            .map(type -> this.newWorker(config, type, client))
            .collect(Collectors.toList());
  }

  @SuppressWarnings("squid:S1168")
  @Override
  public List<SourceRecord> poll() throws InterruptedException {
    final List<SourceRecord> records =
        drainQueueBlocking().stream().map(this::transformJob).collect(Collectors.toList());

    // poll interface specifies to return null instead of empty
    if (records.isEmpty()) {
      LOGGER.trace("Nothing to publish, returning control to caller");
      return null;
    }

    LOGGER.debug("Publishing {} source records", records.size());
    return records;
  }

  @Override
  public void stop() {
    workers.forEach(JobWorker::close);
    workers.clear();
    managedClient.close();
  }

  @Override
  public void commitRecord(final SourceRecord record) throws InterruptedException {
    final long key = (Long) record.sourceOffset().get("key");
    try {
      managedClient.withClient(c -> c.newCompleteCommand(key).send().join());
    } catch (final CancellationException e) {
      LOGGER.debug("Complete command cancelled probably because task is stopping", e);
    } catch (final AlreadyClosedException e) {
      LOGGER.debug("Expected to complete job {}, but client is already closed", key);
    }
  }

  @Override
  public String version() {
    return VersionInfo.getVersion();
  }

  private List<ActivatedJob> drainQueueBlocking() throws InterruptedException {
    final List<ActivatedJob> activatedJobs = new ArrayList<>();
    boolean jobsAvailable = !jobs.isEmpty();

    // if no jobs available, block a few seconds until we receive one
    if (!jobsAvailable) {
      LOGGER.trace("No jobs available, block for {}ms", JOB_QUEUE_TIMEOUT_MS);
      final ActivatedJob job = jobs.poll(JOB_QUEUE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
      if (job != null) {
        activatedJobs.add(job);
        jobsAvailable = true;
      }
    }

    if (jobsAvailable) {
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
        Collections.singletonMap("partitionId", decodePartitionId(job.getKey()));
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

  private JobWorker newWorker(
      final ZeebeSourceConnectorConfig config, final String type, final ZeebeClient client) {
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

  private boolean isJobInvalid(final ActivatedJob job) {
    final String topic = job.getCustomHeaders().get(jobHeaderTopic);
    return topic == null || topic.isEmpty();
  }

  // eventually allow this behaviour here to be configurable: whether to ignore, fail, or
  // throw an exception here on invalid jobs
  // should we block until the request is finished?
  private Future<Void> handleInvalidJob(final JobClient client, final ActivatedJob job) {
    LOGGER.debug("Failing invalid job: {}", job);
    return client
        .newFailCommand(job.getKey())
        .retries(job.getRetries() - 1)
        .errorMessage(
            String.format(
                "Expected a kafka topic to be defined as a custom header with key '%s', but none found",
                jobHeaderTopic))
        .send();
  }

  private void onJobActivated(final JobClient client, final ActivatedJob job)
      throws InterruptedException, AlreadyClosedException {
    if (isJobInvalid(job)) {
      managedClient.withClient(c -> handleInvalidJob(c, job));
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

  // Copied from Zeebe Protocol as it is currently fixed to Java 11, and the connector to Java 8
  // Should be fixed eventually and we can use the protocol directly again
  private int decodePartitionId(final long key) {
    return (int) (key >> 51);
  }
}
