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

import io.zeebe.client.ZeebeClient;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * Wraps a ZeebeClient to provide safe access to it with minimal synchronization.
 *
 * <p>Using {@link ManagedClient#withClient(Consumer)} will ensure that while the function is
 * called, the client cannot be closed; if it is flagged as closed in the meantime, it will close
 * once the function returns.
 *
 * <p>{@link ManagedClient#close()} will flag the client as closing, and will close it immediately
 * iff it is not currently in use.
 */
public class ManagedClient {
  private final Lock lock;
  private final ZeebeClient client;

  private volatile boolean closed;

  public ManagedClient(final ZeebeClient client) {
    this.client = client;
    this.lock = new ReentrantLock();
  }

  public void withClient(final Consumer<ZeebeClient> callback)
      throws AlreadyClosedException, InterruptedException {
    if (closed) {
      throw new AlreadyClosedException();
    }

    lock.lockInterruptibly();
    try {
      callback.accept(client);
    } finally {
      lock.unlock();

      if (closed) {
        close();
      }
    }
  }

  public void close() {
    closed = true;

    if (lock.tryLock()) {
      try {
        client.close();
      } finally {
        lock.unlock();
      }
    }
  }

  public static class AlreadyClosedException extends Exception {}
}
