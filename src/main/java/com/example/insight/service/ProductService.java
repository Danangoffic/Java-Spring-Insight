package com.example.insight.service;

import com.example.insight.dto.ProductLeaderboardDto;
import com.example.insight.model.Product;
import com.example.insight.model.elasticsearch.ProductDocument;
import com.example.insight.repository.ProductRepository;
import com.example.insight.repository.elasticsearch.ProductSearchRepository;
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
    private final ProductSearchRepository productSearchRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    public static final String LEADERBOARD_KEY = "leaderboard:products:sold";

    @Transactional(readOnly = true)
    public List<Product> getAllProducts() {
        log.info("Fetching all products from database");
        return productRepository.findAll();
    }

    // Cache products by ID in Redis (key will be "products::id")
    @Cacheable(value = "products", key = "#id")
    @Transactional(readOnly = true)
    public Product getProductById(Long id) {
        log.info("Cache miss for product ID: {}. Fetching from DB.", id);
        return productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found with id: " + id));
    }

    // Save product to database and update Redis cache
    @CachePut(value = "products", key = "#product.id")
    @Transactional
    public Product createProduct(Product product) {
        log.info("Saving product: {}", product.getName());
        Product saved = productRepository.save(product);
        
        // Sync to Elasticsearch index
        ProductDocument doc = ProductDocument.builder()
                .id(saved.getId().toString())
                .name(saved.getName())
                .category(saved.getCategory())
                .price(saved.getPrice())
                .stock(saved.getStock())
                .build();
        productSearchRepository.save(doc);
        
        return saved;
    }

    // Update product and save in Redis
    @CachePut(value = "products", key = "#id")
    @Transactional
    public Product updateProduct(Long id, Product productDetails) {
        log.info("Updating product ID: {}", id);
        Product product = getProductById(id);
        product.setName(productDetails.getName());
        product.setCategory(productDetails.getCategory());
        product.setPrice(productDetails.getPrice());
        product.setStock(productDetails.getStock());
        Product saved = productRepository.save(product);
        
        // Sync update to Elasticsearch index
        ProductDocument doc = ProductDocument.builder()
                .id(saved.getId().toString())
                .name(saved.getName())
                .category(saved.getCategory())
                .price(saved.getPrice())
                .stock(saved.getStock())
                .build();
        productSearchRepository.save(doc);
        
        return saved;
    }

    // Delete product and evict from cache
    @CacheEvict(value = "products", key = "#id")
    @Transactional
    public void deleteProduct(Long id) {
        log.info("Deleting product ID: {}", id);
        productRepository.deleteById(id);
        
        // Remove from Elasticsearch index
        productSearchRepository.deleteById(id.toString());
    }

    /**
     * Increment units sold score in Redis ZSET
     */
    public void recordProductSales(Long productId, int quantity) {
        log.info("Recording sold score in Redis ZSET. Product ID: {}, quantity: {}", productId, quantity);
        redisTemplate.opsForZSet().incrementScore(LEADERBOARD_KEY, productId.toString(), quantity);
    }

    /**
     * Get top sold products from Redis
     */
    public List<ProductLeaderboardDto> getSalesLeaderboard(int limit) {
        log.info("Getting top {} selling products from Redis", limit);
        Set<ZSetOperations.TypedTuple<Object>> range = redisTemplate.opsForZSet()
                .reverseRangeWithScores(LEADERBOARD_KEY, 0, limit - 1);

        if (range == null || range.isEmpty()) {
            return Collections.emptyList();
        }

        // Loop through Redis range result and map to DTOs
        return range.stream().map(tuple -> {
            Long productId = Long.valueOf(tuple.getValue().toString());
            Double score = tuple.getScore();
            
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

    /**
     * Search products in Elasticsearch index
     */
    public List<ProductDocument> searchProducts(String query) {
        log.info("Searching products in Elasticsearch for query: {}", query);
        return productSearchRepository.findByNameContainingOrCategoryContaining(query, query);
    }
}
