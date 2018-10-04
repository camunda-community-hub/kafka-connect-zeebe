package io.berndruecker.demo.kafka.connect.zeebe;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.connect.sink.SinkRecord;
import org.apache.kafka.connect.sink.SinkTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;

import io.zeebe.gateway.ZeebeClient;
import io.zeebe.gateway.api.events.MessageEvent;
import io.zeebe.gateway.api.events.WorkflowInstanceEvent;
import io.zeebe.gateway.cmd.ClientCommandRejectedException;

public final class ZeebeSinkTask extends SinkTask {

  private static final Logger LOG = LoggerFactory.getLogger(ZeebeSinkTask.class);
  
  private String zeebeBrokerAddress;
  private String correlationKeyJsonPath;
  private String messageNameJsonPath;
  private Map<String, String> startEventMapping = new HashMap<>();

  private ZeebeClient zeebe;

  @Override
  public void start(final Map<String, String> props) {
    zeebeBrokerAddress = props.get(Constants.CONFIG_ZEEBE_BROKER_ADDRESS);

    correlationKeyJsonPath = props.get(Constants.CONFIG_CORRELATION_KEY_JSONPATH);
    messageNameJsonPath = props.get(Constants.CONFIG_MESSAGE_NAME_JSONPATH);
    
    // Read from format "messageName1:process1,messageName2:process2"
    StringTokenizer startEventMappingTokenizer = new StringTokenizer(props.get(Constants.CONFIG_START_EVENT_MAPPING), ",");
    while (startEventMappingTokenizer.hasMoreTokens()) {
      String[] startEventMappingEntries = startEventMappingTokenizer.nextToken().split(":");
      startEventMapping.put(startEventMappingEntries[0].trim(), startEventMappingEntries[1].trim());
    }

    zeebe = ZeebeClient.newClientBuilder() //
          .brokerContactPoint(zeebeBrokerAddress) //
          .build();
  }

  @Override
  public void put(final Collection<SinkRecord> records) {
    for (SinkRecord record : records) {
      // Currently only built for messages with JSON payload without schema
        try {
        final String payload = (String) record.value();
       
        DocumentContext jsonPathCtx = JsonPath.parse(payload);
        String correlationKey = jsonPathCtx.read(correlationKeyJsonPath);
        String messageName = jsonPathCtx.read(messageNameJsonPath);
  
        // message id it used for idempotency - messages with same ID will not be
        // processed twice by Zeebe
        String messageId = record.kafkaPartition() + ":" + record.kafkaOffset();
         
        // workaround for missing message start event (https://github.com/zeebe-io/zeebe/issues/1022)
        // Note that this workaround is NOT idempotent, but I expect the Zeebe implementation to be 
        if (startEventMapping.containsKey(messageName)) {
          String workflowDefinitionName = startEventMapping.get(messageName);
          WorkflowInstanceEvent workflowInstanceEvent = //
              zeebe.workflowClient().newCreateInstanceCommand() //
                .bpmnProcessId(workflowDefinitionName) //
                .latestVersion() //
                .payload(payload) //
                .send().join();
          LOG.warn("Started workflow instance " + workflowInstanceEvent + " based on record " + record);
        } else {
          // back to normal behavior
          MessageEvent messageEvent = zeebe.workflowClient().newPublishMessageCommand() //
            .messageName(messageName) //
            .correlationKey(correlationKey) //
            .messageId(messageId) //
            .payload(payload) //
            .send().join();
        
          LOG.warn("Send message " + messageEvent + " to Zeebe based on record " + record);
        }
      }
      catch (ClientCommandRejectedException ex) {
        // something could not be processed in Zeebe
        // Retry will not have any effect
        // so ignore it for the moment
        LOG.error("Record "+record+" could not be handed over to Zeebe", ex);
      }
    }
  }

  @Override
  public void flush(final Map<TopicPartition, OffsetAndMetadata> offsets) {
    // just send all messages directly in the push method
    // because of idempotent processing of messages in Zeebe it is no
    // problem if messages are sent twice because of not committing the
    // kafka offset due to some system crash
  }

  @Override
  public void stop() {
  }

  @Override
  public String version() {
    return Constants.VERSION;
  }
}
