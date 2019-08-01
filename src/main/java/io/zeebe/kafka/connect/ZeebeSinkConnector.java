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

import io.zeebe.kafka.connect.sink.ZeebeSinkConnectorConfig;
import io.zeebe.kafka.connect.sink.ZeebeSinkTask;
import io.zeebe.kafka.connect.util.VersionInfo;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.config.ConfigException;
import org.apache.kafka.connect.connector.Task;
import org.apache.kafka.connect.errors.ConnectException;
import org.apache.kafka.connect.sink.SinkConnector;

public class ZeebeSinkConnector extends SinkConnector {

  private Map<String, String> config;

  @Override
  public void start(final Map<String, String> props) {
    try {
      // validation
      new ZeebeSinkConnectorConfig(props);
    } catch (final ConfigException e) {
      throw new ConnectException(
          "Failed to start ZeebeSinkConnector due to configuration error", e);
    }

    this.config = new HashMap<>(props);
  }

  @Override
  public Class<? extends Task> taskClass() {
    return ZeebeSinkTask.class;
  }

  @Override
  public List<Map<String, String>> taskConfigs(final int maxTasks) {
    return IntStream.range(0, maxTasks).mapToObj(i -> config).collect(Collectors.toList());
  }

  @Override
  public void stop() {}

  @Override
  public ConfigDef config() {
    return ZeebeSinkConnectorConfig.DEFINITIONS;
  }

  @Override
  public String version() {
    return VersionInfo.getVersion();
  }
}
