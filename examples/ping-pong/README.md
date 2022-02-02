# Ping Pong

This is a very simple example to showcase the interaction between Zeebe and Kafka using Kafka Connect and the Zeebe source and sink connectors, using the following process:

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

## Prerequisites

* Install and run Kafka, Kafka Connect and Zeebe as described [here](https://github.com/zeebe-io/kafka-connect-zeebe/tree/master/examples#setup)

## Running the example

### Create topic

Create topic `pong` in Kafka

### Deploy process

You can use [`zbctl`](https://github.com/zeebe-io/zeebe/releases) or the [Camunda Modeler](https://camunda.com/download/modeler/) to deploy the process to Camunda Cloud.

Now use the command line to to start a process instance, as you then can easily pass variables as JSON. Make sure to replace the Camunda Cloud connection information with your own:

```shell
zbctl --address $ZEEBE_ADDRESS --clientId $ZEEBE_CLIENT_ID --clientSecret $ZEEBE_CLIENT_SECRET create instance --variables "{\"name\": \"pong\", \"payload\": { \"foo\": \"bar\"}, \"key\": 1}" ping-pong
```


#### Deploy connectors

Hint: If `curl` is not available, you can also use [Control Center](http://localhost:9021) to create the connectors.

Make sure to configure the connectors according to the following properties: [source connector properties](source.json), [sink connector properties](sink.json). Especially the Camunda Cloud cluster id and client credentials need to be set (the "\_" is used to comment these lines):

```json
    "zeebe.client.cloud.clusterId": "5be4da01-1f35-4deb-8681-592c7001d1bd",
    "zeebe.client.cloud.region": "bru-2",
    "zeebe.client.cloud.clientId": "8Yni-2iVjOzUMsai_xQrnoY-y2EGlN_H",
    "zeebe.client.cloud.clientSecret": "RH65GZm1N4SygpLEHiqPcPkd80fz_sF2LNZfrAsC6ttIoBy288bkAexscf1PG_PV",
```

Now create the source connector:

```shell
curl -X POST -H "Content-Type: application/json" --data @examples/ping-pong/source.json http://localhost:8083/connectors
```

Next, create the sink connector:

```
curl -X POST -H "Content-Type: application/json" --data @examples/ping-pong/sink.json http://localhost:8083/connectors
```

#### Create a workflow instance

We can now create a workflow instance:

```shell
zbctl create instance --variables "{\"name\": \"pong\", \"payload\": { \"foo\": \"bar\"}, \"key\": 1}" ping-pong
```

Replace the value of the variable `key` if you run multiple instances, to change the correlation key for each.
