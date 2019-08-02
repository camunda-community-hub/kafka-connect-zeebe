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
package io.zeebe.kafka.connect;

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.response.ActivatedJob;
import io.zeebe.client.api.worker.JobClient;
import io.zeebe.client.api.worker.JobHandler;
import io.zeebe.client.api.worker.JobWorker;
import io.zeebe.kafka.connect.source.ZeebeSourceConnectorConfig;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple Zeebe worker which polls for jobs of type `logger`, prints out the job, and completes it.
 * This is simply for demo purposes.
 *
 * <p>To configure the worker use {@link io.zeebe.client.ClientProperties}
 *
 * <p>To specify the jobs it should poll for, use {@link
 * ZeebeSourceConnectorConfig.JOB_TYPES_CONFIG}
 */
public final class LoggerWorker implements Runnable, JobHandler {
  private final ZeebeClient client;
  private final List<JobWorker> workers;
  private final Logger logger;

  private LoggerWorker() {
    logger = LoggerFactory.getLogger(this.getClass());
    client = buildClient();
    workers = new ArrayList<>();
  }

  public static void main(String[] args) {
    new LoggerWorker().run();
  }

  @Override
  public void run() {
    final CountDownLatch latch = new CountDownLatch(1);
    try {
      workers.addAll(buildWorkers());
      latch.await();
    } catch (InterruptedException ignored) {
      // will exit promptly
    } finally {
      workers.forEach(JobWorker::close);
      client.close();
    }
  }

  @Override
  public void handle(JobClient client, ActivatedJob job) throws Exception {
    logger.info("Received job {}", job);
    client.newCompleteCommand(job.getKey()).send();
  }

  private ZeebeClient buildClient() {
    return ZeebeClient.newClientBuilder()
        .withProperties(System.getProperties())
        .numJobWorkerExecutionThreads(1)
        .build();
  }

  private List<JobWorker> buildWorkers() {
    final String[] jobTypes =
        System.getProperty(ZeebeSourceConnectorConfig.JOB_TYPES_CONFIG, "logger").split(",");

    return Arrays.stream(jobTypes).map(this::buildWorker).collect(Collectors.toList());
  }

  private JobWorker buildWorker(String jobType) {
    return client
        .newWorker()
        .jobType(jobType)
        .handler(this)
        .pollInterval(Duration.ofSeconds(1))
        .open();
  }
}
