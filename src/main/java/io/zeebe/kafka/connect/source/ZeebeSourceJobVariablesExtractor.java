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
package io.zeebe.kafka.connect.source;

import io.camunda.zeebe.client.api.response.ActivatedJob;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ZeebeSourceJobVariablesExtractor {
  private final String jobVariables;

  public ZeebeSourceJobVariablesExtractor(ZeebeSourceConnectorConfig config) {
    jobVariables = config.getString(ZeebeSourceConnectorConfig.JOB_HEADER_VARIABLES_CONFIG);
  }

  public List<String> extract(ActivatedJob job) {
    final List<String> variableNames;
    final String jobVariableString = job.getCustomHeaders().get(jobVariables);
    if (jobVariableString == null) {
      variableNames = null;
    } else if (jobVariableString.contains(",")) {
      variableNames =
          Arrays.stream(jobVariableString.split(",", 0))
              .map(String::trim)
              .collect(Collectors.toList());
    } else {
      variableNames = new ArrayList<String>(1);
      variableNames.add(jobVariableString);
    }
    return variableNames;
  }
}
