[![Community Extension](https://img.shields.io/badge/Community%20Extension-An%20open%20source%20community%20maintained%20project-FF4700)](https://github.com/camunda-community-hub/community)
[![Lifecycle: Incubating](https://img.shields.io/badge/Lifecycle-Incubating-blue)](https://github.com/Camunda-Community-Hub/community/blob/main/extension-lifecycle.md#incubating-)
![Compatible with: Camunda Platform 8](https://img.shields.io/badge/Compatible%20with-Camunda%20Platform%208-0072Ce)

> :warning: **This is not the official Kafka connector for Camunda 8.** The Kafka Producer and Kafka Consumer connectors are found [here.](https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/kafka/) This project uses [Kafka Connect from Confluence.](https://docs.confluent.io/platform/current/connect/index.html)
> 
# kafka-connect-zeebe

This [Kafka Connect](https://docs.confluent.io/current/connect/index.html) connector for [Zeebe](https://zeebe.io) can do two things:

* **Send messages to a Kafka topic** when a workflow instance reached a specific activity. Please note that a `message` is more precisely a kafka `record`, which is also often named `event`. This is a **source** in the Kafka Connect speak.

* **Consume messages from a Kafka topic and correlate them to a workflow**. This is a Kafka Connect **sink**.

It can work with [Camunda Platform 8](https://camunda.com/platform/) SaaS or self-managed.

![Overview](doc/images/overview.png)

See this [blog post](https://zeebe.io/blog/2018/12/writing-an-apache-kafka-connector-for-zeebe/) for some background on the implementation.


# Examples and walk-through

**[Examples](examples)**

The following video walks you through an example connecting to [Camunda Platform 8 - SaaS](https://camunda.com/get-started/):

<a href="http://www.youtube.com/watch?feature=player_embedded&v=Sw-5uOuQPVI" target="_blank"><img src="http://img.youtube.com/vi/Sw-5uOuQPVI/0.jpg" alt="Walkthrough" width="240" height="180" border="10" /></a>


# Installation and quickstart

You will find information on **how to build the connector** and **how to run Kafka and Zeebe** to get started quickly here:

**[Installation](examples#setup)**


# Connectors

The plugin comes with two connectors, a source and a sink connector.

The source connector activates Zeebe jobs, publishes them as Kafka records, and completes them once they have been committed to Kafka.

## Sink connector

In a workflow model you can wait for certain events by name (extracted from the payload by messageNameJsonPath):

![Overview](doc/images/sink-example.png)

The sink connector consumes Kafka records and publishes messages constructed from those records to Zeebe.
This uses the [Zeebe Message Correlation](https://docs.camunda.io/docs/components/concepts/messages/) features.
So for example if no matching workflow instance is found, the message is buffered for its time-to-live (TTL) and then discarded.
You could simply ingest all messages from a Kafka topic and check if they correlate to something in Zeebe.

### Configuration

In order to communicate with the Zeebe workflow engine, the connector has to create a Zeebe client.

#### Camunda SaaS Properties

If you want to connect to Camunda SaaS, you can use these properties:

- `zeebe.client.cloud.clusterId`: Cluster ID you want to connect to. The Cluster must run on the public Camunda Cloud
- `zeebe.client.cloud.region`: If you don't connect to the default region (`bru-2`) you can specify the region here
- `zeebe.client.cloud.clientId`: Client ID for the connection. Ideally, create dedicated client credentials for this communication using the Camunda SaaS Console.
- `zeebe.client.cloud.clientSecret`: The Client Secret required
- `zeebe.client.requestTimeout`: timeout in milliseconds for requests to the Zeebe broker; defaults to `10000` (or 10 seconds)

If you want to connect to another endpoint than the public SaaS endpoint, you can further specify:
- `zeebe.client.cloud.token.audience`: The address for which the authorization server token should be valid
- `zeebe.client.cloud.authorization.server.url`: The URL of the authorization server from which the access token will be requested (by default, configured for Camunda SaaS)";

#### Zeebe Broker Properties

If you want to connect to a Zeebe broker hosted yourself (e.g. running on localhost), use these properties:

- `zeebe.client.broker.gateway-address`: the Zeebe gateway address, specified as `host:port`; defaults to `localhost:26500`
- `zeebe.client.requestTimeout`: timeout in milliseconds for requests to the Zeebe broker; defaults to `10000` (or 10 seconds)
- `zeebe.client.security.plaintext`: disable secure connections to the gateway for local development setups


#### Common Configuration

The Zeebe client and job workers can be configured by system properties understood by the [Zeebe Java Client](https://docs.camunda.io/docs/apis-clients/java-client/). Typical other properties are:

- `zeebe.client.worker.maxJobsActive`: the maximum number of jobs that the worker can activate in a single request; defaults to `100`
- `zeebe.client.job.worker`: the worker name; defaults to `kafka-connector`
- `zeebe.client.job.timeout`: how long before a job activated by the worker is made activatable again to others, in milliseconds; defaults to `5000` (or 5 seconds)
- `job.types`: a comma-separated list of job types that should be consumed by the connector; defaults to `kafka`
- `job.header.topics`: the [custom service task header](https://docs.camunda.io/docs/components/modeler/bpmn/service-tasks/#task-headers) which specifies to which topics the message should be published to; defaults to `kafka-topic`

You can find sample properties for the source connector [here](config/quickstart-zeebe-source.properties).

## Sink

The connector does support [schemas](https://docs.confluent.io/current/schema-registry/connect.html), but only supports JSON. The connector will use JSON path to extract certain properties from this JSON data:


- `message.path.correlationKey`: JSONPath query to use to extract the correlation key from the record; defaults to `$.correlationKey`
- `message.path.messageName`: JSONPath query to use to extract the message name from the record; defaults to `$.messageName`
- `message.path.timeToLive`: JSONPath query to use to extract the time to live from the record; defaults to `$.timeToLive`
- `message.path.variables`: JSONPath query to use to extract the variables from the record; defaults to `$.variables`

You can find sample properties for the sink connector [here](config/quickstart-zeebe-sink.properties).

## Source

Similar to receiving a message, the process can also create records. In your BPMN process model you can add a [ServiceTask](https://docs.zeebe.io/bpmn-workflows/service-tasks.html) with a configurable task type which will create a record on the configured Kafka topic:

![Overview](doc/images/source-example.png)

Under the hood, the connector will create one [job worker](https://docs.camunda.io/docs/product-manuals/concepts/job-workers) that publishes records to Kafka. The record value is a JSON representation of the job itself, the record key is the job key.

### Filtering Variables

You can filter the variables being sent to Kafka by adding a configuration option "job.variables" to your source properties. It must contain a comma-separated list of variables to pass to Kafka.

If this property is not present, then all variables in the scope will be sent to Kafka by default.

```properties
{
  "name": "ping",
  "config": {
    ...
    "job.variables": "a, b, andSomeVariableC",
    ...
```

## Configuring Error Handling of Kafka Connect, e.g. Logging or Dead Letter Queues

Kafka Connect allows you to configure what happens if a message cannot be processed. A great explanation can be found in [Kafka Connect Deep Dive – Error Handling and Dead Letter Queues](https://www.confluent.io/blog/kafka-connect-deep-dive-error-handling-dead-letter-queues). This of course also applies to this connector.

# Remote Debugging During Development

To ease with development, you can add this environment variable to kafka-connect:
`"JAVA_TOOL_OPTIONS": "-agentlib:jdwp=transport=dt_socket,address=*:5005,server=y,suspend=n"`

And then use [remote debugging](https://www.jetbrains.com/help/idea/tutorial-remote-debug.html)

# Confluent Hub

This project is set up to be released on Confluent Hub.

When
* Building this project via `mvn package`
* You will find the plugin package as ZIP file under `target/components/packages`, e.g. `target/components/packages/zeebe-io-kafka-connect-zeebe-1.0.0.zip`
* Which can be [installed onto the Confluent Hub](https://docs.confluent.io/current/connect/managing/install.html#connect-install-connectors)

