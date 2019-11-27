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
import io.zeebe.client.ZeebeClientBuilder;
import io.zeebe.client.api.response.ActivatedJob;
import io.zeebe.kafka.connect.util.ManagedClient;
import io.zeebe.kafka.connect.util.ManagedClient.AlreadyClosedException;
import io.zeebe.kafka.connect.util.VersionInfo;
import io.zeebe.kafka.connect.util.ZeebeClientConfigDef;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.source.SourceRecord;
import org.apache.kafka.connect.source.SourceTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Source task for Zeebe which activates jobs, publishes results, and completes jobs */
public class ZeebeSourceTask extends SourceTask {
  static final Logger LOGGER = LoggerFactory.getLogger(ZeebeSourceTask.class);

  private ManagedClient managedClient;

  private int maxJobsToActivate;
  private String jobHeaderTopic;

  private ZeebeSourceTaskBackoff backoff;

  private Map<String, Integer> pendingJobCounts;
  private Map<Long, String> pendingJobTypes;
  private ZeebeSourcePoller poller;

  public ZeebeSourceTask() {}

  @Override
  public void start(final Map<String, String> props) {
    final ZeebeSourceConnectorConfig config = new ZeebeSourceConnectorConfig(props);

    final ZeebeClient client = buildClient(config);
    managedClient = new ManagedClient(client);

    maxJobsToActivate = config.getInt(ZeebeSourceConnectorConfig.MAX_JOBS_TO_ACTIVATE_CONFIG);
    jobHeaderTopic = config.getString(ZeebeSourceConnectorConfig.JOB_HEADER_TOPICS_CONFIG);
    final Duration pollInterval =
        Duration.ofMillis(config.getLong(ZeebeSourceConnectorConfig.POLL_INTERVAL_CONFIG));

    backoff = new ZeebeSourceTaskBackoff(10, pollInterval.toMillis());

    final List<String> jobTypes = config.getList(ZeebeSourceConnectorConfig.JOB_TYPES_CONFIG);
    pendingJobCounts =
        jobTypes.stream().collect(Collectors.toConcurrentMap(Function.identity(), t -> 0));
    pendingJobTypes = new ConcurrentHashMap<>();

    poller = new ZeebeSourcePoller(config);
  }

  @SuppressWarnings("squid:S1168")
  @Override
  public List<SourceRecord> poll() throws InterruptedException {
    if (!hasCapacity()) {
      LOGGER.trace("No capacity left to poll new jobs, returning control to caller after backoff");
      backoff.backoffAndIncrement();
      return null;
    }

    LOGGER.trace("Polling for new jobs");
    final List<SourceRecord> records =
        pendingJobCounts
            .keySet()
            .stream()
            .flatMap(this::poll)
            .map(this::registerJob)
            .map(this::transformJob)
            .collect(Collectors.toList());

    // poll interface specifies to return null instead of empty
    if (records.isEmpty()) {
      backoff.backoffAndIncrement();
      LOGGER.trace("Nothing to publish, returning control to caller");
      return null;
    }

    backoff.reset();

    LOGGER.debug("Publishing {} source records", records.size());
    return records;
  }

  private boolean hasCapacity() {
    return pendingJobCounts.values().stream().anyMatch(v -> v < maxJobsToActivate);
  }

  private Stream<ActivatedJob> poll(final String jobType) {
    final int jobsToActivate = maxJobsToActivate - pendingJobCounts.get(jobType);

    if (jobsToActivate > 0) {
      try {
        final long requestTimeout = backoff.getBackoffInMs();
        final List<ActivatedJob> activatedJobs =
            managedClient.withClient(c -> poller.poll(c, jobType, jobsToActivate, requestTimeout));
        return activatedJobs.stream();
      } catch (final AlreadyClosedException | InterruptedException e) {
        // ignore
      }
    }

    return Stream.empty();
  }

  @Override
  public void stop() {
    managedClient.close();
  }

  @Override
  public void commit() {
    pendingJobTypes.keySet().stream().map(this::unregisterJob).forEach(this::completeJob);
  }

  @Override
  public void commitRecord(final SourceRecord record) {
    final long key = (Long) record.sourceOffset().get("key");
    unregisterJob(key);
    completeJob(key);
  }

  private void completeJob(final long key) {
    try {
      managedClient.withClient(c -> c.newCompleteCommand(key).send());
    } catch (final CancellationException e) {
      LOGGER.debug("Complete command cancelled probably because task is stopping", e);
    } catch (final AlreadyClosedException e) {
      LOGGER.debug("Expected to complete job {}, but client is already closed", key);
    } catch (final InterruptedException e) {
      LOGGER.debug("Interrupted while completing job {}", key);
    } finally {
      backoff.reset();
    }
  }

  @Override
  public String version() {
    return VersionInfo.getVersion();
  }

  private ZeebeClient buildClient(final ZeebeSourceConnectorConfig config) {
    final ZeebeClientBuilder zeebeClientBuilder =
        ZeebeClient.newClientBuilder()
            .brokerContactPoint(config.getString(ZeebeClientConfigDef.BROKER_CONTACTPOINT_CONFIG))
            .numJobWorkerExecutionThreads(1);

    if (config.getBoolean(ZeebeClientConfigDef.USE_PLAINTEXT_CONFIG)) {
      zeebeClientBuilder.usePlaintext();
    }

    return zeebeClientBuilder.build();
  }

  private ActivatedJob registerJob(final ActivatedJob job) {
    pendingJobTypes.put(job.getKey(), job.getType());
    pendingJobCounts.merge(job.getType(), 0, (k, v) -> v + 1);
    return job;
  }

  private long unregisterJob(final long key) {
    final String jobType = pendingJobTypes.remove(key);
    pendingJobCounts.merge(jobType, 1, (k, v) -> v - 1);
    return key;
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

  // Copied from Zeebe Protocol as it is currently fixed to Java 11, and the connector to Java 8
  // Should be fixed eventually and we can use the protocol directly again
  private int decodePartitionId(final long key) {
    return (int) (key >> 51);
  }
}
