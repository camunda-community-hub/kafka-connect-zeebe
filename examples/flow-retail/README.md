# Flow Retail

This example showcases how Zeebe could integrate into a micro service architecture
where Kafka is used as an event bus. Once a workflow instance is created, a service
task is consumed by the source connector, which publishes a record on a particular topic.

An external service can then consume that record, process it, and publish the results back
on a different topic which will be processed by the sink connector. The sink connector then
completes the workflow instance by publishing a message back to Zeebe based on the results
of the work done by the external system (or systems). 

![Process](process.png)

> The `Logger` tasks are there mostly for demo purposes to better visualize the flow.

Once a workflow instance is started, the process will wait at the service task. Once that
task is consumed by the source connector, the workflow will continue to the first `Logger` task,
which simply prints out the job to standard out, allowing us to track progress, before moving on
to the intermediate catch event, where it will wait for a message incoming from the sink connector.  

> You can visualize the records published by the source connector using the [kafka-console-consumer](https://kafka.apache.org/quickstart#quickstart_consume)
  or simply Control Center. The records are published on the topic `payment-request`

To complete the message we will then use the [kafka-console-producer](https://kafka.apache.org/quickstart#quickstart_send),
producing records of the following format:

```json
{
  "eventType": "OrderPaid", 
  "orderId": 1,
  "amount": 4000
}
```

## Running the example

The simplest way to run through it is to use the provided `Makefile`. If that's not an
option on your system, then you can run all the steps manually.

### Requirements

To run the example you need the following tools on your system:

1. [docker-compose](https://docs.docker.com/compose/)
1. [maven](https://maven.apache.org/) (to build the project)

### Makefile

> To use the `Makefile` you will also need [curl](https://curl.haxx.se/).

Before starting, you need to make sure that the connector was built and the docker services are
up and running. You can use the `Makefile` in the root folder of the project, and run the following:

#### Start services

```shell
make build docker docker-wait-zeebe docker-wait-connect
```

This will ensure that everything is up and running before we start. You can then monitor your system
using Confluent Control Center (on port `9021`, e.g. `http://localhost:9021`), and Operate (on port 
`8080`, e.g. `http://localhost:8080`).

#### Deploy workflow and connectors

Once everything is up and running, you can start the example by running:

```shell
make deploy-workflow create-source-connector create-sink-connector
```

#### Create an instance

You can now create a workflow instance of the `flow-retail` process; this instance will start, and create a job which is consumed by the source connector. 

To create the instance, run:

```shell
make id=1 create-workflow
```

The value of `id` will be the `orderId` variable for the instance.

#### Start the logger worker

```shell
make start-logger
```

#### Publishing a message

To publish a message back through the connector, we have to produce a record on the `payment-confirm` topic. The record should have the format as described above.

To publish a message, run:

```shell
make start-producer
```

This will start the [kafka-console-producer](https://kafka.apache.org/quickstart#quickstart_send).
Simply write the expected JSON record, e.g.:

```json
{"eventType": "OrderPaid", "orderId": 1, "amount": 4000}
``` 

### Manually

If `make` is not available on your system (if on Windows, WSL could help there), then you can run
steps manually:

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
docker cp examples/flow-retail/process.bpmn $(shell docker-compose -f docker/docker-compose.yml ps -q zeebe):/tmp/process.bpmn
docker-compose -f docker/docker-compose.yml exec zeebe zbctl deploy /tmp/process.bpmn
```

Now create the source connector:
```shell
curl -X POST -H "Content-Type: application/json" --data @examples/flow-retail/source.json http://localhost:8083
```

Next, create the sink connector:

```
curl -X POST -H "Content-Type: application/json" --data @examples/flow-retail/source.json http://localhost:8083
```

After this we can now create a workflow instance:

```shell
docker-compose -f docker/docker-compose.yml exec zeebe \
	zbctl create instance --variables "{\"orderId\": 1}" flow-retail
```

And start the logger worker to complete the `Logger` service tasks:

```shell
  mvn exec:java -Dexec.mainClass=io.zeebe.kafka.connect.LoggerWorker -Dexec.classpathScope="test"
```

Then we now have to start the Kafka console producer:

```shell
docker-compose -f docker/docker-compose.yml exec kafka \
	kafka-console-producer --request-required-acks 1 --broker-list kafka:19092 --topic payment-confirm
```

This will start the [kafka-console-producer](https://kafka.apache.org/quickstart#quickstart_send).
Simply write the expected JSON record, e.g.:

```json
{"eventType": "OrderPaid", "orderId": 1, "amount": 4000}
``` 

Make sure to update the `orderId` to match the expected correlation key.
