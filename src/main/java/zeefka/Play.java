package zeefka;

import io.zeebe.gateway.ZeebeClient;
import io.zeebe.gateway.api.clients.JobClient;
import io.zeebe.gateway.api.events.JobEvent;
import io.zeebe.gateway.api.subscription.JobHandler;

public class Play {

  public static void main(String[] args) {
    ZeebeClient zeebe = ZeebeClient.newClient();
    
    zeebe.topicClient().workflowClient().newDeployCommand()
      .addResourceFromClasspath("play.bpmn")
      .send().join();
    
    System.out.println("deployed");
    
    zeebe.topicClient().workflowClient().newCreateInstanceCommand()
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
    
    zeebe.topicClient().workflowClient().newCreateInstanceCommand()
      .bpmnProcessId("play")
      .latestVersion()
      .payload("{\"orderId\": \"17\"}")
      .send().join();

  System.out.println("started again");

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

    System.out.println("and waiting...");

  }

}
