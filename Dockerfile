FROM maven as build
WORKDIR /usr/src/build
COPY src /usr/src/build/src
COPY pom.xml /usr/src/build/
RUN ["mvn", "clean", "install"]

FROM confluentinc/cp-kafka-connect:5.3.0 as kafka-connect-zeebe
COPY --from=build /usr/src/build/target/*uber.jar /etc/kafka-connect/jars/