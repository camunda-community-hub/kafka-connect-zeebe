package io.berndruecker.demo.kafka.connect.zeebe;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.source.SourceRecord;
import org.apache.kafka.connect.source.SourceTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.clients.JobClient;
import io.zeebe.client.api.response.ActivatedJob;
import io.zeebe.client.api.subscription.JobHandler;
import io.zeebe.client.api.subscription.JobWorker;

public final class ZeebeSourceTask extends SourceTask {
  
  private class JobRecord {
    Long jobKey;
    String variablesJson;
  } 
  
  private static final Logger LOG = LoggerFactory.getLogger(ZeebeSourceTask.class);
  private static String NAME_variablesToSendToKafka = "variablesToSendToKafka";  

  private String[] kafkaTopics;

  private String zeebeBrokerAddress;

  private ZeebeClient zeebe;

  private JobWorker subscription;
  private Queue<JobRecord> collectedJobs = new ConcurrentLinkedQueue<>();
  private Map<SourceRecord, Long> jobKeyForRecord = new ConcurrentHashMap<>();
  
  @Override
  public void start(final Map<String, String> props) {

    zeebeBrokerAddress = props.get(Constants.CONFIG_ZEEBE_BROKER_ADDRESS);
    kafkaTopics = props.get(Constants.CONFIG_KAFKA_TOPIC_NAMES).split(",");

    LOG.info("Connecting to Zeebe broker at '" + zeebeBrokerAddress + "'");
    
    zeebe = ZeebeClient.newClientBuilder()
      .brokerContactPoint(zeebeBrokerAddress)
      .build();    

    // subscribe to Zeebe to collect new messages to be sent
    subscription = zeebe.newWorker() //
        .jobType("sendMessage") //
        .handler(new JobHandler() {

          public void handle(JobClient jobClient, ActivatedJob jobEvent) {
            JobRecord jobRecord = new JobRecord();
            jobRecord.jobKey = jobEvent.getKey();
            jobRecord.variablesJson = getVariableJsonToPassToKafka(jobEvent);            
            collectedJobs.add(jobRecord);
          }          
        }) //
        .name("KafkaConnector") //
        .timeout(Duration.ofSeconds(5)) // lock it for 5 seconds - commit (see below) must be within that time frame
        .open();
    LOG.info("Subscribed to Zeebe at '" + zeebeBrokerAddress + "' for sending records");
  }

  private String getVariableJsonToPassToKafka(ActivatedJob jobEvent) {
    if (jobEvent.getCustomHeaders().containsKey(NAME_variablesToSendToKafka)) {
      List<String> variableNamesThatShouldBeSent = Arrays.asList( //
          jobEvent.getCustomHeaders().get(NAME_variablesToSendToKafka) //
            .toString().trim().split("\\s*,\\s*"));
      
      final Map<String, Object> variableMap = jobEvent.getVariablesAsMap();              
      final Map<String, Object> variablesToSend = new HashMap<>();
      variableNamesThatShouldBeSent.forEach((variableName) -> {
        variablesToSend.put(variableName, variableMap.get(variableName));
      });
      
      try {
        return new ObjectMapper().writeValueAsString(variablesToSend);
      } catch (JsonProcessingException e) {
        throw new RuntimeException("Could not transform variables map to JSON: " + e.getMessage(), e);
      }
    } else {
      return jobEvent.getVariables();
    }
  }
  
  @Override
  public List<SourceRecord> poll() {
    final List<SourceRecord> records = new LinkedList<>();

    JobRecord collectedJob = null;
    while ((collectedJob = collectedJobs.poll()) != null) {

      for (String topic : kafkaTopics) {
        // You could also send a byte array instead
        // byte[] variables = collectedJob.variablesJson.getBytes(Charset.forName("UTF-8"));
        // Schema.BYTE_SCHEMA,
        final SourceRecord record = new SourceRecord(null, null, topic, // ignore partitions for now random.nextInt(kafkaPartitions), 
            Schema.STRING_SCHEMA,
            collectedJob.variablesJson
        );
        records.add(record);
        // remember job key to complete it during commit
        jobKeyForRecord.put(record, collectedJob.jobKey);
        LOG.info("Collected record to be sent to Kafka " + record);
      }
    }

    return records;
  }

  @Override
  public void commitRecord(SourceRecord record) throws InterruptedException {
    Long jobKey = jobKeyForRecord.remove(record);
    zeebe.newCompleteCommand(jobKey) //
      .send().join();
  }
  
  @Override
  public void stop() {
    if (subscription!=null) {
      subscription.close();
    }
    if (zeebe!=null) {      
      zeebe.close();
    }
  }

  @Override
  public String version() {
    return Constants.VERSION;
  }

}