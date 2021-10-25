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

import java.time.Duration;

public final class Message {
  private final String id;
  private final String name;
  private final String key;
  private final Object variables;
  private final Duration timeToLive;

  private Message(
      final String id,
      final String name,
      final String key,
      final Object variables,
      final Duration timeToLive) {
    this.id = id;
    this.name = name;
    this.key = key;
    this.variables = variables;
    this.timeToLive = timeToLive;
  }

  static Builder builder() {
    return new Builder();
  }

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getKey() {
    return key;
  }

  public Object getVariables() {
    return variables;
  }

  public boolean hasVariables() {
    return variables != null;
  }

  public Duration getTimeToLive() {
    return timeToLive;
  }

  public boolean hasTimeToLive() {
    return timeToLive != null;
  }

  @Override
  public String toString() {
    return "Message{"
        + "id='"
        + id
        + '\''
        + ", name='"
        + name
        + '\''
        + ", key='"
        + key
        + '\''
        + ", variables="
        + variables
        + ", timeToLive="
        + timeToLive
        + '}';
  }

  public static final class Builder {
    private String id;
    private String name;
    private String key;
    private Object variables;
    private Duration timeToLive;

    private Builder() {}

    public Builder withId(final String id) {
      this.id = id;
      return this;
    }

    public Builder withName(final String name) {
      this.name = name;
      return this;
    }

    public Builder withKey(final String key) {
      this.key = key;
      return this;
    }

    public Builder withVariables(final Object variables) {
      this.variables = variables;
      return this;
    }

    public Builder withTimeToLive(final long timeToLiveMs) {
      this.timeToLive = Duration.ofMillis(timeToLiveMs);
      return this;
    }

    public Message build() {
      return new Message(id, name, key, variables, timeToLive);
    }
  }
}
