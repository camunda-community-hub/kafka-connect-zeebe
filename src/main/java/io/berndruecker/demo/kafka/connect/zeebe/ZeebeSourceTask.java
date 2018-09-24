package io.berndruecker.demo.kafka.connect.zeebe;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.source.SourceRecord;
import org.apache.kafka.connect.source.SourceTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.zeebe.gateway.ZeebeClient;
import io.zeebe.gateway.api.clients.JobClient;
import io.zeebe.gateway.api.events.JobEvent;
import io.zeebe.gateway.api.subscription.JobHandler;
import io.zeebe.gateway.api.subscription.JobWorker;

public final class ZeebeSourceTask extends SourceTask {

  private static final Logger LOG = LoggerFactory.getLogger(ZeebeSourceTask.class);

  private String[] kafkaTopics;
  private int kafkaPartitions;
  private URI redisAddress;
  private String nameListKey;

  private URI zeebeBrokerAddress;
  private String correlationKeyJsonPath;
  private String messageNameJsonPath;

  private ZeebeClient zeebe;

  private JobWorker subscription;
  private ConcurrentLinkedQueue<JobEvent> collectedJobs = new ConcurrentLinkedQueue<>();

  @Override
  public void start(final Map<String, String> props) {

//    try {
//      zeebeBrokerAddress = new URI(props.get(Constants.CONFIG_ZEEBE_BROKER_ADDRESS));
//    } catch (URISyntaxException e) {
//      throw new RuntimeException(e);
//    }
//
//    correlationKeyJsonPath = props.get(Constants.CONFIG_CORRELATION_KEY_JSONPATH);
//    messageNameJsonPath = props.get(Constants.CONFIG_MESSAGE_NAME_JSONPATH);

    zeebe = ZeebeClient.newClient();

    // subscribe to Zeebe to collect new messages to be sent
    subscription = zeebe.jobClient().newWorker() //
        .jobType("sendMessage") //
        .handler(new JobHandler() {
          public void handle(JobClient jobClient, JobEvent jobEvent) {
            collectedJobs.add(jobEvent);
          }
        }) //
        .name("KafkaConnector") //
        .timeout(Duration.ofSeconds(1)) //
        .open();
  }

  @Override
  public List<SourceRecord> poll() {
    final List<SourceRecord> records = new LinkedList<>();

    JobEvent jobEvent = null;
    while ((jobEvent = collectedJobs.poll()) != null) {

      for (String topic : kafkaTopics) {
        final SourceRecord record = new SourceRecord(null, null, topic, // ignore partitions for now random.nextInt(kafkaPartitions), 
            Schema.BYTES_SCHEMA, //
            // TODO: THink about if always the full payload should be transfered
            jobEvent.getPayload().getBytes(Charset.forName("UTF-8"))); 
        records.add(record);
      }

    }

    // if (jedis.isConnected()) {
    // try {
    // final List<String> entry = jedis.blpop(Constants.REDIS_QUERY_TIMEOUT,
    // nameListKey);
    //
    // final String name = entry.get(1);
    //
    // if (name != null) {
    // final byte[] message = name.getBytes(Charset.forName("UTF-8"));
    //
    // }
    // } catch (JedisConnectionException e) {
    // LOG.warn("Socket closed during Redis query {}", e);
    // }
    // }

    return records;
  }

  @Override
  public void stop() {
    subscription.close();
    zeebe.close();
  }

  @Override
  public String version() {
    return Constants.VERSION;
  }
}