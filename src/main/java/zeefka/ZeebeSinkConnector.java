package zeefka;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.config.ConfigDef.Importance;
import org.apache.kafka.common.config.ConfigDef.Type;
import org.apache.kafka.connect.connector.Task;
import org.apache.kafka.connect.sink.SinkConnector;

public final class ZeebeSinkConnector extends SinkConnector {
  
  private URI zeebeBrokerAddress;
  
  private String topic;
  private String correlationKeyJsonPath;
  private String messageNameJsonPath;

  @Override
  public Class<? extends Task> taskClass() {
    return ZeebeSinkTask.class;
  }

  @Override
  public ConfigDef config() {
    final ConfigDef configDef = new ConfigDef();
    configDef.define(Constants.CONFIG_ZEEBE_BROKER_ADDRESS, Type.STRING, "localhost:26500", Importance.HIGH, "Zeebe address (<host>:<port>)");
    configDef.define(Constants.CONFIG_CORRELATION_KEY_JSONPATH, Type.STRING, "$.correlationKey", Importance.HIGH, "JsonPath to gather correlation key to be used to correlate to a workflow instance from the message payload (e.g. $.correlationKey)");
    configDef.define(Constants.CONFIG_MESSAGE_NAME_JSONPATH, Type.STRING, "$.messageName", Importance.HIGH, "JsonPath to gather message name to be send to Zeebe (e.g. $.messageName)");
    return configDef;
  }

  @Override
  public List<Map<String, String>> taskConfigs(final int maxTasks) {
    final List<Map<String, String>> configs = new LinkedList<>();

    for (int i = 0; i < maxTasks; i++) {
      final Map<String, String> config = new HashMap<>();
      config.put(Constants.CONFIG_ZEEBE_BROKER_ADDRESS, zeebeBrokerAddress.toString());
      config.put(Constants.CONFIG_CORRELATION_KEY_JSONPATH, correlationKeyJsonPath);
      config.put(Constants.CONFIG_MESSAGE_NAME_JSONPATH, messageNameJsonPath);

      configs.add(config);
    }

    return configs;
  }

  @Override
  public void start(final Map<String, String> props) {
    try {
      zeebeBrokerAddress = new URI(props.get(Constants.CONFIG_ZEEBE_BROKER_ADDRESS));
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }

    correlationKeyJsonPath = props.get(Constants.CONFIG_CORRELATION_KEY_JSONPATH);
    messageNameJsonPath = props.get(Constants.CONFIG_MESSAGE_NAME_JSONPATH);
  }

  @Override
  public void stop() {
  }

  @Override
  public String version() {
    return Constants.VERSION;
  }
}
