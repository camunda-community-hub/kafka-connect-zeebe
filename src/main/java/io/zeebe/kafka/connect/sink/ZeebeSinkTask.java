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

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.command.FinalCommandStep;
import io.zeebe.client.api.command.PublishMessageCommandStep1.PublishMessageCommandStep3;
import io.zeebe.kafka.connect.sink.message.JsonRecordParser;
import io.zeebe.kafka.connect.sink.message.JsonRecordParser.Builder;
import io.zeebe.kafka.connect.sink.message.Message;
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
  private JsonRecordParser parser;

  @Override
  public void start(final Map<String, String> props) {
    final ZeebeSinkConnectorConfig config = new ZeebeSinkConnectorConfig(props);
    client = buildClient(config);
    parser = buildParser(config);
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

    LOGGER.trace("Published {} messages", sinkRecords.size());
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

  private FinalCommandStep<Void> prepareRequest(final SinkRecord record) {
    final Message message = parser.parse(record);
    PublishMessageCommandStep3 request =
        client
            .newPublishMessageCommand()
            .messageName(message.getName())
            .correlationKey(message.getKey())
            .messageId(message.getId());

    if (message.hasTimeToLive()) {
      request = request.timeToLive(message.getTimeToLive());
    }

    if (message.hasVariables()) {
      request = request.variables(message.getVariables());
    }

    LOGGER.debug("Publishing message {}", message);
    return request;
  }

  private ZeebeClient buildClient(final ZeebeSinkConnectorConfig config) {
    final long requestTimeoutMs = config.getLong(ZeebeClientConfigDef.REQUEST_TIMEOUT_CONFIG);

    return ZeebeClient.newClientBuilder()
        .brokerContactPoint(config.getString(ZeebeClientConfigDef.BROKER_CONTACTPOINT_CONFIG))
        .defaultRequestTimeout(Duration.ofMillis(requestTimeoutMs))
        .build();
  }

  private JsonRecordParser buildParser(final ZeebeSinkConnectorConfig config) {
    final Builder builder =
        JsonRecordParser.builder()
            .withKeyPath(config.getString(ZeebeSinkConnectorConfig.MESSAGE_PATH_KEY_CONFIG))
            .withNamePath(config.getString(ZeebeSinkConnectorConfig.MESSAGE_PATH_NAME_CONFIG));

    final String ttlPath = config.getString(ZeebeSinkConnectorConfig.MESSAGE_PATH_TTL_CONFIG);
    if (ttlPath != null && !ttlPath.isEmpty()) {
      builder.withTtlPath(ttlPath);
    }

    final String variablesPath =
        config.getString(ZeebeSinkConnectorConfig.MESSAGE_PATH_VARIABLES_CONFIG);
    if (variablesPath != null && !variablesPath.isEmpty()) {
      builder.withVariablesPath(variablesPath);
    }

    return builder.build();
  }
}
