# Ping Pong

This is a very simple example to showcase the interaction between Zeebe and Kafka using Kafka Connect
and the Zeebe source and sink connectors, using the following process:

![Process](process.png)

When an instance of this process is created, the service task `To Kafka` will be activated by the
source connector `ping`, which will publish a record on the topic `pong`. That record will have
the job key as its key, and as value the job itself serialized to JSON.

The `pong` sink connector will consume records from the `pong` topic, and publish a message to
its Zeebe broker. For example, given the following record published to `pong` on partition 1, 
at offset 1, and with the following value:

```json
{
  "name": "pong",
  "key": 1,
  "payload": {
    "foo": "bar"
  },
  "ttl": 10000,
  ...
}
```

It will publish the following message to Zeebe:

```json
{
  "name": "pong",
  "correlationKey": 1,
  "timeToLive": 10000,
  "messageId": "pong:1:1",
  "variables": {
    "foo": "bar"
  }
}
```

If you inspect the instance in Operate, you will see that it should be completed, and `foo` will now
be a top level variable.

## Running the example

The simplest way to run through it is to use the provided `Makefile`. If that's not an
option on your system, then you can run all the steps manually.

### Requirements

To run the example you need the following tools on your system:

1. [docker-compose](https://docs.docker.com/compose/)
1. [maven](https://maven.apache.org/) (to build the project)

### Makefile

> To use the `Makefile` you will also need [curl](https://curl.haxx.se/).

Running `make` will build the project, start the services, deploy all resources, and create
a single workflow instance. Broken down into steps:

#### Setup

```shell
make build docker docker-wait-zeebe docker-wait-connect
```

This will ensure the project is built, and all the services are up and ready.

#### Deploy workflow and connectors

```shell
make workflow source sink
```

#### Create an instance

To create the instance, run:

```shell
make instance
```
### Manually

If `make` is not available on your system then you can run steps manually:

#### Setup

Build the project by running

```shell
mvn clean package
```

Copy the resulting development connector folder at `target/kafka-connect-zeebe-*-development/share/java/kafka-connect-zeebe` 
(replacing the star by the version, e.g. `1.0.0-SNAPSHOT`) to `docker/connectors/kafka-connect-zeebe`

Now start all docker services:

```shell
docker-compose -f docker/docker-compose.yml up -d
```


To ensure all services are up and running, you query the following URLs, which should return 2xx
responses: `http://localhost:9600/ready` (Zeebe ready check) and `http://localhost:8083/` (Kafka
Connect API endpoint)

Once everything is up and running, deploy the workflow. 
First, we need to copy the process file into the Zeebe container.

```shell
docker cp examples/ping-pong/process.bpmn $(shell docker-compose -f docker/docker-compose.yml ps -q zeebe):/tmp/process.bpmn
docker-compose -f docker/docker-compose.yml exec zeebe zbctl deploy /tmp/process.bpmn
```

#### Deploy workflow and connectors

If `curl` is not available, you can also use [Control Center](http://localhost:9021) to create the connectors.
Make sure to configure them according to the following properties: [source connector properties](source.json), [sink connector properties](sink.json)

Now create the source connector:

```shell
curl -X POST -H "Content-Type: application/json" --data @examples/ping-pong/source.json http://localhost:8083
```

Next, create the sink connector:

```
curl -X POST -H "Content-Type: application/json" --data @examples/ping-pong/source.json http://localhost:8083
```

#### Create a workflow instance

We can now create a workflow instance:

```shell
docker-compose -f docker/docker-compose.yml exec zeebe \
	zbctl create instance --variables "{\"name\": \"pong\", \"payload\": { \"foo\": "bar"}, \"key\": 1}" ping-pong
```

Replace the value of the key variable to change the correlation key.
