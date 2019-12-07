FROM maven:3.6.3-jdk-14 as build
WORKDIR /usr/src/build
COPY src pom.xml /usr/src/build/
CMD ["mvn", "clean", "install"]

FROM confluentinc/cp-kafka-connect:5.3.0 as kafka-connect-zeebe
COPY --from=build /usr/src/build/ /etc/kafka-connect/jars/