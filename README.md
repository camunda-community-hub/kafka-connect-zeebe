# kafka-connect-zeebe

[Kafka Connect](https://docs.confluent.io/2.0.0/connect/)or for [Zeebe.io](http://zeebe.io/)

**This is a prototype for a POC - it is not production ready!**

See this blog post for an introduction: https://zeebe.io/blog/2018/12/writing-an-apache-kafka-connector-for-zeebe/

Features:
* Correlate messages from a Kafka topic with Zeebe workflows. This uses the [Zeebe Message Correlation](https://docs.zeebe.io/reference/message-correlation.html) features. So for example if no matching workflow instance is found, the message is buffered for its time-to-live (TTL) and then discarded. You could simply ingest all messages from a Kafka topic and check if they correlate to something in Zeebe.
* Send messages from a workflow in Zeebe to a Kafka topic.

![Overview](overview.png)

# How to use the connector

* Build via mvn package
* Put the resulting UBER jar into KAFKA_HOME/plugins
* Run Kafka Connect using the Connector pointing to the property files listed below: connect-standalone connect-standalone.properties zeebe-sink.properties zeebe-source.properties


## Sink (Kafka => Zeebe)

The sink will forward all records on a Kafka topic to Zeebe (see [quickstart-zeebe-sink.properties](blob/master/config/quickstart-zeebe-sink.properties)):

```
name=zeebe-sink
connector.class=io.zeebe.kafka.connect.ZeebeSinkConnector
tasks.max=1
topics=zeebe

# Set default converters to be JSON with no schemas; this allows standard consumers to use a simple
# JsonDeserializer to quickly inspect published jobs.
# You should modify this to your preferred converter config to use schemas properly.
key.converter=org.apache.kafka.connect.json.JsonConverter
key.converter.schemas.enable=false

value.converter=org.apache.kafka.connect.json.JsonConverter
value.converter.schemas.enable=false

# Connector specific settings
zeebe.client.broker.contactPoint=localhost:26500
zeebe.client.requestTimeout=10000

message.path.correlationKey=$.correlationKey
message.path.messageName=$.messageName

# Optional settings
# message.path.timeToLive=$.timeToLive
# message.path.variables=$.variables
...
```

In a workflow model you can wait for certain events by name (extracted from the payload by messageNameJsonPath):

![Overview](bpmn1.png)

## Source (Zeebe => Kafka)

The source can send records to Kafka if a workflow instance flows through a certain activity ([quickstart-zeebe-source.properties](blob/master/config/quickstart-zeebe-source.properties)):

```
name=zeebe-source
connector.class=io.zeebe.kafka.connect.ZeebeSourceConnector
tasks.max=1

# Set default converters to be JSON with no schemas; this allows standard consumers to use a simple
# JsonDeserializer to quickly inspect published jobs.
# You should modify this to your preferred converter config to use schemas properly.
key.converter=org.apache.kafka.connect.json.JsonConverter
key.converter.schemas.enable=false

value.converter=org.apache.kafka.connect.json.JsonConverter
value.converter.schemas.enable=false

# Connector specific settings
zeebe.client.broker.contactPoint=localhost:26500
zeebe.client.worker.maxJobsActive=100
zeebe.client.job.worker=kafka-connector
zeebe.client.job.timeout=5000
zeebe.client.requestTimeout=10000

job.types=kafka
job.header.topics=kafka-topic
```

In a workflow you can then add a Service Task with the task type "sendMessage" which will create a record on the Kafka topic configured:

![Overview](bpmn2.png)

## Filtering Variables

You can filter the variables being sent to Kafka by adding a custom header to the "sendMessage" task with the configuration option "job.variables".

Set the value of this key to a comma-separated list of variables to pass to Kafka.

If this custom header is not present, then all variables in the scope will be sent to Kafka by default.

![Filter Variables](variables-custom-header.png)
