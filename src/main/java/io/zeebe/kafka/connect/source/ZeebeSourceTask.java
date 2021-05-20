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

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.zeebe.kafka.connect.util.ManagedClient;
import io.zeebe.kafka.connect.util.ManagedClient.AlreadyClosedException;
import io.zeebe.kafka.connect.util.VersionInfo;
import io.zeebe.kafka.connect.util.ZeebeClientHelper;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.source.SourceRecord;
import org.apache.kafka.connect.source.SourceTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Source task for Zeebe which activates jobs, publishes results, and completes jobs */
public class ZeebeSourceTask extends SourceTask {
  private static final Logger LOGGER = LoggerFactory.getLogger(ZeebeSourceTask.class);

  private ManagedClient managedClient;
  private ZeebeSourceTopicExtractor topicExtractor;
  private ZeebeSourceTaskFetcher taskFetcher;
  private ZeebeSourceInflightRegistry inflightRegistry;
  private ZeebeSourceBackoff backoff;

  public ZeebeSourceTask() {}

  @Override
  public void start(final Map<String, String> props) {
    final ZeebeSourceConnectorConfig config = new ZeebeSourceConnectorConfig(props);
    final ZeebeClient client = ZeebeClientHelper.buildClient(config);
    managedClient = new ManagedClient(client);

    topicExtractor = new ZeebeSourceTopicExtractor(config);
    taskFetcher = new ZeebeSourceTaskFetcher(config, topicExtractor);
    inflightRegistry = new ZeebeSourceInflightRegistry(config);
    backoff = new ZeebeSourceBackoff(config);
  }

  @SuppressWarnings("squid:S1168")
  @Override
  public List<SourceRecord> poll() {
    if (!inflightRegistry.hasCapacity()) {
      LOGGER.trace("No capacity left to poll new jobs, returning control to caller after backoff");
      backoff.backoff();
      return null;
    }

    final List<SourceRecord> records =
        inflightRegistry
            .jobTypesWithCapacity()
            .flatMap(this::fetchJobs)
            .map(inflightRegistry::registerJob)
            .map(this::transformJob)
            .collect(Collectors.toList());

    // poll interface specifies to return null instead of empty
    if (records.isEmpty()) {
      LOGGER.trace("Nothing to publish, returning control to caller after backoff");
      backoff.backoff();
      return null;
    }

    LOGGER.debug("Publishing {} source records", records.size());
    return records;
  }

  private Stream<ActivatedJob> fetchJobs(final String jobType) {
    final int amount = inflightRegistry.capacityForType(jobType);
    final Duration requestTimeout = backoff.currentDuration();
    try {
      return managedClient
          .withClient(c -> taskFetcher.fetchBatch(c, jobType, amount, requestTimeout))
          .stream();
    } catch (final AlreadyClosedException e) {
      LOGGER.warn(
          "Expected to activate jobs for type {}, but failed to receive response", jobType, e);

    } catch (final InterruptedException e) {
      LOGGER.warn("Expected to activate jobs for type {}, but was interrupted", jobType);
      Thread.currentThread().interrupt();
    }
    return Stream.empty();
  }

  @Override
  public void stop() {
    managedClient.close();
  }

  @Override
  public void commit() {
    inflightRegistry.unregisterAllJobs().forEach(this::completeJob);
  }

  @Override
  public void commitRecord(final SourceRecord record) {
    final long key = (Long) record.sourceOffset().get("key");
    completeJob(key);
    inflightRegistry.unregisterJob(key);
    backoff.reset();
  }

  private void completeJob(final long key) {
    try {
      managedClient.withClient(c -> c.newCompleteCommand(key).send());
    } catch (final CancellationException e) {
      LOGGER.debug("Complete command cancelled probably because task is stopping", e);
    } catch (final AlreadyClosedException e) {
      LOGGER.debug("Expected to complete job {}, but client is already closed", key);
    } catch (final InterruptedException e) {
      LOGGER.debug("Expected to complete job {}, but was interrupted", key);
      Thread.currentThread().interrupt();
    }
  }

  @Override
  public String version() {
    return VersionInfo.getVersion();
  }

  private SourceRecord transformJob(final ActivatedJob job) {
    final String topic = topicExtractor.extract(job);
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
