package io.berndruecker.demo.kafka.connect.zeebe;

  import org.apache.kafka.connect.source.SourceConnector;
import org.apache.kafka.connect.connector.Task;
  import org.apache.kafka.common.config.ConfigDef;
  import org.apache.kafka.common.config.ConfigDef.Type;
  import org.apache.kafka.common.config.ConfigDef.Range;
  import org.apache.kafka.common.config.ConfigDef.Importance;

  import java.util.List;
  import java.util.LinkedList;
  import java.util.Map;
  import java.util.HashMap;
  import java.net.URI;
  import java.net.URISyntaxException;

  /**
   * Manages NameTask's
   */
  public final class ZeebeSourceConnector extends SourceConnector {
    
    private String[] kafkaTopics;
    private int kafkaPartitions;
    private String zeebeBrokerAddress;
    private String nameListKey;

    @Override
    public Class<? extends Task> taskClass() {
      return ZeebeSourceTask.class;
    }

    @Override
    public ConfigDef config() {
      final ConfigDef configDef = new ConfigDef();
      configDef.define(Constants.CONFIG_ZEEBE_BROKER_ADDRESS, Type.STRING, "localhost:26500", Importance.HIGH, "Zeebe broker address (<host>:<port>)");
      return configDef;
    }

    @Override
    public void start(final Map<String, String> props) {
      zeebeBrokerAddress = props.get(Constants.CONFIG_ZEEBE_BROKER_ADDRESS);
    }

    @Override
    public void stop() {}

    @Override
    public List<Map<String, String>> taskConfigs(final int maxTasks) {
      final List<Map<String, String>> configs = new LinkedList<>();

      for (int i = 0; i < maxTasks; i++) {
        final Map<String, String> config = new HashMap<>();
        config.put(Constants.CONFIG_ZEEBE_BROKER_ADDRESS, zeebeBrokerAddress);
        configs.add(config);
      }

      return configs;
    }

    @Override
    public String version() {
      return Constants.VERSION;
    }
  }

