package io.berndruecker.demo;

import io.zeebe.gateway.ZeebeClient;

public class StartWorkflowInstance {

  public static void main(String[] args) {
    ZeebeClient zeebe = ZeebeClient.newClient();
    
    zeebe.workflowClient().newCreateInstanceCommand()
      .bpmnProcessId("play")
      .latestVersion()
      .payload("{\"orderId\": \"18\"}")
      .send().join();

    System.out.println("started");
    
    zeebe.close();
  }
}
