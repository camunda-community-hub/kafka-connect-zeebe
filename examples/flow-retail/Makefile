SELF_DIR := $(realpath $(dir $(lastword $(MAKEFILE_LIST))))
DOCKER_DIR := $(SELF_DIR)/../../docker
DOCKER_FILE := $(DOCKER_DIR)/docker-compose.yml

.PHONY: deploy-workflow
deploy-workflow:
	docker cp $(SELF_DIR)/process.bpmn $(shell docker-compose -f $(DOCKER_FILE) ps -q zeebe):/tmp/process.bpmn && \
	docker-compose -f $(DOCKER_FILE) exec zeebe zbctl deploy /tmp/process.bpmn

.PHONY: create-source-connector
create-source-connector:
	curl -X POST -H "Content-Type: application/json" --data @$(SELF_DIR)/source.json http://localhost:8083/connectors

.PHONY: create-sink-connector
create-sink-connector:
	curl -X POST -H "Content-Type: application/json" --data @$(SELF_DIR)/sink.json http://localhost:8083/connectors

.PHONY: create-workflow
create-workflow:
	docker-compose -f $(DOCKER_FILE) exec zeebe zbctl create instance --variables '{"orderId": ${id}}' flow-retail

.PHONY: start-logger
start-logger:
	cd $(SELF_DIR)/../.. && \
		mvn exec:java -Dexec.mainClass=io.zeebe.kafka.connect.LoggerWorker -Dexec.classpathScope="test"

.PHONY: start-producer
start-producer:
	docker-compose -f $(DOCKER_FILE) exec kafka \
	kafka-console-producer --request-required-acks 1 --broker-list kafka:19092 --topic payment-confirm
