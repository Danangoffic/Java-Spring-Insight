package com.example.insight.service;

import com.example.insight.dto.ProductLeaderboardDto;
import com.example.insight.model.Product;
import com.example.insight.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    public static final String LEADERBOARD_KEY = "leaderboard:products:sold";

    @Transactional(readOnly = true)
    public List<Product> getAllProducts() {
        log.info("Fetching all products from DB");
        return productRepository.findAll();
    }

    @Cacheable(value = "products", key = "#id")
    @Transactional(readOnly = true)
    public Product getProductById(Long id) {
        log.info("Cache miss: Fetching product ID {} from DB", id);
        return productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found with id: " + id));
    }

    @CachePut(value = "products", key = "#product.id")
    @Transactional
    public Product createProduct(Product product) {
        log.info("Saving product to DB and updating cache: {}", product.getName());
        return productRepository.save(product);
    }

    @CachePut(value = "products", key = "#id")
    @Transactional
    public Product updateProduct(Long id, Product productDetails) {
        log.info("Updating product ID {} in DB and cache", id);
        Product product = getProductById(id);
        product.setName(productDetails.getName());
        product.setCategory(productDetails.getCategory());
        product.setPrice(productDetails.getPrice());
        product.setStock(productDetails.getStock());
        return productRepository.save(product);
    }

    @CacheEvict(value = "products", key = "#id")
    @Transactional
    public void deleteProduct(Long id) {
        log.info("Deleting product ID {} and evicting from cache", id);
        productRepository.deleteById(id);
    }

    /**
     * Updates the sales leaderboard in Redis Sorted Set.
     */
    public void recordProductSales(Long productId, int quantity) {
        log.info("Recording sale in Redis ZSET: product ID {} with quantity {}", productId, quantity);
        redisTemplate.opsForZSet().incrementScore(LEADERBOARD_KEY, productId.toString(), quantity);
    }

    /**
     * Fetches the top-selling products leaderboard from Redis.
     */
    public List<ProductLeaderboardDto> getSalesLeaderboard(int limit) {
        log.info("Fetching top {} selling products from Redis ZSET", limit);
        Set<ZSetOperations.TypedTuple<Object>> range = redisTemplate.opsForZSet()
                .reverseRangeWithScores(LEADERBOARD_KEY, 0, limit - 1);

        if (range == null || range.isEmpty()) {
            return Collections.emptyList();
        }

        // Use Java Streams to map the Redis ZSET tuple results to Leaderboard DTOs
        return range.stream().map(tuple -> {
            Long productId = Long.valueOf(tuple.getValue().toString());
            Double score = tuple.getScore();
            
            // Get product (uses Redis cache if available)
            String productName;
            try {
                Product product = getProductById(productId);
                productName = product.getName();
            } catch (Exception e) {
                productName = "Unknown Product";
            }
            
            return ProductLeaderboardDto.builder()
                    .productId(productId)
                    .productName(productName)
                    .unitsSold(score)
                    .build();
        }).collect(Collectors.toList());
    }
}
