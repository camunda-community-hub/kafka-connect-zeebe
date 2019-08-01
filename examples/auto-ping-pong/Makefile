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

.PHONY: ping-pong
ping:
	for i in {1..10}; do \
		zbctl create instance --insecure \
			--variables "{\"name\": \"pong\", \"payload\": { \"foo\": $$i }, \"key\": $$i }" auto-ping-pong;	\
	done
