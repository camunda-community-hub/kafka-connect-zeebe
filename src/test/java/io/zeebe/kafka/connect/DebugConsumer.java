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
import java.util.Arrays;
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

public class DebugConsumer {
  public static void main(String[] args) {
    new DebugConsumer().run();
  }

  private void run() {
    final Logger logger = LoggerFactory.getLogger(this.getClass());
    try (final Consumer<JsonNode, JsonNode> consumer = buildConsumer()) {
      consumer.subscribe(Pattern.compile("zeebe*"));
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
    final String servers =
        System.getProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
    config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, Arrays.asList(servers.split(",")));

    // having a UUID as the default group ID allows us to restart from the beginning on each run
    config.put(
        ConsumerConfig.GROUP_ID_CONFIG,
        System.getProperty(ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString()));
    config.put(ConsumerConfig.CLIENT_ID_CONFIG, this.getClass().getName());
    config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

    config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);
    config.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 100);
    config.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 10000);
    config.put(ConsumerConfig.METADATA_MAX_AGE_CONFIG, 1000);

    return new KafkaConsumer<>(
        config, new ErrorHandlingDeserializer(), new ErrorHandlingDeserializer());
  }

  private static class ErrorHandlingDeserializer implements Deserializer<JsonNode> {
    private final Deserializer<JsonNode> internal = new JsonDeserializer();

    @Override
    public JsonNode deserialize(String topic, byte[] data) {
      try {
        return internal.deserialize(topic, data);
      } catch (SerializationException e) {
        // ignore
        return null;
      }
    }
  }
}
