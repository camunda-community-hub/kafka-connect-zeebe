package io.berndruecker.demo.kafka.connect.zeebe;

import java.nio.charset.Charset;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minidev.json.JSONValue;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.source.SourceRecord;
import org.apache.kafka.connect.source.SourceTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.clients.JobClient;
import io.zeebe.client.api.response.ActivatedJob;
import io.zeebe.client.api.subscription.JobHandler;
import io.zeebe.client.api.subscription.JobWorker;

final class JobRecord {
  Long jobKey;
  String variables;
}

public final class ZeebeSourceTask extends SourceTask {

  private static final Logger LOG = LoggerFactory.getLogger(ZeebeSourceTask.class);

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

            final String variablesToSendToKafka = "variablesToSendToKafka";
            Map<String, Object> headers = jobEvent.getCustomHeaders();

            if (headers.containsKey(variablesToSendToKafka)) {
              final Map<String, Object> variablesToSend = new HashMap<>();

              List<String> variablesThatShouldBeSent = Arrays.asList(
                      headers.get(variablesToSendToKafka).toString()
                              .split(","));
              Map<String, Object> variables = jobEvent.getVariablesAsMap();
              variables.forEach((k,v) -> {
                if (variablesThatShouldBeSent.indexOf(k) != -1) variablesToSend.put(k, v);
              });
              jobRecord.variables = JSONValue.toJSONString(variablesToSend);
            } else {
              jobRecord.variables = jobEvent.getVariables();
            }
            jobRecord.jobKey = jobEvent.getKey();
            collectedJobs.add(jobRecord);
          }
        }) //
        .name("KafkaConnector") //
        .timeout(Duration.ofSeconds(5)) // lock it for 5 seconds - commit (see below) must be within that time frame
        .open();
    LOG.info("Subscribed to Zeebe at '" + zeebeBrokerAddress + "' for sending records");
  }

  @Override
  public List<SourceRecord> poll() {
    final List<SourceRecord> records = new LinkedList<>();

    JobRecord collectedJob = null;
    while ((collectedJob = collectedJobs.poll()) != null) {

      for (String topic : kafkaTopics) {
        byte[] variables = collectedJob.variables.getBytes(Charset.forName("UTF-8"));
        final SourceRecord record = new SourceRecord(null, null, topic, // ignore partitions for now random.nextInt(kafkaPartitions), 
            Schema.BYTES_SCHEMA,
            variables
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