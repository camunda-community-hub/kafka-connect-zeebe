package io.berndruecker.demo.kafka.connect.zeebe;

import java.net.URI;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.kafka.common.record.Record;
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

public final class ZeebeSourceTask extends SourceTask {

  private static final Logger LOG = LoggerFactory.getLogger(ZeebeSourceTask.class);

  private String[] kafkaTopics;
  private int kafkaPartitions;
  private URI redisAddress;
  private String nameListKey;

  private String zeebeBrokerAddress;
  private String correlationKeyJsonPath;
  private String messageNameJsonPath;

  private ZeebeClient zeebe;

  private JobWorker subscription;
  private Queue<ActivatedJob> collectedJobs = new ConcurrentLinkedQueue<>();
  private Map<SourceRecord, Long> jobKeyForRecord = new ConcurrentHashMap<>();
  
  @Override
  public void start(final Map<String, String> props) {

    zeebeBrokerAddress = props.get(Constants.CONFIG_ZEEBE_BROKER_ADDRESS);

    LOG.info("Connecting to Zeebe broker at '" + zeebeBrokerAddress + "'");
    
    zeebe = ZeebeClient.newClientBuilder()
      .brokerContactPoint(zeebeBrokerAddress)
      .build();    

    // subscribe to Zeebe to collect new messages to be sent
    subscription = zeebe.jobClient().newWorker() //
        .jobType("sendMessage") //
        .handler(new JobHandler() {
          public void handle(JobClient jobClient, ActivatedJob jobEvent) {
            collectedJobs.add(jobEvent);
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

    ActivatedJob collectedJob = null;
    while ((collectedJob = collectedJobs.poll()) != null) {

      for (String topic : kafkaTopics) {
        final SourceRecord record = new SourceRecord(null, null, topic, // ignore partitions for now random.nextInt(kafkaPartitions), 
            Schema.BYTES_SCHEMA, //
            // TODO: THink about if always the full payload should be transfered
            collectedJob.getPayload().getBytes(Charset.forName("UTF-8"))); 
        records.add(record);
        // remember job key to complete it during commit
        jobKeyForRecord.put(record, collectedJob.getKey());
        LOG.info("Collected record to be sent to Kafka " + record);
      }
    }

    return records;
  }

  @Override
  public void commitRecord(SourceRecord record) throws InterruptedException {
    Long jobKey = jobKeyForRecord.remove(record);
    zeebe.jobClient() // 
      .newCompleteCommand(jobKey) //
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