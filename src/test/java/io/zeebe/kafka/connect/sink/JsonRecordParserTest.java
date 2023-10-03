package io.zeebe.kafka.connect.sink;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;

import org.apache.kafka.connect.sink.SinkRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import io.zeebe.kafka.connect.sink.message.JsonRecordParser;

@Execution(ExecutionMode.CONCURRENT)
public class JsonRecordParserTest {

    @Test
    void shouldParseLongValuesFromTtl() {
        // given
        final JsonRecordParser parser = JsonRecordParser.builder()
                .withKeyPath("key")
                .withNamePath("name")
                .withTtlPath("ttl")
                .build();

        String json = new String("{\"key\": 1, \"name\": \"message\", \"ttl\": 1000}");
        SinkRecord record = new SinkRecord("test", 0, null, null, null, json, 0, null, null, null);

        // when
        assertDoesNotThrow(() -> parser.parse(record));

        // then
        Duration ttl = parser.parse(record).getTimeToLive();
        assertEquals(1000, ttl.toMillis());
    }
}
