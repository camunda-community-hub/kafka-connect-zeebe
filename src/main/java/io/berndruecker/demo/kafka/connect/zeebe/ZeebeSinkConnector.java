package io.berndruecker.demo.kafka.connect.zeebe;

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
  
  private String zeebeBrokerAddress;
  
  private String correlationKeyVariable;
  private String messageNameVariable;
  private String startEventMapping;

  @Override
  public Class<? extends Task> taskClass() {
    return ZeebeSinkTask.class;
  }

  @Override
  public ConfigDef config() {
    final ConfigDef configDef = new ConfigDef();
    configDef.define(Constants.CONFIG_ZEEBE_BROKER_ADDRESS, Type.STRING, "localhost:26500", Importance.HIGH, "Zeebe address (<host>:<port>)");
    configDef.define(Constants.CONFIG_CORRELATION_KEY_VARIABLE, Type.STRING, "correlationKey", Importance.HIGH, "Variable name to gather correlation key to be used to correlate to a workflow instance from the message payload (e.g. correlationKey)");
    configDef.define(Constants.CONFIG_MESSAGE_NAME_VARIABLE, Type.STRING, "messageName", Importance.HIGH, "Variable name to gather message name to be send to Zeebe (e.g. messageName)");
    configDef.define(Constants.CONFIG_START_EVENT_MAPPING, Type.STRING, "", Importance.LOW, "Mapping for events that start new workflow instances as a workaround for a Zeebe feature (e.g. myMessage:myProcess,otherMessage:otherProcess)");
    return configDef;
  }

  @Override
  public List<Map<String, String>> taskConfigs(final int maxTasks) {
    final List<Map<String, String>> configs = new LinkedList<>();

    for (int i = 0; i < maxTasks; i++) {
      final Map<String, String> config = new HashMap<>();
      config.put(Constants.CONFIG_ZEEBE_BROKER_ADDRESS, zeebeBrokerAddress);
      config.put(Constants.CONFIG_CORRELATION_KEY_VARIABLE, correlationKeyVariable);
      config.put(Constants.CONFIG_MESSAGE_NAME_VARIABLE, messageNameVariable);
      config.put(Constants.CONFIG_START_EVENT_MAPPING, startEventMapping);

      configs.add(config);
    }

    return configs;
  }

  @Override
  public void start(final Map<String, String> props) {
    zeebeBrokerAddress = props.get(Constants.CONFIG_ZEEBE_BROKER_ADDRESS);
    correlationKeyVariable = props.get(Constants.CONFIG_CORRELATION_KEY_VARIABLE);
    messageNameVariable = props.get(Constants.CONFIG_MESSAGE_NAME_VARIABLE);
    startEventMapping = props.get(Constants.CONFIG_START_EVENT_MAPPING);
  }

  @Override
  public void stop() {
  }

  @Override
  public String version() {
    return Constants.VERSION;
  }
}
