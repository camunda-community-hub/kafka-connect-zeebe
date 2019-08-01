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
package io.zeebe.kafka.connect.util;

import java.io.InputStream;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class VersionInfo {
  private static final String VERSION;
  private static final String VERSION_FILE = "/kafka-connect-zeebe-version.properties";
  private static final String UNKNOWN_VERSION = "unknown";
  private static final Logger LOGGER = LoggerFactory.getLogger(VersionInfo.class);

  static {
    final Properties props = new Properties();
    try (final InputStream resourceStream = VersionInfo.class.getResourceAsStream(VERSION_FILE)) {
      props.load(resourceStream);
    } catch (final Exception e) {
      LOGGER.warn("Error while loading {}", VERSION_FILE, e);
    }

    VERSION = props.getProperty("version", UNKNOWN_VERSION).trim();
  }

  private VersionInfo() {}

  public static String getVersion() {
    return VERSION;
  }
}
