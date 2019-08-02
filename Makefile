SELF_DIR := $(realpath $(dir $(lastword $(MAKEFILE_LIST))))
DOCKER_DIR := $(SELF_DIR)/docker
DOCKER_FILE := $(DOCKER_DIR)/docker-compose.yml
EXAMPLES_DIR := $(SELF_DIR)/examples
BUILD_DIR := $(SELF_DIR)/target

.DEFAULT_TARGET: build

.PHONY: build
build:
	cd $(SELF_DIR) && mvn install -DskipTests

.PHONY: rebuild
rebuild:
	cd $(SELF_DIR) && mvn clean install -DskipTests

.PHONY: prepare-docker
prepare-docker:
	cp -R $(BUILD_DIR)/kafka-connect-zeebe-*-development/share/java/* $(DOCKER_DIR)/connectors/

.PHONY: docker
docker: prepare-docker
	docker-compose -f $(DOCKER_FILE) --project-directory $(DOCKER_DIR) up -d

.PHONY: docker-wait-zeebe
docker-wait-zeebe:
	while ! curl --fail http://localhost:9600/ready; do sleep 1; done

.PHONY: docker-wait-connect
docker-wait-connect:
	while ! curl --fail http://localhost:8083; do sleep 1; done

.PHONY: docker-stop
docker-stop:
	docker-compose -f $(DOCKER_FILE) down -v

.PHONY: clean
clean: docker-stop
	mvn clean

.PHONY: monitor
monitor:
	xdg-open "http://localhost:8080"
	xdg-open "http://localhost:9021"

