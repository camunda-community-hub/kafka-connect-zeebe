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

import io.zeebe.client.api.response.ActivatedJob;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.LongStream;
import java.util.stream.Stream;

class ZeebeSourceInflightRegistry {

  private final Integer maxJobsToActivate;
  private final List<String> jobTypes;
  private final Map<String, Integer> inflightJobPerTypeCounts;
  private final Queue<JobInfo> inflightJobInfoQueue;

  ZeebeSourceInflightRegistry(final ZeebeSourceConnectorConfig config) {
    maxJobsToActivate = config.getInt(ZeebeSourceConnectorConfig.MAX_JOBS_TO_ACTIVATE_CONFIG);
    jobTypes = config.getList(ZeebeSourceConnectorConfig.JOB_TYPES_CONFIG);
    inflightJobPerTypeCounts = new ConcurrentHashMap<>(jobTypes.size());
    inflightJobInfoQueue = new LinkedList<>();
  }

  boolean hasCapacity() {
    return jobTypes.stream().anyMatch(this::hasCapacityForType);
  }

  Stream<String> jobTypesWithCapacity() {
    return jobTypes.stream().filter(this::hasCapacityForType);
  }

  int capacityForType(final String jobType) {
    return maxJobsToActivate - inflightJobPerTypeCounts.getOrDefault(jobType, 0);
  }

  private boolean hasCapacityForType(final String jobType) {
    return capacityForType(jobType) > 0;
  }

  ActivatedJob registerJob(final ActivatedJob job) {
    inflightJobPerTypeCounts.merge(job.getType(), 0, (k, v) -> v + 1);
    inflightJobInfoQueue.add(new JobInfo(job));
    return job;
  }

  long unregisterJob(final long key) {
    while (!inflightJobInfoQueue.isEmpty()) {
      final JobInfo jobInfo = inflightJobInfoQueue.remove();
      inflightJobPerTypeCounts.merge(jobInfo.getJobType(), 1, (k, v) -> v - 1);
      if (jobInfo.getKey() == key) {
        break;
      }
    }
    return key;
  }

  LongStream unregisterAllJobs() {
    return inflightJobInfoQueue.stream().mapToLong(JobInfo::getKey).map(this::unregisterJob);
  }

  static class JobInfo {
    final long key;
    final String jobType;

    JobInfo(final long key, final String jobType) {
      this.key = key;
      this.jobType = jobType;
    }

    JobInfo(final ActivatedJob job) {
      this(job.getKey(), job.getType());
    }

    public long getKey() {
      return key;
    }

    String getJobType() {
      return jobType;
    }

    @Override
    public String toString() {
      return "JobInfo{" + "key=" + key + ", jobType='" + jobType + '\'' + '}';
    }
  }
}
