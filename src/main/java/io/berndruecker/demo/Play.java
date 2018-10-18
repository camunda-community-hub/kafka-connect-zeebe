package io.berndruecker.demo;

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.clients.JobClient;
import io.zeebe.client.api.events.JobEvent;
import io.zeebe.client.api.response.ActivatedJob;
import io.zeebe.client.api.subscription.JobHandler;

public class Play {

  public static void main(String[] args) {
    ZeebeClient zeebe = ZeebeClient.newClient();
    
    zeebe.workflowClient().newDeployCommand()
      .addResourceFromClasspath("play.bpmn")
      .send().join();
    
    System.out.println("deployed");
    
    zeebe.workflowClient().newCreateInstanceCommand()
      .bpmnProcessId("play")
      .latestVersion()
      .payload("{\"orderId\": \"17\"}")
      .send().join();

    System.out.println("started");

//    zeebe.topicClient().workflowClient().newPublishMessageCommand()
//      .messageName("OrderPaid")
//      .correlationKey("17")
//      .payload("{\"x\": \"y\"}")
//      .send().join();
//    
//    System.out.println("sent message");
    
    zeebe.workflowClient().newCreateInstanceCommand()
      .bpmnProcessId("play")
      .latestVersion()
      .payload("{\"orderId\": \"17\"}")
      .send().join();

  System.out.println("started again");

    zeebe.jobClient().newWorker()
      .jobType("sysout")
      .handler(new JobHandler() {
        
        @Override
        public void handle(JobClient client, ActivatedJob job) {
          System.out.println(job);    
          client
            .newCompleteCommand(job.getKey())
            .send().join();                    
        }
      })
      .open();

    System.out.println("and waiting...");

  }

}
