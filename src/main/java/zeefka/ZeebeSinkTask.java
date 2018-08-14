package zeefka;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Map;

import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.connect.sink.SinkRecord;
import org.apache.kafka.connect.sink.SinkTask;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;

import io.zeebe.gateway.ZeebeClient;

public final class ZeebeSinkTask extends SinkTask {

  private URI zeebeBrokerAddress;
  private String correlationKeyJsonPath;
  private String messageNameJsonPath;

  private ZeebeClient zeebe;

  @Override
  public void start(final Map<String, String> props) {
    try {
      zeebeBrokerAddress = new URI(props.get(Constants.CONFIG_ZEEBE_BROKER_ADDRESS));
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }

    correlationKeyJsonPath = props.get(Constants.CONFIG_CORRELATION_KEY_JSONPATH);
    messageNameJsonPath = props.get(Constants.CONFIG_MESSAGE_NAME_JSONPATH);

    zeebe = ZeebeClient.newClient();
  }

  @Override
  public void put(final Collection<SinkRecord> records) {
    for (SinkRecord record : records) {
      System.out.println( record.value().getClass() );
      System.out.println( record.value() );
//      System.out.println( ((Map)record.value()).keySet() );
      final String payload = (String) record.value();

      // Currently only built for messages with JSON payload
//      final String payload = new String(message, Charset.forName("UTF-8"));

      DocumentContext jsonPathCtx = JsonPath.parse(payload);
      String correlationKey = jsonPathCtx.read(correlationKeyJsonPath);
      String messageName = jsonPathCtx.read(messageNameJsonPath);

      // message id it used for idempotency - messages with same ID will not be
      // processed twice by Zeebe
      String messageId = record.kafkaPartition() + ":" + record.kafkaOffset();

      zeebe.topicClient().workflowClient().newPublishMessageCommand() //
          .messageName(messageName) //
          .correlationKey(correlationKey) //
          .messageId(messageId) //
          .payload(payload) //
          .send().join();
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
