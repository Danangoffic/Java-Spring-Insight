package com.example.insight.service;

import com.example.insight.dto.AnalyticsSummaryDto;
import com.example.insight.model.Order;
import com.example.insight.model.OrderStatus;
import com.example.insight.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final OrderRepository orderRepository;

    /**
     * Calculate sales statistics in-memory using Java Streams.
     */
    @Transactional(readOnly = true)
    public AnalyticsSummaryDto getStreamsSummary() {
        log.info("Calculating order summary stats using Streams");
        List<Order> allOrders = orderRepository.findAll();

        // Filter: only processed orders
        List<Order> processedOrders = allOrders.stream()
                .filter(order -> order.getStatus() == OrderStatus.PROCESSED)
                .collect(Collectors.toList());

        // Sum up total revenue
        double totalRevenue = processedOrders.stream()
                .mapToDouble(Order::getTotalAmount)
                .reduce(0.0, Double::sum);

        // Calculate average order value
        double averageOrderValue = processedOrders.stream()
                .mapToDouble(Order::getTotalAmount)
                .average()
                .orElse(0.0);

        // Group revenue by payment method
        Map<String, Double> revenueByPayment = processedOrders.stream()
                .collect(Collectors.groupingBy(
                        Order::getPaymentMethod,
                        Collectors.summingDouble(Order::getTotalAmount)
                ));

        // Partition orders: high value (> 150) vs low value
        Map<Boolean, List<Order>> partitionedOrders = processedOrders.stream()
                .collect(Collectors.partitioningBy(order -> order.getTotalAmount() > 150.0));

        List<Long> highValueIds = partitionedOrders.get(true).stream()
                .map(Order::getId)
                .collect(Collectors.toList());

        List<Long> lowValueIds = partitionedOrders.get(false).stream()
                .map(Order::getId)
                .collect(Collectors.toList());

        return AnalyticsSummaryDto.builder()
                .totalRevenue(totalRevenue)
                .averageOrderValue(averageOrderValue)
                .processedOrdersCount((long) processedOrders.size())
                .revenueByPaymentMethod(revenueByPayment)
                .highValueOrderIds(highValueIds)
                .lowValueOrderIds(lowValueIds)
                .build();
    }

    /**
     * Get daily sales trends using CTE + Window query from database
     */
    @Cacheable(value = "analytics", key = "'sales-trends'")
    @Transactional(readOnly = true)
    public List<OrderRepository.SalesTrendProjection> getDailySalesTrends() {
        log.info("Running daily sales trends query (Cache miss)");
        return orderRepository.findDailySalesTrends();
    }

    /**
     * Get ranked top selling products per category from database
     */
    @Cacheable(value = "analytics", key = "'top-products-rank-' + #maxRank")
    @Transactional(readOnly = true)
    public List<OrderRepository.ProductRevenueRankProjection> getTopProductsByCategory(int maxRank) {
        log.info("Running top products per category query (Cache miss). Max rank: {}", maxRank);
        return orderRepository.findTopProductsByCategory(maxRank);
    }
}
