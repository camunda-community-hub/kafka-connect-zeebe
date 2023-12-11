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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.zeebe.kafka.connect.sink.message.JsonRecordParser;
import java.time.Duration;
import org.apache.kafka.connect.sink.SinkRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.CONCURRENT)
public class JsonRecordParserTest {

  @Test
  void shouldParseLongValuesFromTtl() {
    // given
    final JsonRecordParser parser =
        JsonRecordParser.builder()
            .withKeyPath("key")
            .withNamePath("name")
            .withTtlPath("ttl")
            .build();

    final String json = new String("{\"key\": 1, \"name\": \"message\", \"ttl\": 1000}");
    final SinkRecord record =
        new SinkRecord("test", 0, null, null, null, json, 0, null, null, null);

    // when
    assertDoesNotThrow(() -> parser.parse(record));

    // then
    final Duration ttl = parser.parse(record).getTimeToLive();
    assertEquals(1000, ttl.toMillis());
  }
}
