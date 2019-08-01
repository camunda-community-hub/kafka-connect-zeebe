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

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.connect.json.JsonDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Consumes all records from a given topic (or topics if given a regex pattern); expects records to
 * be valid JSON (both key and value). Prints out every record consumed, always restarts from the
 * earliest offset possible.
 */
public final class LoggerConsumer implements Runnable {
  public static void main(String[] args) {
    new LoggerConsumer().run();
  }

  private final Logger logger;

  private LoggerConsumer() {
    this.logger = LoggerFactory.getLogger(this.getClass());
  }

  @Override
  public void run() {
    try (final Consumer<JsonNode, JsonNode> consumer = buildConsumer()) {
      final String topics = getConfigParam("consumer.topics", "zeebe*");
      consumer.subscribe(Pattern.compile(topics));

      while (true) {
        for (ConsumerRecord<JsonNode, JsonNode> record : consumer.poll(Duration.ofSeconds(5))) {
          if (record.key() != null && record.value() != null) {
            logger.info("Consumed record: {}", record);
          }
        }
      }
    }
  }

  private Consumer<JsonNode, JsonNode> buildConsumer() {
    final Map<String, Object> config = new HashMap<>();

    setConfig(config, ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
    // having a UUID as the default group ID allows us to restart from the beginning on each run
    setConfig(config, ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString());
    setConfig(config, ConsumerConfig.CLIENT_ID_CONFIG, this.getClass().getName());
    setConfig(config, ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    setConfig(config, ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
    setConfig(config, ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, "100");
    setConfig(config, ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, "10000");
    setConfig(config, ConsumerConfig.METADATA_MAX_AGE_CONFIG, "1000");

    return new KafkaConsumer<>(
        config, new ErrorHandlingDeserializer(logger), new ErrorHandlingDeserializer(logger));
  }

  private void setConfig(final Map<String, Object> config, String key, String fallback) {
    config.put(key, getConfigParam(key, fallback));
  }

  private String getConfigParam(final String key, String fallback) {
    return System.getProperty(key, fallback);
  }

  private static class ErrorHandlingDeserializer implements Deserializer<JsonNode> {
    private final Deserializer<JsonNode> internal = new JsonDeserializer();
    private final Logger logger;

    ErrorHandlingDeserializer(Logger logger) {
      this.logger = logger;
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
      internal.configure(configs, isKey);
    }

    @Override
    public JsonNode deserialize(String topic, byte[] data) {
      try {
        return internal.deserialize(topic, data);
      } catch (SerializationException e) {
        // ignore
        logger.warn("Failed to deserialize a record on topic {}", topic, e);
        return null;
      }
    }
  }
}
