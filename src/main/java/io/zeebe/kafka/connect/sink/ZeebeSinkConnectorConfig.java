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
package io.zeebe.kafka.connect.sink;

import io.zeebe.kafka.connect.util.ZeebeClientConfigDef;
import java.util.Map;
import org.apache.kafka.common.config.AbstractConfig;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.config.ConfigDef.Importance;
import org.apache.kafka.common.config.ConfigDef.Type;
import org.apache.kafka.common.config.ConfigDef.Width;

public class ZeebeSinkConnectorConfig extends AbstractConfig {
  static final String MESSAGE_PATH_KEY_CONFIG = "message.path.correlationKey";
  static final String MESSAGE_PATH_NAME_CONFIG = "message.path.messageName";
  static final String MESSAGE_PATH_TTL_CONFIG = "message.path.timeToLive";
  static final String MESSAGE_PATH_VARIABLES_CONFIG = "message.path.variables";

  private static final String MESSAGE_CONFIG_GROUP = "Zeebe Messages";
  private static final String MESSAGE_PATH_KEY_DEFAULT = "correlationKey";
  private static final String MESSAGE_PATH_KEY_DOC =
      "JSON path expression to extract the correlation key";
  private static final String MESSAGE_PATH_NAME_DEFAULT = "messageName";
  private static final String MESSAGE_PATH_NAME_DOC =
      "JSON path expression to extract the message name";
  private static final String MESSAGE_PATH_TTL_DEFAULT = "timeToLive";
  private static final String MESSAGE_PATH_TTL_DOC =
      "JSON path expression  to extract the message time to live";
  private static final String MESSAGE_PATH_VARIABLES_DEFAULT = "variables";
  private static final String MESSAGE_PATH_VARIABLES_DOC =
      "JSON path expression to extract message variables";

  public static final ConfigDef DEFINITIONS = defineConfiguration();

  public ZeebeSinkConnectorConfig(final Map<String, String> properties) {
    super(DEFINITIONS, properties);
  }

  private static ConfigDef defineConfiguration() {
    final ConfigDef definitions = new ConfigDef();
    ZeebeClientConfigDef.defineClientGroup(definitions);
    defineMessageGroup(definitions);

    return definitions;
  }

  private static void defineMessageGroup(final ConfigDef definitions) {
    int order = 0;

    definitions
        .define(
            MESSAGE_PATH_NAME_CONFIG,
            Type.STRING,
            MESSAGE_PATH_NAME_DEFAULT,
            Importance.HIGH,
            MESSAGE_PATH_NAME_DOC,
            MESSAGE_CONFIG_GROUP,
            ++order,
            Width.SHORT,
            "Message name jsonpath query")
        .define(
            MESSAGE_PATH_KEY_CONFIG,
            Type.STRING,
            MESSAGE_PATH_KEY_DEFAULT,
            Importance.HIGH,
            MESSAGE_PATH_KEY_DOC,
            MESSAGE_CONFIG_GROUP,
            ++order,
            Width.SHORT,
            "Correlation key jsonpath query")
        .define(
            MESSAGE_PATH_VARIABLES_CONFIG,
            Type.STRING,
            MESSAGE_PATH_VARIABLES_DEFAULT,
            Importance.MEDIUM,
            MESSAGE_PATH_VARIABLES_DOC,
            MESSAGE_CONFIG_GROUP,
            ++order,
            Width.SHORT,
            "Message variables jsonpath query")
        .define(
            MESSAGE_PATH_TTL_CONFIG,
            Type.STRING,
            MESSAGE_PATH_TTL_DEFAULT,
            Importance.LOW,
            MESSAGE_PATH_TTL_DOC,
            MESSAGE_CONFIG_GROUP,
            ++order,
            Width.SHORT,
            "Message TTL jsonpath query");
  }
}
