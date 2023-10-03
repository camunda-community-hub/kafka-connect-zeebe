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
package io.zeebe.kafka.connect.sink.message;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.JsonPathException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.json.JsonConverter;
import org.apache.kafka.connect.sink.SinkRecord;
import org.apache.kafka.connect.storage.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Eventually extract to interface so we can swap it out for Schema or something
public final class JsonRecordParser {
  private static final Logger LOGGER = LoggerFactory.getLogger(JsonRecordParser.class);
  private static final Converter JSON_CONVERTER;

  static {
    JSON_CONVERTER = new JsonConverter();
    JSON_CONVERTER.configure(Collections.singletonMap("schemas.enable", "false"), false);
  }

  private JsonPath keyPath;
  private JsonPath namePath;
  private JsonPath ttlPath;
  private JsonPath variablesPath;

  private JsonRecordParser(
      final JsonPath keyPath,
      final JsonPath namePath,
      final JsonPath ttlPath,
      final JsonPath variablesPath) {
    this.keyPath = keyPath;
    this.namePath = namePath;
    this.ttlPath = ttlPath;
    this.variablesPath = variablesPath;
  }

  public static Builder builder() {
    return new Builder();
  }

  public Message parse(final SinkRecord record) {
    final Message.Builder builder = Message.builder();

    try {
      final DocumentContext document = parseRecordValue(record);
      builder
          .withId(generateId(record))
          .withKey(document.read(keyPath).toString())
          .withName(document.read(namePath).toString());

      if (ttlPath != null) {
        applyTimeToLiveIfPossible(builder, document);
      }

      if (variablesPath != null) {
        applyVariableWithFallback(builder, document);
      }
    } catch (final Exception e) {
      LOGGER.debug("Failed to parse record as JSON: {}", record, e);
      throw e;
    }

    return builder.build();
  }

  /**
   * If given a schema, use {@link JsonConverter#fromConnectData(String, Schema, Object)} to parse
   * the value. If given a string parse the JSON document, otherwise delegate to {@link
   * JsonPath#parse(Object)}
   */
  private DocumentContext parseRecordValue(final SinkRecord record) {
    Object value = record.value();

    if (record.valueSchema() != null) {
      final byte[] bytes =
          JSON_CONVERTER.fromConnectData(record.topic(), record.valueSchema(), record.value());
      value = new String(bytes, StandardCharsets.UTF_8);
    }

    if (value instanceof String) {
      return JsonPath.parse((String) value);
    }

    return JsonPath.parse(value);
  }

  private void applyVariableWithFallback(
      final Message.Builder builder, final DocumentContext document) {
    try {
      builder.withVariables(document.read(variablesPath));
    } catch (final JsonPathException e) {
      LOGGER.trace("No variables found, fallback to the whole document as payload");
      builder.withVariables(document.json());
    }
  }

  private void applyTimeToLiveIfPossible(
      final Message.Builder builder, final DocumentContext document) {
    try {
      builder.withTimeToLive(document.read(ttlPath, Long.class));
    } catch (final JsonPathException e) {
      LOGGER.trace("No timeToLive found, ignoring");
    }
  }

  // using a combination of the topic, partition, and offset allows us to ensure a Kafka record is
  // strictly published at most once for a given workflow instance
  private String generateId(final SinkRecord record) {
    return record.topic() + ":" + record.kafkaPartition() + ":" + record.kafkaOffset();
  }

  public static final class Builder {
    private JsonPath keyPath;
    private JsonPath namePath;
    private JsonPath ttlPath;
    private JsonPath variablesPath;

    private Builder() {}

    public Builder withKeyPath(final String keyPath) {
      this.keyPath = JsonPath.compile(keyPath);
      return this;
    }

    public Builder withNamePath(final String namePath) {
      this.namePath = JsonPath.compile(namePath);
      return this;
    }

    public Builder withTtlPath(final String ttlPath) {
      this.ttlPath = JsonPath.compile(ttlPath);
      return this;
    }

    public Builder withVariablesPath(final String variablesPath) {
      this.variablesPath = JsonPath.compile(variablesPath);
      return this;
    }

    public JsonRecordParser build() {
      return new JsonRecordParser(keyPath, namePath, ttlPath, variablesPath);
    }
  }
}
