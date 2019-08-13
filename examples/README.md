# Examples

You will find some examples that help show how to use the connectors in various
ways:

* [ping-pong](ping-pong/): This is a very simple example to showcase the interaction between Zeebe and Kafka using Kafka Connect and the Zeebe source and sink connectors
* [microservices-orchestration](microservices-orchestration/): This example showcases how Zeebe could orchestrate a payment microservice from within an order fulfillment microservice when Kafka is used as transport.


# Setup

All examples will require you to build the project and run the required services via docker. While docker is not the only way to run the examples, it provides the quickest get started experience and thus is the only option described here. Refer to [Kafka Connect Installation](https://docs.confluent.io/3.1.2/connect/userguide.html) or [Zeebe Installation](https://docs.zeebe.io/introduction/install.html) guides for more options.

To run the example you need the following tools on your system:

1. [docker-compose](https://docs.docker.com/compose/) to run Kafka and Zeebe
1. Java and [maven](https://maven.apache.org/) to build the connector
1. [Zeebe Modeler](https://github.com/zeebe-io/zeebe-modeler/releases) (optional, but it is a nice addition to graphically model the Zeebe workflows)


## Build the connector

To build the connector, simply run the following from the root project directory:

```shell
mvn clean install -DskipTests
```

The resulting artifact is an uber JAR, e.g. `target/kafka-connect-zeebe-*-uber.jar`, where the
asterisk is replaced by the current project version. For example, for version `1.0.0-SNAPSHOT`, then
the artifact is located at: `target/kafka-connect-zeebe-1.0.0-SNAPSHOT-uber.jar`.

Copy this JAR to `docker/connectors/`, e.g. `docker/connectors/kafka-connect-zeebe-1.0.0-SNAPSHOT-uber.jar`.

## Start Zeebe and Kafka via Docker Compose

This project contains a Docker Compose file to startup a nice set of tools for playing around:

```shell
cd docker
docker-compose up
```

will start:

- [Zeebe](https://zeebe.io), on port `26500` for the client, and port `9600` for monitoring.
    - To check if your Zeebe instance is ready, you can check [http://localhost:9600/ready](http://localhost:9600/ready), 
      which will return a `204` response if ready.
- [Kafka](https://kafka.apache.org/), on port `9092`.
    - [Zookeeper](https://zookeeper.apache.org/), on port `2081`.
- [Kafka Schema Registry](https://docs.confluent.io/current/schema-registry/index.html), on port `8081`.
- [Kafka Connect](https://docs.confluent.io/current/connect/index.html), on port `8083`.
- Monitoring tools
    - [Operate](https://github.com/zeebe-io/zeebe/releases/tag/0.20.0), a [monitoring tool for Zeebe](https://zeebe.io/blog/2019/04/announcing-operate-visibility-and-problem-solving/), on port `8080`.
        - Operate has an external dependency on [Elasticsearch](https://www.elastic.co/), which we'll also run on port `9200`.
    - [Confluent Control Center](https://www.confluent.io/confluent-control-center/), on port `9021`. This will be our tool to monitor the Kafka cluster, create connectors, visualize Kafka topics, etc.

Of course you can customize the Docker Compose file to your needs. This Docker Compose file is also just based on the examples provided by Zeebe and Confluent:

- [Zeebe Docker Compose](https://github.com/zeebe-io/zeebe-docker-compose)
- [CP Docker Images](https://github.com/zeebe-io/zeebe-docker-compose)

## Running without Docker

Of course, you can also run without Docker. For development purposes or just to try it out, you can simply grab the  uber JAR after the Maven build and place it in your [Kafka Connect plugin path](https://docs.confluent.io/current/connect/userguide.html#installing-plugins).
