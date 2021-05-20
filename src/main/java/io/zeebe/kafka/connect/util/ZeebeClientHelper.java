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

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.ZeebeClientBuilder;
import java.time.Duration;
import org.apache.kafka.common.config.AbstractConfig;

public class ZeebeClientHelper {

  public static ZeebeClient buildClient(final AbstractConfig config) {
    final long requestTimeoutMs = config.getLong(ZeebeClientConfigDef.REQUEST_TIMEOUT_CONFIG);

    final String camundaCloudClusterId =
        config.getString(ZeebeClientConfigDef.CAMUNDA_CLOUD_CLUSTER_ID_CONFIG);
    if (camundaCloudClusterId != null) {
      // Camunda Cloud
      final String camundaCloudClientId =
          config.getString(ZeebeClientConfigDef.CAMUNDA_CLOUD_CLIENT_ID_CONFIG);
      final String camundaCloudCliendSecret =
          config.getString(ZeebeClientConfigDef.CAMUNDA_CLOUD_CLIENT_SECRET_CONFIG);

      return ZeebeClient.newCloudClientBuilder()
          .withClusterId(camundaCloudClusterId)
          .withClientId(camundaCloudClientId)
          .withClientSecret(camundaCloudCliendSecret)
          .build();
    } else {
      // Zeebe directly (e.g. localhost)
      final ZeebeClientBuilder zeebeClientBuilder =
          ZeebeClient.newClientBuilder()
              .gatewayAddress(config.getString(ZeebeClientConfigDef.GATEWAY_ADDRESS_CONFIG))
              .numJobWorkerExecutionThreads(1)
              .defaultRequestTimeout(Duration.ofMillis(requestTimeoutMs));

      if (config.getBoolean(ZeebeClientConfigDef.USE_PLAINTEXT_CONFIG)) {
        zeebeClientBuilder.usePlaintext();
      }

      return zeebeClientBuilder.build();
    }
  }
}
