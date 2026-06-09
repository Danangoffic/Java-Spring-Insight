package com.example.insight.config;

import com.example.insight.messaging.OrderEventConsumer;
import com.example.insight.messaging.OrderPlacedEvent;
import com.example.insight.model.elasticsearch.ProductDocument;
import com.example.insight.model.mongodb.OrderAudit;
import com.example.insight.repository.elasticsearch.ProductSearchRepository;
import com.example.insight.repository.mongodb.OrderAuditRepository;
import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Configuration
@Profile("mock")
public class MockInfrastructureConfig {

    private final Map<String, Double> leaderboard = new ConcurrentHashMap<>();
    private final Set<String> locks = ConcurrentHashMap.newKeySet();

    // In-memory list stores to simulate DB tables for Elasticsearch and MongoDB
    private final List<ProductDocument> mockEsIndex = Collections.synchronizedList(new ArrayList<>());
    private final List<OrderAudit> mockMongoCollection = Collections.synchronizedList(new ArrayList<>());

    @Bean
    @SuppressWarnings("unchecked")
    public RedisTemplate<String, Object> redisTemplate() {
        RedisTemplate<String, Object> template = Mockito.mock(RedisTemplate.class);
        ZSetOperations<String, Object> zSetOps = Mockito.mock(ZSetOperations.class);
        ValueOperations<String, Object> valueOps = Mockito.mock(ValueOperations.class);

        // Mock ZSet operations for Leaderboard
        Mockito.when(template.opsForZSet()).thenReturn(zSetOps);
        
        Mockito.when(zSetOps.incrementScore(Mockito.anyString(), Mockito.any(), Mockito.anyDouble()))
                .thenAnswer(invocation -> {
                    String val = invocation.getArgument(1).toString();
                    Double delta = invocation.getArgument(2);
                    leaderboard.merge(val, delta, Double::sum);
                    return leaderboard.get(val);
                });

        Mockito.when(zSetOps.reverseRangeWithScores(Mockito.anyString(), Mockito.anyLong(), Mockito.anyLong()))
                .thenAnswer(invocation -> {
                    return leaderboard.entrySet().stream()
                            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                            .map(entry -> {
                                TypedTuple<Object> tuple = Mockito.mock(TypedTuple.class);
                                Mockito.when(tuple.getValue()).thenReturn(entry.getKey());
                                Mockito.when(tuple.getScore()).thenReturn(entry.getValue());
                                return tuple;
                            })
                            .collect(Collectors.toCollection(LinkedHashSet::new));
                });

        // Mock Value operations for Distributed Lock
        Mockito.when(template.opsForValue()).thenReturn(valueOps);
        
        Mockito.when(valueOps.setIfAbsent(Mockito.anyString(), Mockito.any(), Mockito.any(Duration.class)))
                .thenAnswer(invocation -> {
                    String key = invocation.getArgument(0);
                    return locks.add(key); // returns true if lock key was added (not present), false if already locked
                });

        Mockito.when(template.delete(Mockito.anyString()))
                .thenAnswer(invocation -> {
                    String key = invocation.getArgument(0);
                    return locks.remove(key); // returns true if lock key was deleted
                });

        return template;
    }

    @Bean
    @SuppressWarnings("unchecked")
    public KafkaTemplate<String, Object> kafkaTemplate(@Lazy OrderEventConsumer consumer) {
        KafkaTemplate<String, Object> template = Mockito.mock(KafkaTemplate.class);

        // Mock event sending by delivering to local consumer in an async thread to simulate network lag
        Mockito.when(template.send(Mockito.anyString(), Mockito.anyString(), Mockito.any()))
                .thenAnswer(invocation -> {
                    String topic = invocation.getArgument(0);
                    Object payload = invocation.getArgument(2);
                    
                    if ("orders-topic".equals(topic) && payload instanceof OrderPlacedEvent) {
                        new Thread(() -> {
                            try {
                                Thread.sleep(300); // 300ms network lag simulation
                                consumer.consumeOrderPlaced((OrderPlacedEvent) payload);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        }).start();
                    }
                    return null;
                });

        return template;
    }

    @Bean
    @SuppressWarnings("unchecked")
    public ProductSearchRepository productSearchRepository() {
        ProductSearchRepository repo = Mockito.mock(ProductSearchRepository.class);

        // Mock save
        Mockito.when(repo.save(Mockito.any(ProductDocument.class))).thenAnswer(invocation -> {
            ProductDocument doc = invocation.getArgument(0);
            mockEsIndex.removeIf(d -> d.getId().equals(doc.getId()));
            mockEsIndex.add(doc);
            return doc;
        });

        // Mock deleteById
        Mockito.doAnswer(invocation -> {
            String id = invocation.getArgument(0);
            mockEsIndex.removeIf(d -> d.getId().equals(id));
            return null;
        }).when(repo).deleteById(Mockito.anyString());

        // Mock findByNameContainingOrCategoryContaining
        Mockito.when(repo.findByNameContainingOrCategoryContaining(Mockito.anyString(), Mockito.anyString()))
                .thenAnswer(invocation -> {
                    String query = invocation.getArgument(0).toString().toLowerCase();
                    return mockEsIndex.stream()
                            .filter(d -> d.getName().toLowerCase().contains(query) || d.getCategory().toLowerCase().contains(query))
                            .collect(Collectors.toList());
                });

        return repo;
    }

    @Bean
    @SuppressWarnings("unchecked")
    public OrderAuditRepository orderAuditRepository() {
        OrderAuditRepository repo = Mockito.mock(OrderAuditRepository.class);

        // Mock save
        Mockito.when(repo.save(Mockito.any(OrderAudit.class))).thenAnswer(invocation -> {
            OrderAudit audit = invocation.getArgument(0);
            if (audit.getId() == null) {
                audit.setId(UUID.randomUUID().toString());
            }
            mockMongoCollection.add(audit);
            return audit;
        });

        // Mock findByOrderIdOrderByTimestampDesc
        Mockito.when(repo.findByOrderIdOrderByTimestampDesc(Mockito.anyLong())).thenAnswer(invocation -> {
            Long orderId = invocation.getArgument(0);
            return mockMongoCollection.stream()
                    .filter(a -> a.getOrderId().equals(orderId))
                    .sorted(Comparator.comparing(OrderAudit::getTimestamp).reversed())
                    .collect(Collectors.toList());
        });

        return repo;
    }
}
