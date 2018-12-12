package io.berndruecker.demo.kafka.connect.zeebe.test;

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.clients.JobClient;
import io.zeebe.client.api.response.ActivatedJob;
import io.zeebe.client.api.subscription.JobHandler;

public class SysoutWorker {

  public static void main(String[] args) {
    ZeebeClient zeebe = ZeebeClient.newClient();
    
    zeebe.jobClient().newWorker()
      .jobType("sysout")
      .handler(new JobHandler() {
        
        @Override
        public void handle(JobClient client, ActivatedJob job) {
          System.out.println(job);    
          client.newCompleteCommand(job.getKey()).send().join();          
        }
      })
      .open();

    System.out.println("Waiting for work...");
  }

}
