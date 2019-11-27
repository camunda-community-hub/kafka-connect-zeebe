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
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZeebeSourcePoller {
  static final Logger LOGGER = LoggerFactory.getLogger(ZeebeSourcePoller.class);

  private final String jobHeaderTopic;
  private final List<String> jobVariables;
  private final Duration jobTimeout;
  private final String workerName;

  public ZeebeSourcePoller(final ZeebeSourceConnectorConfig config) {
    jobHeaderTopic = config.getString(ZeebeSourceConnectorConfig.JOB_HEADER_TOPICS_CONFIG);
    jobVariables = config.getList(ZeebeSourceConnectorConfig.JOB_VARIABLES_CONFIG);
    jobTimeout = Duration.ofMillis(config.getLong(ZeebeSourceConnectorConfig.JOB_TIMEOUT_CONFIG));
    workerName = config.getString(ZeebeSourceConnectorConfig.WORKER_NAME_CONFIG);
  }

  public List<ActivatedJob> poll(
      final ZeebeClient client, final String jobType, final int amount, long requestTimeout) {
    LOGGER.trace("Fetching maximal {} jobs for job type {}", amount, jobType);

    final List<ActivatedJob> activatedJobs = fetchJobs(client, jobType, amount, requestTimeout);
    LOGGER.trace("Activated {} jobs for job type {}", activatedJobs.size(), jobType);

    final Map<Boolean, List<ActivatedJob>> jobs =
        activatedJobs.stream().collect(Collectors.partitioningBy(this::isValidJob));
    final List<ActivatedJob> validJobs = jobs.get(true);
    final List<ActivatedJob> invalidJobs = jobs.get(false);
    LOGGER.trace(
        "Fetch {} valid and {} invalid jobs for job type {}",
        validJobs.size(),
        invalidJobs.size(),
        jobType);

    // fail invalid jobs
    invalidJobs.forEach(j -> failInvalidJob(client, j));

    return validJobs;
  }

  private List<ActivatedJob> fetchJobs(
      final ZeebeClient client, final String jobType, final int amount, long requestTimeout) {
    LOGGER.trace("Sending request with request timeout {}", requestTimeout);
    return client
        .newActivateJobsCommand()
        .jobType(jobType)
        .maxJobsToActivate(amount)
        .workerName(workerName)
        .fetchVariables(jobVariables)
        .timeout(jobTimeout)
        .requestTimeout(Duration.ofMillis(requestTimeout))
        .send()
        .join()
        .getJobs();
  }

  // eventually allow this behaviour here to be configurable: whether to ignore, fail, or
  // throw an exception here on invalid jobs
  // should we block until the request is finished?
  private void failInvalidJob(final ZeebeClient client, final ActivatedJob job) {
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

  private boolean isValidJob(final ActivatedJob job) {
    final String topic = job.getCustomHeaders().get(jobHeaderTopic);
    return topic != null && !topic.isEmpty();
  }
}
