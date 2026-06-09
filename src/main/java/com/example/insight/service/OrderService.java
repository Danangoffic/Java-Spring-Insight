package com.example.insight.service;

import com.example.insight.model.Order;
import com.example.insight.model.OrderItem;
import com.example.insight.model.OrderStatus;
import com.example.insight.model.Product;
import com.example.insight.model.mongodb.OrderAudit;
import com.example.insight.messaging.OrderEventProducer;
import com.example.insight.messaging.OrderPlacedEvent;
import com.example.insight.repository.OrderRepository;
import com.example.insight.repository.mongodb.OrderAuditRepository;
import com.example.insight.service.payment.PaymentResolver;
import com.example.insight.service.payment.PaymentStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductService productService;
    private final PaymentResolver paymentResolver;
    private final OrderEventProducer eventProducer;
    private final RedisTemplate<String, Object> redisTemplate;
    private final OrderAuditRepository orderAuditRepository;

    @Transactional
    public Order placeOrder(String customerName, String paymentMethod, List<OrderItemRequest> itemRequests) {
        log.info("Starting order checkout for customer: {}", customerName);

        List<OrderItem> orderItems = new ArrayList<>();
        double totalAmount = 0.0;

        // Loop through each item in the order request
        for (OrderItemRequest request : itemRequests) {
            String lockKey = "lock:product:" + request.getProductId();
            
            // Lock the product using Redis to avoid double buying
            if (!acquireLockWithRetry(lockKey, 5, 200)) {
                log.warn("Lock failed for product ID {}. Product is busy.", request.getProductId());
                throw new IllegalStateException("Product is currently busy, please try again.");
            }

            try {
                // Check if we have enough stock in database
                Product product = productService.getProductById(request.getProductId());
                if (product.getStock() < request.getQuantity()) {
                    log.warn("Not enough stock for {}. Has: {}, Need: {}", 
                            product.getName(), product.getStock(), request.getQuantity());
                    throw new IllegalArgumentException("Insufficient stock for: " + product.getName());
                }

                // Decrement stock and save
                product.setStock(product.getStock() - request.getQuantity());
                productService.updateProduct(product.getId(), product);

                // Save price snapshot
                OrderItem orderItem = OrderItem.builder()
                        .product(product)
                        .quantity(request.getQuantity())
                        .price(product.getPrice())
                        .build();

                orderItems.add(orderItem);
                totalAmount += (product.getPrice() * request.getQuantity());

            } finally {
                // Always delete the lock in finally block
                redisTemplate.delete(lockKey);
            }
        }

        // Process payment dynamically using our payment strategies
        log.info("Processing payment via: {}", paymentMethod);
        PaymentStrategy paymentStrategy = paymentResolver.resolve(paymentMethod);
        paymentStrategy.process(totalAmount);

        // Save order as PENDING first (Kafka consumer will set it to PROCESSED)
        Order order = Order.builder()
                .customerName(customerName)
                .paymentMethod(paymentMethod)
                .totalAmount(totalAmount)
                .status(OrderStatus.PENDING)
                .orderDate(LocalDateTime.now())
                .build();

        for (OrderItem item : orderItems) {
            order.addOrderItem(item);
        }

        Order savedOrder = orderRepository.save(order);
        log.info("Order saved as PENDING with ID: {}", savedOrder.getId());

        // Save order placement audit trail in MongoDB
        try {
            Map<String, Object> auditDetails = new HashMap<>();
            auditDetails.put("customer", customerName);
            auditDetails.put("totalAmount", totalAmount);
            auditDetails.put("paymentMethod", paymentMethod);
            auditDetails.put("itemCount", itemRequests.size());
            
            OrderAudit audit = OrderAudit.builder()
                    .orderId(savedOrder.getId())
                    .action("ORDER_PLACED")
                    .timestamp(LocalDateTime.now())
                    .details(auditDetails)
                    .build();
            orderAuditRepository.save(audit);
            log.info("Saved ORDER_PLACED audit log in MongoDB for Order ID: {}", savedOrder.getId());
        } catch (Exception e) {
            log.error("Failed to save ORDER_PLACED audit log in MongoDB", e);
        }

        // Send order event to Kafka topic
        List<OrderPlacedEvent.OrderItemEvent> eventItems = savedOrder.getItems().stream()
                .map(item -> OrderPlacedEvent.OrderItemEvent.builder()
                        .productId(item.getProduct().getId())
                        .quantity(item.getQuantity())
                        .price(item.getPrice())
                        .build())
                .collect(Collectors.toList());

        OrderPlacedEvent orderEvent = OrderPlacedEvent.builder()
                .orderId(savedOrder.getId())
                .customerName(savedOrder.getCustomerName())
                .paymentMethod(savedOrder.getPaymentMethod())
                .totalAmount(savedOrder.getTotalAmount())
                .items(eventItems)
                .build();

        eventProducer.publishOrderPlaced(orderEvent);

        return savedOrder;
    }

    private boolean acquireLockWithRetry(String lockKey, int retries, long delayMs) {
        for (int i = 0; i < retries; i++) {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "LOCKED", Duration.ofSeconds(5));
            if (Boolean.TRUE.equals(acquired)) {
                return true;
            }
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    // Helper static class for request mapping
    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class OrderItemRequest {
        private Long productId;
        private Integer quantity;
    }
}
