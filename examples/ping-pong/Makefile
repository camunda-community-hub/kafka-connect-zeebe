SELF_DIR := $(realpath $(dir $(lastword $(MAKEFILE_LIST))))
include $(SELF_DIR)/../../Makefile

.PHONY: ping-pong
ping-pong: workflow source sink instance

.PHONY: workflow
workflow:
	docker cp $(SELF_DIR)/process.bpmn $(shell docker-compose -f $(DOCKER_FILE) ps -q zeebe):/tmp/process.bpmn && \
	docker-compose -f $(DOCKER_FILE) exec zeebe zbctl deploy /tmp/process.bpmn

.PHONY: source
source:
	curl -X POST -H "Content-Type: application/json" --data @$(SELF_DIR)/source.json http://localhost:8083/connectors

.PHONY: sink
sink:
	curl -X POST -H "Content-Type: application/json" --data @$(SELF_DIR)/sink.json http://localhost:8083/connectors

.PHONY: instance
instance:
	@KEY=$(shell read -p "Correlation key: " key; echo $$key); \
	docker-compose -f $(DOCKER_FILE) exec zeebe \
	zbctl create instance --variables "{\"name\": \"pong\", \"payload\": {\"foo\": \"bar\"}, \"key\": $$KEY}" ping-pong
