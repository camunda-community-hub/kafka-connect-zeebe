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
package io.zeebe.kafka.connect;

import io.zeebe.kafka.connect.source.ZeebeSourceConnectorConfig;
import io.zeebe.kafka.connect.source.ZeebeSourceTask;
import io.zeebe.kafka.connect.util.VersionInfo;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.config.ConfigException;
import org.apache.kafka.connect.connector.Task;
import org.apache.kafka.connect.errors.ConnectException;
import org.apache.kafka.connect.source.SourceConnector;
import org.apache.kafka.connect.util.ConnectorUtils;

public class ZeebeSourceConnector extends SourceConnector {
  private List<String> jobTypes;
  private ZeebeSourceConnectorConfig config;

  /** {@inheritDoc} */
  @Override
  public void start(final Map<String, String> props) {
    try {
      config = new ZeebeSourceConnectorConfig(props);
    } catch (final ConfigException e) {
      throw new ConnectException(
          "Failed to start ZeebeSourceConnector due to configuration error", e);
    }

    jobTypes = config.getList(ZeebeSourceConnectorConfig.JOB_TYPES_CONFIG);
    if (jobTypes.isEmpty()) {
      throw new ConnectException(
          "Expected at least one job type to poll for, but nothing configured");
    }
  }

  /** {@inheritDoc} */
  @Override
  public Class<? extends Task> taskClass() {
    return ZeebeSourceTask.class;
  }

  /** {@inheritDoc} */
  @Override
  public List<Map<String, String>> taskConfigs(final int maxTasks) {
    return ConnectorUtils.groupPartitions(jobTypes, maxTasks).stream()
        .map(this::transformTask)
        .collect(Collectors.toList());
  }

  /** {@inheritDoc} */
  @Override
  public void stop() {}

  /** {@inheritDoc} */
  @Override
  public ConfigDef config() {
    return ZeebeSourceConnectorConfig.DEFINITIONS;
  }

  /** {@inheritDoc} */
  @Override
  public String version() {
    return VersionInfo.getVersion();
  }

  private Map<String, String> transformTask(final List<String> jobTypes) {
    final Map<String, String> taskConfig = new HashMap<>(config.originalsStrings());
    taskConfig.put(ZeebeSourceConnectorConfig.JOB_TYPES_CONFIG, String.join(",", jobTypes));

    return taskConfig;
  }
}
