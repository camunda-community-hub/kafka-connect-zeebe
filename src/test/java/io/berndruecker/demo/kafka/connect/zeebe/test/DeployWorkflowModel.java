package io.berndruecker.demo.kafka.connect.zeebe.test;

import io.zeebe.client.ZeebeClient;

public class DeployWorkflowModel {

  public static void main(String[] args) {
    ZeebeClient zeebe = ZeebeClient.newClient();
    
    zeebe.workflowClient().newDeployCommand()
      .addResourceFromClasspath("test-kafka-connect.bpmn")
      .send().join();
    
    System.out.println("deployed");
    
    zeebe.close();
  }

}
