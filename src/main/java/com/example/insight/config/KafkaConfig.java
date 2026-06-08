package com.example.insight.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@Profile("!mock")
public class KafkaConfig {

    public static final String ORDERS_TOPIC = "orders-topic";
    public static final String ORDERS_PROCESSED_TOPIC = "orders-processed-topic";

    @Bean
    public NewTopic ordersTopic() {
        return TopicBuilder.name(ORDERS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic ordersProcessedTopic() {
        return TopicBuilder.name(ORDERS_PROCESSED_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
