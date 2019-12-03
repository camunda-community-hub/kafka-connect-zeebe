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

import io.zeebe.kafka.connect.util.ZeebeClientConfigDef;
import java.time.Duration;

public class ZeebeSourceBackoff {

  private static final int NO_BACKOFF = -1;

  private final long minBackoff;
  private final long maxBackoff;
  private long backoff;

  private ZeebeSourceBackoff(final long minBackoff, final long maxBackoff) {
    this.minBackoff = minBackoff;
    this.maxBackoff = maxBackoff;
    reset();
  }

  ZeebeSourceBackoff(final ZeebeSourceConnectorConfig config) {
    this(100, config.getLong(ZeebeClientConfigDef.REQUEST_TIMEOUT_CONFIG));
  }

  void reset() {
    backoff = NO_BACKOFF;
  }

  Duration currentDuration() {
    return Duration.ofMillis(backoff);
  }

  private long nextDuration() {
    final long nextBackoff = Math.max(backoff, minBackoff) * 2;
    return Math.min(nextBackoff, maxBackoff);
  }

  void backoff() {
    backoff = nextDuration();
    try {
      Thread.sleep(backoff);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
