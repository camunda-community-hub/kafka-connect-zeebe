SELF_DIR := $(realpath $(dir $(lastword $(MAKEFILE_LIST))))
include $(SELF_DIR)/../../Makefile

.DEFAULT_TARGET: flow-retail
.PHONY: flow-retail
flow-retail: workflow source sink

.PHONY: workflow
workflow:
	docker cp $(SELF_DIR)/process.bpmn $(shell docker-compose -f $(DOCKER_FILE) ps -q zeebe):/tmp/process.bpmn && \
	docker-compose -f $(DOCKER_FILE) exec zeebe zbctl deploy /tmp/process.bpmn

.PHONY: source
create-source-connector:
	curl -X POST -H "Content-Type: application/json" --data @$(SELF_DIR)/source.json http://localhost:8083/connectors

.PHONY: sink
sink:
	curl -X POST -H "Content-Type: application/json" --data @$(SELF_DIR)/sink.json http://localhost:8083/connectors

.PHONY: instance
instance:
  @ORDER_ID=$(shell read -p "Order ID: " orderId; echo $$orderId); \
	docker-compose -f $(DOCKER_FILE) exec zeebe zbctl create instance --variables '{"orderId": $$ORDER_ID}' flow-retail

.PHONY: logger
logger:
	cd $(ROOT_DIR) && \
		mvn exec:java -Dexec.mainClass=io.zeebe.kafka.connect.LoggerWorker -Dexec.classpathScope="test" -Djob.types="payment-requested,payment-confirmed"

.PHONY:
producer:
	docker-compose -f $(DOCKER_FILE) exec kafka \
	kafka-console-producer --request-required-acks 1 --broker-list kafka:19092 --topic payment-confirm
