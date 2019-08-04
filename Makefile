ROOT_DIR := $(realpath $(dir $(lastword $(MAKEFILE_LIST))))
DOCKER_DIR := $(ROOT_DIR)/docker
DOCKER_FILE := $(DOCKER_DIR)/docker-compose.yml
EXAMPLES_DIR := $(ROOT_DIR)/examples
BUILD_DIR := $(ROOT_DIR)/target

.DEFAULT_TARGET: build

.PHONY: build
build:
	cd $(ROOT_DIR) && mvn install -DskipTests

.PHONY: rebuild
rebuild:
	cd $(ROOT_DIR) && mvn clean install -DskipTests

.PHONY: prepare-docker
prepare-docker:
	cp -R $(BUILD_DIR)/kafka-connect-zeebe-*-uber.jar $(DOCKER_DIR)/connectors/kafka-connect-zeebe.jar

.PHONY: docker
docker: prepare-docker
	docker-compose -f $(DOCKER_FILE) --project-directory $(DOCKER_DIR) up -d

.PHONY: docker-wait-zeebe
docker-wait-zeebe:
	while ! curl --fail -s http://localhost:9600/ready; do sleep 1; done

.PHONY: docker-wait-connect
docker-wait-connect:
	while ! curl --fail -s http://localhost:8083; do sleep 1; done

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

