package com.example.insight.messaging;

import com.example.insight.config.KafkaConfig;
import com.example.insight.model.Order;
import com.example.insight.model.OrderStatus;
import com.example.insight.repository.OrderRepository;
import com.example.insight.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventConsumer {

    private final OrderRepository orderRepository;
    private final ProductService productService;
    private final OrderEventProducer eventProducer;

    @Transactional
    @KafkaListener(topics = KafkaConfig.ORDERS_TOPIC, groupId = "insight-group")
    public void consumeOrderPlaced(OrderPlacedEvent event) {
        log.info("Received OrderPlacedEvent from Kafka for Order ID: {}", event.getOrderId());

        try {
            // Find order in database
            Order order = orderRepository.findById(event.getOrderId())
                    .orElseThrow(() -> new IllegalArgumentException("Order not found with ID: " + event.getOrderId()));

            // Update status to PROCESSED
            order.setStatus(OrderStatus.PROCESSED);
            orderRepository.save(order);
            log.info("Order ID {} status updated to PROCESSED", order.getId());

            // Save product sales count in Redis Sorted Set
            for (OrderPlacedEvent.OrderItemEvent item : event.getItems()) {
                productService.recordProductSales(item.getProductId(), item.getQuantity());
            }

            // Publish processed event
            eventProducer.publishOrderProcessed(order.getId());
            
        } catch (Exception e) {
            log.error("Failed to process OrderPlacedEvent for Order ID: {}", event.getOrderId(), e);
        }
    }
}
