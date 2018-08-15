package com.berndruecker.demo;

import io.zeebe.gateway.ZeebeClient;
import io.zeebe.gateway.api.clients.JobClient;
import io.zeebe.gateway.api.events.JobEvent;
import io.zeebe.gateway.api.subscription.JobHandler;

public class SysoutWorker {

  public static void main(String[] args) {
    ZeebeClient zeebe = ZeebeClient.newClient();
    
    zeebe.topicClient().jobClient().newWorker()
      .jobType("sysout")
      .handler(new JobHandler() {
        
        @Override
        public void handle(JobClient client, JobEvent evt) {
          System.out.println(evt);    
          client.newCompleteCommand(evt).send().join();          
        }
      })
      .open();

    System.out.println("Waiting...");
  }

}
