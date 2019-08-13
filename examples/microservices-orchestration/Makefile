SELF_DIR := $(realpath $(dir $(lastword $(MAKEFILE_LIST))))
include $(SELF_DIR)/../../Makefile

.DEFAULT_TARGET: flow-retail
.PHONY: flow-retail
flow-retail: workflow source sink

.PHONY: workflow
workflow:
	docker cp $(SELF_DIR)/order.bpmn $(shell docker-compose -f $(DOCKER_FILE) ps -q zeebe):/tmp/order.bpmn && \
	docker-compose -f $(DOCKER_FILE) exec zeebe zbctl deploy /tmp/order.bpmn

.PHONY: source
source:
	curl -X POST -H "Content-Type: application/json" --data @$(SELF_DIR)/payment-source.json http://localhost:8083/connectors

.PHONY: sink
sink:
	curl -X POST -H "Content-Type: application/json" --data @$(SELF_DIR)/payment-sink.json http://localhost:8083/connectors

.PHONY: order
order:
	@ORDER_ID=$(or $(ORDER_ID), $(shell read -p "Order ID: " orderId; echo $$orderId)); \
	docker-compose -f $(DOCKER_FILE) exec zeebe zbctl create instance --variables "{\"orderId\": $$ORDER_ID}" order

.PHONY: logger
logger:
	cd $(ROOT_DIR) && \
	docker-compose -f $(DOCKER_FILE) exec zeebe /bin/bash -c \
		"zbctl create worker --handler cat --maxJobsActive 1 payment-requested & zbctl create worker --handler cat --maxJobsActive 1 payment-confirmed"

.PHONY: payment
payment:
	@ORDER_ID=$(or $(ORDER_ID), $(shell read -p "Order ID: " orderId; echo $$orderId)); \
	AMOUNT=$(or $(AMOUNT), $(shell read -p "Amount: " amount; echo $$amount)); \
	docker-compose -f $(DOCKER_FILE) exec kafka /bin/bash -c \
		"echo '{\"eventType\": \"OrderPaid\", \"orderId\": $$ORDER_ID, \"amount\": $$AMOUNT}' | kafka-console-producer --request-required-acks 1 --broker-list kafka:19092 --topic payment-confirm"

