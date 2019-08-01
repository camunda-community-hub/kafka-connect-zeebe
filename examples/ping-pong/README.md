# Ping Pong

This example showcases how jobs are consumed by Kafka (e.g. `ping`), and how records can be published
to Zeebe in order to `pong`, using the following [process](auto-ping-pong/process.bpmn)

![Process](process.png)

The difference with the [Auto Ping Pong](blob/master/examples/auto-ping-pong) example is that here
we need to interact with Kafka in order to complete it; this also allows us to monitor the state of
both systems, through Operate and Control Center.

Once a workflow instance is started, the process will wait at the service task.

The job created by service task is consumed by the source connector, which produces a record
on the `ping` topic. The record will contain the variables of the job, including 
the correlation key for the intermediate message catch event. Using `kafkacat` in consumer
mode, or using Control Center, you can then visualize the records currently published to this topic.

Once completed, the process will then move to the intermediate catch event; in order to proceed further,
it will require a record to be published on the `pong` Kafka topic. When that record is published,
it will then complete the workflow instance.

Records published on the `pong` topic should have the following format:

```json
{
  "name": "pong", 
  "variables": { 
    "foo": 1
  },
  "key": 1, 
  "ttl": 10000
}
```

This is so the JSON path configured in the sink connector can properly construct our Zeebe message. Note
that the key here obviously should be updated to correlate to the correct message.

## Running the example

The simplest way to run through it is to use the provided `Makefile`. If that's not an
option on your system, then you can run all the steps manually.

### Requirements

To run the example you need the following tools on your system:

1. [docker-compose](https://docs.docker.com/compose/)
1. [zbctl](https://github.com/zeebe-io/zeebe/releases) (part of Zeebe, you can download it from the latest release)
1. [maven](https://maven.apache.org/) (to build the project)
1. [kafkacat](https://github.com/edenhill/kafkacat) (allows you to consume and produce records)

The [Zeebe Modeler](https://github.com/zeebe-io/zeebe-modeler/releases) is a nice addition to see 
process as well.

### Makefile

Before starting, you need to make sure that the connector was built and the docker services are
up and running. You can use the `Makefile` in the root folder of the project, and run the following:

```shell
make build docker docker-wait-zeebe docker-wait-connect
```

This will ensure that everything is up and running before we start. You can then monitor your system
using Confluent Control Center (on port `9021`, e.g. `http://localhost:9021`), and Operate (on port 
`8080`, e.g. `http://localhost:8080`). If you're on Linux you can run `make monitor` which will open
those pages.

Once everything is up and running, you can start the example by running:

```shell
make deploy-workflow create-source-connector create-sink-connector
```

This will deploy the process and create the source and sink connectors.

Running `make ping` will  then create a series of instances which you can track. After this, you can
now check Operate and Control Center to monitor the state of your instances/connectors.

Once the instances awaiting messages, in order to complete the cycle we need to produce some records.
You can do so by running `make id=1 pong`, which will complete instance with correlation key 1. To 
complete all instances created by the ping command you can simply run `for {1..10}; do make id="$i" pong; done`.

### Manually

If `make` is not available on your system (if on Windows, WSL could help there), then you can run
steps manually:

1. Build the project: `mvn clean package`
1. Copy the resulting development connector folder at 
   `target/kafka-connect-zeebe-*-development/share/java/kafka-connect-zeebe` (replacing the star 
   by the version, e.g. `1.0.0-SNAPSHOT`) to `docker/connectors/kafka-connect-zeebe`
1. Go to the `docker/` folder and start all services using `docker-compose up -d`
1. To ensure all services are up and running, you query the following URLs, which should return 2xx
   responses: `http://localhost:9600/ready` (Zeebe ready check) and `http://localhost:8083/` (Kafka
   Connect API endpoint)
1. Navigate to this folder
1. Once everything is up and running, deploy the workflow using: 
   `zbctl deploy --insecure process.bpmn`
1. Create the source connector:
   `curl -X POST -H "Content-Type: application/json" --data @source.json http://localhost:8083`
1. Create the sink connector:
   `curl -X POST -H "Content-Type: application/json" --data @source.json http://localhost:8083`
1. Create a workflow instance:
   `zbctl create instance --insecure --variables "{\"name\": \"pong\", \"payload\": { \"foo\": 1 }, \"key\": 1 }" ping-pong`
1. You can now check Operate and Control Center in order to see the state of the instance.
1. Once the process is awaiting a message, you can run the following to complete it:
   `echo "{\"name\": \"pong\", \"variables\": { \"foo\": 1 }, \"key\": 1, \"ttl\": 10000 }" | kafkacat -b localhost:9092 -t pong -T -P`

If you create many instances, a good way to differentiate them is incrementing the `1`s in the above command,
which is what the `Makefile` does.
