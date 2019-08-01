SELF_DIR := $(dir $(lastword $(MAKEFILE_LIST)))

.PHONY: build
build:
	cd $(SELF_DIR) && mvn install -DskipTests

.PHONY: rebuild
rebuild:
	cd $(SELF_DIR) && mvn clean install -DskipTests

.PHONY: prepare-docker
prepare-docker: build
	cp -R $(SELF_DIR)target/kafka-connect-zeebe-*-development/share/java/* $(SELF_DIR)docker/connectors/

.PHONY: docker
docker: prepare-docker
	docker-compose -f $(SELF_DIR)docker/docker-compose.yml --project-directory $(SELF_DIR)docker up -d

.PHONY: docker-wait-zeebe
docker-wait-zeebe:
	while ! curl http://localhost:9600; do sleep 0.1; done

.PHONY: docker-wait-connect
docker-wait-connect:
	while ! nc -z localhost 8083; do sleep 0.1; done

.PHONY: docker-stop
docker-stop:
	docker-compose -f $(SELF_DIR)docker/docker-compose.yml down
