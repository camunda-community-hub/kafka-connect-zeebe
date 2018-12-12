package io.berndruecker.demo.kafka.connect.zeebe.test;

import io.zeebe.client.ZeebeClient;

public class StartWorkflowInstance {

  public static void main(String[] args) {
    ZeebeClient zeebe = ZeebeClient.newClient();
    
    zeebe.workflowClient().newCreateInstanceCommand()
      .bpmnProcessId("test-kafka-connect")
      .latestVersion()
      .payload("{\"orderId\": \"18\"}")
      .send().join();

    System.out.println("started");
    
    zeebe.close();
  }
}
