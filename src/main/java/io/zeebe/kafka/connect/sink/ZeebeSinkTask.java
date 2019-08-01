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

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.command.FinalCommandStep;
import io.zeebe.client.api.command.PublishMessageCommandStep1.PublishMessageCommandStep3;
import io.zeebe.kafka.connect.util.VersionInfo;
import io.zeebe.kafka.connect.util.ZeebeClientConfigDef;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.connect.errors.ConnectException;
import org.apache.kafka.connect.sink.SinkRecord;
import org.apache.kafka.connect.sink.SinkTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZeebeSinkTask extends SinkTask {
  private static final Logger LOGGER = LoggerFactory.getLogger(ZeebeSinkTask.class);

  private ZeebeClient client;

  private JsonPath messageKeyConfig;
  private JsonPath messageNameConfig;
  private JsonPath messageTtlConfig;
  private JsonPath messageVariablesConfig;

  @Override
  public void start(final Map<String, String> props) {
    final ZeebeSinkConnectorConfig config = new ZeebeSinkConnectorConfig(props);
    client = buildClient(config);

    messageKeyConfig =
        JsonPath.compile(config.getString(ZeebeSinkConnectorConfig.MESSAGE_PATH_KEY_CONFIG));
    messageNameConfig =
        JsonPath.compile(config.getString(ZeebeSinkConnectorConfig.MESSAGE_PATH_NAME_CONFIG));

    final String ttlJsonPath = config.getString(ZeebeSinkConnectorConfig.MESSAGE_PATH_TTL_CONFIG);
    if (ttlJsonPath != null && !ttlJsonPath.isEmpty()) {
      messageTtlConfig = JsonPath.compile(ttlJsonPath);
    }

    final String variablesJsonPath =
        config.getString(ZeebeSinkConnectorConfig.MESSAGE_PATH_VARIABLES_CONFIG);
    if (variablesJsonPath != null && !variablesJsonPath.isEmpty()) {
      messageVariablesConfig = JsonPath.compile(variablesJsonPath);
    }
  }

  // The documentation specifies that we probably shouldn't block here but I'm not sure what the
  // consequences of doing so are so for now we await all futures
  @Override
  public void put(final Collection<SinkRecord> sinkRecords) {
    final CompletableFuture[] pendingRequests =
        sinkRecords
            .stream()
            .map(this::prepareRequest)
            .map(FinalCommandStep::send)
            .toArray(CompletableFuture[]::new);

    try {
      CompletableFuture.allOf(pendingRequests).join();
    } catch (final CancellationException e) {
      LOGGER.debug("Publish requests cancelled, probably due to task stopping", e);
    } catch (final Exception e) {
      throw new ConnectException(e);
    }

    LOGGER.debug("Published {} messages", sinkRecords.size());
  }

  @Override
  public void stop() {
    if (client != null) {
      client.close();
      client = null;
    }
  }

  @Override
  public String version() {
    return VersionInfo.getVersion();
  }

  private ZeebeClient buildClient(final ZeebeSinkConnectorConfig config) {
    final long requestTimeoutMs = config.getLong(ZeebeClientConfigDef.REQUEST_TIMEOUT_CONFIG);

    return ZeebeClient.newClientBuilder()
        .brokerContactPoint(config.getString(ZeebeClientConfigDef.BROKER_CONTACTPOINT_CONFIG))
        .defaultRequestTimeout(Duration.ofMillis(requestTimeoutMs))
        .build();
  }

  private FinalCommandStep<Void> prepareRequest(final SinkRecord record) {
    final String messageId = generateMessageId(record);
    final DocumentContext document = JsonPath.parse(record.value().toString());
    final String messageName = document.read(messageNameConfig).toString();
    final String correlationKey = document.read(messageKeyConfig).toString();
    Object variables = null;
    Duration timeToLive = null;

    PublishMessageCommandStep3 request =
        client
            .newPublishMessageCommand()
            .messageName(messageName)
            .correlationKey(correlationKey)
            .messageId(messageId);

    if (messageTtlConfig != null) {
      try {
        final long timeToLiveMs = document.read(messageTtlConfig);
        timeToLive = Duration.ofMillis(timeToLiveMs);
        request = request.timeToLive(timeToLive);
      } catch (final PathNotFoundException e) {
        LOGGER.trace("No timeToLive found, ignoring");
      }
    }

    if (messageVariablesConfig != null) {
      try {
        variables = document.read(messageVariablesConfig);
        LOGGER.debug("Publishing message with variables {}", variables);
        request = request.variables(variables);
      } catch (final PathNotFoundException e) {
        LOGGER.trace("No variables found, ignoring");
      }
    } else {
      request = request.variables(record.value());
    }

    LOGGER.debug(
        "Publishing message with ID {}, name {}, correlation key {}, timeToLive {}, and variables {}",
        messageId,
        messageName,
        correlationKey,
        timeToLive,
        variables);
    return request;
  }

  // using a combination of the topic, partition, and offset allows us to ensure a Kafka record is
  // strictly published at most once for a given workflow instance
  private String generateMessageId(final SinkRecord record) {
    return record.topic() + ":" + record.kafkaPartition() + ":" + record.kafkaOffset();
  }
}
