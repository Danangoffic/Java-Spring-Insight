package com.example.insight.service;

import com.example.insight.model.Order;
import com.example.insight.model.OrderItem;
import com.example.insight.model.OrderStatus;
import com.example.insight.model.Product;
import com.example.insight.messaging.OrderEventProducer;
import com.example.insight.messaging.OrderPlacedEvent;
import com.example.insight.repository.OrderRepository;
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
import java.util.List;
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

    @Transactional
    public Order placeOrder(String customerName, String paymentMethod, List<OrderItemRequest> itemRequests) {
        log.info("Processing order placement for customer: {}", customerName);

        List<OrderItem> orderItems = new ArrayList<>();
        double totalAmount = 0.0;

        // 1. Process items, validating inventory using Redis distributed lock
        for (OrderItemRequest request : itemRequests) {
            String lockKey = "lock:product:" + request.getProductId();
            
            // Acquire Redis distributed lock with retry
            if (!acquireLockWithRetry(lockKey, 5, 200)) {
                throw new IllegalStateException("Failed to acquire lock for product ID " + request.getProductId() + ". Please try again.");
            }

            try {
                // Critical section: Fetch product, validate stock, and decrement inventory
                Product product = productService.getProductById(request.getProductId());
                if (product.getStock() < request.getQuantity()) {
                    throw new IllegalArgumentException("Insufficient stock for product: " + product.getName() 
                            + ". Available: " + product.getStock() + ", Requested: " + request.getQuantity());
                }

                // Decrement stock and update
                product.setStock(product.getStock() - request.getQuantity());
                productService.updateProduct(product.getId(), product);

                // Build order line item
                OrderItem orderItem = OrderItem.builder()
                        .product(product)
                        .quantity(request.getQuantity())
                        .price(product.getPrice()) // snapshot price
                        .build();

                orderItems.add(orderItem);
                totalAmount += (product.getPrice() * request.getQuantity());

            } finally {
                // Release the lock
                redisTemplate.delete(lockKey);
            }
        }

        // 2. Resolve payment strategy via Spring IoC and process payment
        PaymentStrategy paymentStrategy = paymentResolver.resolve(paymentMethod);
        paymentStrategy.process(totalAmount);

        // 3. Persist Order as PENDING (will be completed asynchronously via Kafka)
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
        log.info("Order saved in PENDING state with ID: {}", savedOrder.getId());

        // 4. Emit event to Kafka for stream processing
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
