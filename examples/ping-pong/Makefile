PING_PONG_DIR := $(dir $(lastword $(MAKEFILE_LIST)))
include $(PING_PONG_DIR)/../../Makefile

.PHONY: prepare-ping-pong
prepare-ping-pong: docker docker-wait-zeebe docker-wait-connect
	zbctl deploy --insecure $(PING_PONG_DIR)ping-pong.bpmn && \
	curl -X POST -H "Content-Type: application/json" --data @$(PING_PONG_DIR)/source.json \
		http://localhost:8083/connectors

.PHONY: ping-pong
ping-pong: prepare-ping-pong
	for i in {1..10}; do \
		zbctl create instance --insecure --variables \
			"{\"name\": \"pong\", \"payload\": { \"foo\": $i }, \"key\": $i }" \
		ping-pong; \
	done
