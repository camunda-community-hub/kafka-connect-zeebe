package com.berndruecker.demo;

import java.util.Properties;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;

public class SendKafkaMessage {

  public static void main(String[] argv) throws Exception {
    // Configure the Producer
    Properties configProperties = new Properties();
    configProperties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
    configProperties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.ByteArraySerializer");
    configProperties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");

    Producer<String, String> producer = new KafkaProducer<>(configProperties);

    ProducerRecord<String, String> rec = new ProducerRecord<String, String>( //
        "flowing-retail", // 
        "{ \"eventType\": \"OrderPaid\", \"orderId\": \"18\"}");
    producer.send(rec);
    
    producer.close();
  }

}
