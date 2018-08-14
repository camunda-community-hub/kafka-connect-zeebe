package zeefka;

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
    private URI zeebeBrokerAddress;
    private String nameListKey;

    @Override
    public Class<? extends Task> taskClass() {
      return ZeebeSourceTask.class;
    }

    @Override
    public ConfigDef config() {
      final ConfigDef configDef = new ConfigDef();
//      configDef.define(Constants.CONFIG_KAFKA_PARTITIONS, Type.INT, Range.atLeast(0), Importance.LOW, "Number of available Kafka partitions");
//      configDef.define(Constants.CONFIG_REDIS_ADDRESS, Type.STRING, "redis://localhost:6379", Importance.HIGH, "Redis address (redis://<host>:<port>)");
//      configDef.define(Constants.CONFIG_NAME_LIST_KEY, Type.STRING, "names", Importance.HIGH, "Redis key for name list");

      return configDef;
    }

    @Override
    public void start(final Map<String, String> props) {
//      kafkaTopics = props.get(Constants.CONFIG_TOPICS).split(Constants.TOPIC_DELIMITER);
//      kafkaPartitions = Integer.parseInt(props.get(Constants.CONFIG_KAFKA_PARTITIONS));
//
//      try {
//        redisAddress = new URI(props.get(Constants.CONFIG_REDIS_ADDRESS));
//      } catch (URISyntaxException e) {
//        throw new RuntimeException(e);
//      }
//
//      nameListKey = props.get(Constants.CONFIG_NAME_LIST_KEY);
    }

    @Override
    public void stop() {}

    @Override
    public List<Map<String, String>> taskConfigs(final int maxTasks) {
      final List<Map<String, String>> configs = new LinkedList<>();

      for (int i = 0; i < maxTasks; i++) {
        final Map<String, String> config = new HashMap<>();
//        config.put(Constants.CONFIG_KAFKA_PARTITIONS, String.valueOf(kafkaPartitions));
//        config.put(Constants.CONFIG_REDIS_ADDRESS, redisAddress.toString());
//        config.put(Constants.CONFIG_NAME_LIST_KEY, nameListKey);

        configs.add(config);
      }

      return configs;
    }

    @Override
    public String version() {
      return Constants.VERSION;
    }
  }

