package io.berndruecker.demo;

import io.zeebe.gateway.ZeebeClient;
import io.zeebe.gateway.api.clients.JobClient;
import io.zeebe.gateway.api.events.JobEvent;
import io.zeebe.gateway.api.subscription.JobHandler;

public class DeployWorkflowModel {

  public static void main(String[] args) {
    ZeebeClient zeebe = ZeebeClient.newClient();
    
    zeebe.workflowClient().newDeployCommand()
      .addResourceFromClasspath("play.bpmn")
      .send().join();
    
    System.out.println("deployed");
    
    zeebe.close();
  }

}
