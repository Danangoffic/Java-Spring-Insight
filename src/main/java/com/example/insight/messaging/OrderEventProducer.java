package com.example.insight.messaging;

import com.example.insight.config.KafkaConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishOrderPlaced(OrderPlacedEvent event) {
        log.info("Publishing OrderPlacedEvent to Kafka for Order ID: {}", event.getOrderId());
        kafkaTemplate.send(KafkaConfig.ORDERS_TOPIC, event.getOrderId().toString(), event);
    }
    
    public void publishOrderProcessed(Long orderId) {
        log.info("Publishing OrderProcessedEvent to Kafka for Order ID: {}", orderId);
        kafkaTemplate.send(KafkaConfig.ORDERS_PROCESSED_TOPIC, orderId.toString(), "Order " + orderId + " fully processed");
    }
}
