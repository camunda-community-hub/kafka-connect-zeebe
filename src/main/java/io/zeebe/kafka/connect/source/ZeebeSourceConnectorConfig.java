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

import io.zeebe.client.ClientProperties;
import io.zeebe.kafka.connect.util.ZeebeClientConfigDef;
import java.util.Map;
import org.apache.kafka.common.config.AbstractConfig;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.config.ConfigDef.Importance;
import org.apache.kafka.common.config.ConfigDef.Type;
import org.apache.kafka.common.config.ConfigDef.Width;

public class ZeebeSourceConnectorConfig extends AbstractConfig {
  public static final String JOB_TYPES_CONFIG = "job.types";
  static final String WORKER_NAME_CONFIG = ClientProperties.DEFAULT_JOB_WORKER_NAME;
  static final String MAX_JOBS_TO_ACTIVATE_CONFIG = ClientProperties.JOB_WORKER_MAX_JOBS_ACTIVE;
  static final String POLL_INTERVAL_CONFIG = ClientProperties.DEFAULT_JOB_POLL_INTERVAL;
  static final String JOB_TIMEOUT_CONFIG = ClientProperties.DEFAULT_JOB_TIMEOUT;
  static final String JOB_HEADER_TOPICS_CONFIG = "job.header.topics";
  static final String JOB_VARIABLES_CONFIG = "job.variables";

  private static final String WORKER_CONFIG_GROUP = "Job Worker";
  private static final String WORKER_NAME_DEFAULT = "kafka-connector";
  private static final String WORKER_NAME_DOC = "Name of the Zeebe worker that will poll jobs";
  private static final int MAX_JOBS_TO_ACTIVATE_DEFAULT = 100;
  private static final String MAX_JOBS_TO_ACTIVATE_DOC =
      "Maximum number of jobs to fetch at once when a task is polling";
  private static final long POLL_INTERVAL_DEFAULT = 5000;
  private static final String POLL_INTERVAL_DOC = "How often the job worker will poll for new jobs";
  private static final long JOB_TIMEOUT_DEFAULT = 5_000;
  private static final String JOB_TIMEOUT_DOC =
      "How long to wait before the job fetched can be seen by another worker; this should be "
          + "enough time for the task to produce a source record and complete the job";
  private static final String JOB_TYPES_DEFAULT = "kafka";
  private static final String JOB_TYPES_DOC =
      "A comma-separated list of one or more job types to poll for";
  private static final String JOB_HEADER_TOPICS_DEFAULT = "kafka-topic";
  private static final String JOB_HEADER_TOPICS_DOC =
      "Zeebe service task extension header key which determines to what Kafka topic a job should "
          + "be published. The value of the header is expected to be a comma-separated list of "
          + "Kafka topics on which the source record will be published.";
  private static final String JOB_VARIABLES_DEFAULT = "";
  private static final String JOB_VARIABLES_DOC =
      "A comma-separated list of variables to fetch when activating a job. If none given, then "
          + "all variables are fetched.";
  public static final ConfigDef DEFINITIONS = createDefinitions();

  public ZeebeSourceConnectorConfig(final Map<String, String> properties) {
    super(DEFINITIONS, properties);
  }

  private static ConfigDef createDefinitions() {
    final ConfigDef definitions = new ConfigDef();
    ZeebeClientConfigDef.defineClientGroup(definitions);
    defineWorkerGroup(definitions);

    return definitions;
  }

  private static void defineWorkerGroup(final ConfigDef definitions) {
    int order = 0;

    definitions
        .define(
            WORKER_NAME_CONFIG,
            Type.STRING,
            WORKER_NAME_DEFAULT,
            Importance.LOW,
            WORKER_NAME_DOC,
            WORKER_CONFIG_GROUP,
            ++order,
            Width.SHORT,
            "Name")
        .define(
            MAX_JOBS_TO_ACTIVATE_CONFIG,
            Type.INT,
            MAX_JOBS_TO_ACTIVATE_DEFAULT,
            Importance.MEDIUM,
            MAX_JOBS_TO_ACTIVATE_DOC,
            WORKER_CONFIG_GROUP,
            ++order,
            Width.SHORT,
            "Max jobs to activate")
        .define(
            POLL_INTERVAL_CONFIG,
            Type.LONG,
            POLL_INTERVAL_DEFAULT,
            Importance.MEDIUM,
            WORKER_CONFIG_GROUP,
            POLL_INTERVAL_DOC,
            ++order,
            Width.SHORT,
            "Poll interval")
        .define(
            JOB_TIMEOUT_CONFIG,
            Type.LONG,
            JOB_TIMEOUT_DEFAULT,
            Importance.MEDIUM,
            JOB_TIMEOUT_DOC,
            WORKER_CONFIG_GROUP,
            ++order,
            Width.SHORT,
            "Job timeout")
        .define(
            JOB_TYPES_CONFIG,
            Type.LIST,
            JOB_TYPES_DEFAULT,
            Importance.HIGH,
            JOB_TYPES_DOC,
            WORKER_CONFIG_GROUP,
            ++order,
            Width.LONG,
            "Job types")
        .define(
            JOB_HEADER_TOPICS_CONFIG,
            Type.STRING,
            JOB_HEADER_TOPICS_DEFAULT,
            Importance.HIGH,
            JOB_HEADER_TOPICS_DOC,
            WORKER_CONFIG_GROUP,
            ++order,
            Width.SHORT,
            "Job topics header")
        .define(
            JOB_VARIABLES_CONFIG,
            Type.LIST,
            JOB_VARIABLES_DEFAULT,
            Importance.LOW,
            JOB_VARIABLES_DOC,
            WORKER_CONFIG_GROUP,
            ++order,
            Width.SHORT,
            "Job variables");
  }
}
