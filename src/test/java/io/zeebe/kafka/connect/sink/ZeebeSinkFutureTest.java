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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.grpc.Status;
import io.grpc.Status.Code;
import io.grpc.StatusRuntimeException;
import io.zeebe.client.api.ZeebeFuture;
import io.zeebe.client.api.command.FinalCommandStep;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.CONCURRENT)
class ZeebeSinkFutureTest {

  @Test
  void shouldCompleteByDefault() {
    // given
    final ZeebeSinkFuture future = create(() -> {});

    // when
    assertThatCode(() -> future.executeAsync().join())
        // then
        .doesNotThrowAnyException();
  }

  @Test
  void shouldCompleteExceptionalIfNotStatusException() {
    // given
    final String message = "expected test error";
    final ZeebeSinkFuture future = create(new RuntimeException(message));

    // when
    assertThatThrownBy(() -> future.executeAsync().join())
        // then
        .hasCauseInstanceOf(RuntimeException.class)
        .hasMessageContaining(message);
  }

  @Test
  void shouldCompleteIfAcceptableStatusException() {
    // given
    ZeebeSinkFuture.SUCCESS_CODES
        .stream()
        .map(this::create)
        .forEach(
            future -> {
              // when
              assertThatCode(() -> future.executeAsync().join())
                  // then
                  .doesNotThrowAnyException();
            });
  }

  @Test
  void shouldCompleteIfRetriableStatusException() {
    // given
    ZeebeSinkFuture.RETRIABLE_CODES
        .stream()
        .map(this::retriable)
        .map(this::create)
        .forEach(
            future -> {
              // when
              assertThatCode(() -> future.executeAsync().join())
                  // then
                  .doesNotThrowAnyException();
            });
  }

  @Test
  void shouldCompleteExceptionalIfNonRecoverableStatusException() {
    // given
    ZeebeSinkFuture.FAILURE_CODES
        .stream()
        .map(this::create)
        .forEach(
            future -> {
              // when
              assertThatThrownBy(() -> future.executeAsync().join())
                  // then
                  .hasCauseInstanceOf(StatusRuntimeException.class);
            });
  }

  private Runnable retriable(final Code code) {
    return new Runnable() {
      boolean failed = false;

      @Override
      public void run() {
        if (!failed) {
          failed = true;
          throw new StatusRuntimeException(Status.fromCode(code));
        }
      }
    };
  }

  private ZeebeSinkFuture create(final Code code) {
    return create(new StatusRuntimeException(Status.fromCode(code)));
  }

  private ZeebeSinkFuture create(final RuntimeException e) {
    return create(
        () -> {
          throw e;
        });
  }

  private ZeebeSinkFuture create(final Runnable r) {
    return new ZeebeSinkFuture(new FinalStepMock(r));
  }

  static class FinalStepMock implements FinalCommandStep<Void> {

    final Runnable mock;

    FinalStepMock(final Runnable r) {
      this.mock = r;
    }

    @Override
    public FinalCommandStep<Void> requestTimeout(final Duration duration) {
      return this;
    }

    @Override
    public ZeebeFuture<Void> send() {
      final ZeebeFutureMock future = new ZeebeFutureMock();
      try {
        mock.run();
        future.complete(null);
      } catch (final Exception e) {
        future.completeExceptionally(e);
      }

      return future;
    }
  }

  static class ZeebeFutureMock extends CompletableFuture<Void> implements ZeebeFuture<Void> {

    @Override
    public Void join(final long l, final TimeUnit timeUnit) {
      try {
        return get(l, timeUnit);
      } catch (final Exception e) {
        throw new RuntimeException(e);
      }
    }
  }
}
