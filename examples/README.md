# Examples

You will find some examples that help show how to use the connectors in various
ways.

To run the example you need the following tools on your system:

1. [docker-compose](https://docs.docker.com/compose/)
1. [maven](https://maven.apache.org/) (to build the project)

The [Zeebe Modeler](https://github.com/zeebe-io/zeebe-modeler/releases) is a nice addition to see 
process as well.

## Setup

All examples will require you to build the project and setup the required services.

### Build

To build the project, simply run the following from the root project directory:

```shell
mvn clean install -DskipTests
```

The resulting artifact is an uber JAR, e.g. `target/kafka-connect-zeebe-*-uber.jar`, where the
asterisk is replaced by the current project version. For example, for version `1.0.0-SNAPSHOT`, then
the artifact is located at: `target/kafka-connect-zeebe-1.0.0-SNAPSHOT-uber.jar`.

Copy this JAR to `docker/connectors/`, e.g. `docker/connectors/kafka-connect-zeebe-1.0.0-SNAPSHOT-uber.jar`.

### Start services

In order to run the examples, we'll be running the following services:

- [Zeebe](https://zeebe.io), on port `26500` for the client, and port `9600` for monitoring.
    - To check if your Zeebe instance is ready, you can check [http://localhost:9600/ready](http://localhost:9600/ready), 
      which will return a `204` response if ready.
- [Kafka](https://kafka.apache.org/), on port `9092`.
    - [Zookeeper](https://zookeeper.apache.org/), on port `2081`.
- [Kafka Schema Registry](https://docs.confluent.io/current/schema-registry/index.html), on port `8081`.
- [Kafka Connect](https://docs.confluent.io/current/connect/index.html), on port `8083`.

Additionally, we will start two services to monitor Zeebe and Kafka:

- [Operate](https://github.com/zeebe-io/zeebe/releases/tag/0.20.0), a [monitoring tool for Zeebe](https://zeebe.io/blog/2019/04/announcing-operate-visibility-and-problem-solving/), on [port 8080](http://localhost:8080).
    - Operate has an external dependency on [Elasticsearch](https://www.elastic.co/), which we'll also run on port `9200`.
- [Confluent Control Center](https://www.confluent.io/confluent-control-center/), on port `9021`. This will be our tool to monitor the Kafka cluster, create connectors, visualize Kafka topics, etc.
