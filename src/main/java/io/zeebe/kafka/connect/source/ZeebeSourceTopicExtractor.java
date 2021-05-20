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

import io.camunda.zeebe.client.api.response.ActivatedJob;

public class ZeebeSourceTopicExtractor {

  private final String jobHeaderTopic;
  private final String topicHeaderNotFoundErrorMessage;

  public ZeebeSourceTopicExtractor(ZeebeSourceConnectorConfig config) {
    jobHeaderTopic = config.getString(ZeebeSourceConnectorConfig.JOB_HEADER_TOPICS_CONFIG);

    topicHeaderNotFoundErrorMessage =
        String.format(
            "Expected a kafka topic to be defined as a custom header with key '%s', but none found",
            jobHeaderTopic);
  }

  public String extract(ActivatedJob job) {
    return job.getCustomHeaders().get(jobHeaderTopic);
  }

  public String getTopicHeaderNotFoundErrorMessage() {
    return topicHeaderNotFoundErrorMessage;
  }
}
