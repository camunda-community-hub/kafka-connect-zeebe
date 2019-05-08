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

The sink will forward all records on a Kafka topic to Zeebe (see [sample-sink.properties](blob/master/src/test/resources/zeebe-test-sink.properties)):

```
name=ZeebeSinkConnector
connector.class=...ZeebeSinkConnector

correlationJsonPath=$.orderId
messageNameJsonPath=$.eventType

zeebeBrokerAddress=localhost:26500

topics=flowing-retail
...
```

In a workflow model you can wait for certain events by name (extracted from the payload by messageNameJsonPath):

![Overview](bpmn1.png)

## Source (Zeebe => Kafka)

The source can send records to Kafka if a workflow instance flows through a certain activity ([sample-source.properties](blob/master/src/test/resources/zeebe-test-source.properties)):

```
name=ZeebeSourceConnector

connector.class=...ZeebeSourceConnector
zeebeBrokerAddress=localhost:26500

topics=flowing-retail
```

In a workflow you can then add a Service Task with the task type "sendMessage" which will create a record on the Kafka topic configured:

![Overview](bpmn2.png)

## Filtering Variables

You can filter the variables being sent to Kafka by adding a custom header to the "sendMessage" task with the key "variablesToSendToKafka".

Set the value of this key to a comma-separated list of variables to pass to Kafka.

If this custom header is not present, then all variables in the scope will be sent to Kafka by default.

![Filter Variables](variables-custom-header.png)