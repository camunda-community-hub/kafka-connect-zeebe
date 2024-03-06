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
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ZeebeSourceTaskFetcher {

  private static final Logger LOGGER = LoggerFactory.getLogger(ZeebeSourceTaskFetcher.class);

  private final ZeebeSourceTopicExtractor topicExtractor;

  private final Duration jobTimeout;
  private final String workerName;

  ZeebeSourceTaskFetcher(
      final ZeebeSourceConnectorConfig config, final ZeebeSourceTopicExtractor topicExtractor) {
    this.topicExtractor = topicExtractor;

    jobTimeout = Duration.ofMillis(config.getLong(ZeebeSourceConnectorConfig.JOB_TIMEOUT_CONFIG));
    workerName = config.getString(ZeebeSourceConnectorConfig.WORKER_NAME_CONFIG);
  }

  List<ActivatedJob> fetchBatch(
      final ZeebeClient client,
      final String jobType,
      final int amount,
      final Duration requestTimeout) {
    final Map<Boolean, List<ActivatedJob>> jobs =
        activateJobs(client, jobType, amount, requestTimeout).stream()
            .collect(Collectors.partitioningBy(this::isJobValid));

    final List<ActivatedJob> validJobs = jobs.get(true);
    final List<ActivatedJob> invalidJobs = jobs.get(false);

    invalidJobs.forEach(j -> handleInvalidJob(client, j));

    return validJobs;
  }

  private List<ActivatedJob> activateJobs(
      final ZeebeClient client,
      final String jobType,
      final int amount,
      final Duration requestTimeout) {
    LOGGER.trace(
        "Sending activate jobs command for maximal {} jobs of type {} with request timeout {}",
        amount,
        jobType,
        requestTimeout);
    try {
      return client
          .newActivateJobsCommand()
          .jobType(jobType)
          .maxJobsToActivate(amount)
          .workerName(workerName)
          .timeout(jobTimeout)
          .requestTimeout(requestTimeout)
          .send()
          .get()
          .getJobs();
    } catch (final ExecutionException e) {
      LOGGER.warn(
          "Expected to fetch maximal {} jobs for type {}, but failed to do so", amount, jobType, e);
    } catch (final InterruptedException e) {
      LOGGER.warn(
          "Expected to fetch maximal {} jobs for type {}, but was interrupted", amount, jobType);
    }

    return Collections.emptyList();
  }

  // eventually allow this behaviour here to be configurable: whether to ignore, fail, or
  // throw an exception here on invalid jobs
  // should we block until the request is finished?
  private void handleInvalidJob(final ZeebeClient client, final ActivatedJob job) {
    final String topicHeaderNotFoundErrorMessage =
        topicExtractor.getTopicHeaderNotFoundErrorMessage();
    LOGGER.warn(
        "{} for job with key {} and type {}",
        topicHeaderNotFoundErrorMessage,
        job.getKey(),
        job.getType());
    client
        .newFailCommand(job.getKey())
        .retries(job.getRetries() - 1)
        .errorMessage(topicHeaderNotFoundErrorMessage)
        .send();
  }

  private boolean isJobValid(final ActivatedJob job) {
    final String topic = topicExtractor.extract(job);
    return topic != null && !topic.isEmpty();
  }
}
