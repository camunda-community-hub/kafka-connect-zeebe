SELF_DIR := $(dir $(lastword $(MAKEFILE_LIST)))

.PHONY: deploy-workflow
deploy-workflow:
  zbctl deploy --insecure $(SELF_DIR)process.bpmn

.PHONY: create-source-connector
create-source-connector:
  curl -X POST -H "Content-Type: application/json" --data @$(SELF_DIR)source.json http://localhost:8083/connectors

.PHONY: create-sink-connector
create-sink-connector:
  curl -X POST -H "Content-Type: application/json" --data @$(SELF_DIR)sink.json http://localhost:8083/connectors

.PHONY: ping
ping:
	zbctl create instance --insecure  --variables "{\"name\": \"pong\", \"payload\": { \"foo\": ${id:-1} }, \"key\": ${id:-1} }" ping-pong;	\

.PHONY: pong
pong:
  echo "{\"name\": \"pong\", \"variables\": { \"foo\": ${id:-1} }, \"key\": ${id:-1}, \"ttl\": 10000 }" | kafkacat -b localhost:9092 -t pong -T -P
