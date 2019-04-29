package io.berndruecker.demo.kafka.connect.zeebe;

  import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.config.ConfigDef.Importance;
import org.apache.kafka.common.config.ConfigDef.Type;
import org.apache.kafka.connect.connector.Task;
import org.apache.kafka.connect.source.SourceConnector;

public final class ZeebeSourceConnector extends SourceConnector {
    
    private String kafkaTopics;
    private String zeebeBrokerAddress;

    @Override
    public Class<? extends Task> taskClass() {
      return ZeebeSourceTask.class;
    }

    @Override
    public ConfigDef config() {
      final ConfigDef configDef = new ConfigDef();
      configDef.define(Constants.CONFIG_ZEEBE_BROKER_ADDRESS, Type.STRING, "localhost:26500", Importance.HIGH, "Zeebe address (<host>:<port>)");
      configDef.define(Constants.CONFIG_KAFKA_TOPIC_NAMES, Type.STRING, "", Importance.HIGH, "Topic name(s) the message should be sent to");
      return configDef;
    }

    @Override
    public void start(final Map<String, String> props) {
      zeebeBrokerAddress = props.get(Constants.CONFIG_ZEEBE_BROKER_ADDRESS);
      kafkaTopics = props.get(Constants.CONFIG_KAFKA_TOPIC_NAMES);
    }

    @Override
    public void stop() {}

    @Override
    public List<Map<String, String>> taskConfigs(final int maxTasks) {
      final List<Map<String, String>> configs = new LinkedList<>();

      for (int i = 0; i < maxTasks; i++) {
        final Map<String, String> config = new HashMap<>();
        config.put(Constants.CONFIG_ZEEBE_BROKER_ADDRESS, zeebeBrokerAddress);
        config.put(Constants.CONFIG_KAFKA_TOPIC_NAMES, kafkaTopics);
        configs.add(config);
      }

      return configs;
    }

    @Override
    public String version() {
      return Constants.VERSION;
    }
  }

