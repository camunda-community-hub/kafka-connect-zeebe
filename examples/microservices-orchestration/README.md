# Microservices Orchestration

This example showcases how Zeebe could orchestrate a payment microservice from within an order fulfillment microservice when Kafka is used as transport.

![Process](order-microservices-orchestration.png)


> The `Logger` tasks are there only demo purposes to follow along on the console.

It leverages the connector's source to push a message onto Kafka whenever a payment is required, expecting some payment service to process it and emit an event (or send a response message) later on. This response is correlated to the waiting order fulfillment process using the connectors sink.

The example needs the payment service to be simulated, means you need to publish a record to the `payment-confirm` topic. You could do that using the [kafka-console-producer](https://kafka.apache.org/quickstart#quickstart_send):

```json
{
  "eventType": "OrderPaid", 
  "orderId": 1,
  "amount": 4000
}
```

> You can visualize the records published by the source connector using the [kafka-console-consumer](https://kafka.apache.org/quickstart#quickstart_consume)
  or simply Control Center. The records are published on the topic `payment-request`.

## Prerequisites

* Install and run Kafka, Kafka Connect and Zeebe as described [here](https://github.com/zeebe-io/kafka-connect-zeebe/tree/master/examples#setup). The following description assumes that you leverage a managed Zeebe Cluster in Camunda Cloud.

## Running the example

Follow the following steps


### Deploy process

You can use [`zbctl`](https://github.com/zeebe-io/zeebe/releases) or the [Camunda Modeler](https://camunda.com/download/modeler/) to deploy the process to Camunda Cloud. 


#### Deploy connectors

Hint: If `curl` is not available, you can also use [Control Center](http://localhost:9021) to create the connectors.

Make sure to configure them according to the following properties: [source connector properties](source-payment.json), [sink connector properties](sink-payment.json)

Now create the source connector:
```shell
curl -X POST -H "Content-Type: application/json" --data @payment-source.json http://localhost:8083/connectors
```

Next, create the sink connector:

```
curl -X POST -H "Content-Type: application/json" --data @payment-sink.json http://localhost:8083/connectors
```

#### Create a workflow instance

Now use the command line to to start a process instance, as you then can easily pass variables as JSON. Make sure to replace the Camunda Cloud connection information with your own:

```shell
zbctl --address 8fdfbf36-5c3a-49ff-b5c6-7057d396c88c.bru-2.zeebe.camunda.io:443 --clientId 6NlBrCXH5knkZsJod2xNaR~Z2Af45mYN --clientSecret TKJVqOUkauL-m93LjGaSlry6q.8~BsVIAiCFXsriK096qTEUbgGKw5q.SjE_YGhi create instance --variables "{\"orderId\": 1}" order
```

Replace the value of the `orderId` variable to change the correlation key.

#### Logger worker

Using Linux (or Mac) you can easily open a separate console, navigate to the root project directory, and run a worker directing all jobs to the console:

```shell
zbctl --address 8fdfbf36-5c3a-49ff-b5c6-7057d396c88c.bru-2.zeebe.camunda.io:443 --clientId 6NlBrCXH5knkZsJod2xNaR~Z2Af45mYN --clientSecret TKJVqOUkauL-m93LjGaSlry6q.8~BsVIAiCFXsriK096qTEUbgGKw5q.SjE_YGhi create worker --handler cat --maxJobsActive 1 payment-requested  & zbctl --address 8fdfbf36-5c3a-49ff-b5c6-7057d396c88c.bru-2.zeebe.camunda.io:443 --clientId 6NlBrCXH5knkZsJod2xNaR~Z2Af45mYN --clientSecret TKJVqOUkauL-m93LjGaSlry6q.8~BsVIAiCFXsriK096qTEUbgGKw5q.SjE_YGhi create worker --handler cat --maxJobsActive 1 payment-confirmed
```

If you do not want to start this worker, you can also simply wait 30 seconds for the time in BPMN to kick in and just skip the logging step.

#### Confirming order (Kafka Producer)

In order to simulate the external payment confirmation service, let's start a
[Kafka producer](https://kafka.apache.org/quickstart#quickstart_send).

```shell
docker-compose -f docker/docker-compose.yml exec kafka  kafka-console-producer --request-required-acks 1 --broker-list kafka:19092 --topic payment-confirm
```

To confirm the order, we can write a record of the following format:

```json
{"eventType": "OrderPaid", "orderId": 1, "amount": 4000}
``` 

Make sure to update the `orderId` to match the expected correlation key.
