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
     * Compute real-time statistics in memory using Java Streams.
     */
    @Transactional(readOnly = true)
    public AnalyticsSummaryDto getStreamsSummary() {
        log.info("Generating analytics summary in memory using Java Streams");
        List<Order> allOrders = orderRepository.findAll();

        // 1. Filter processed orders
        List<Order> processedOrders = allOrders.stream()
                .filter(order -> order.getStatus() == OrderStatus.PROCESSED)
                .collect(Collectors.toList());

        // 2. Calculate total revenue using Stream Map & Reduce
        double totalRevenue = processedOrders.stream()
                .mapToDouble(Order::getTotalAmount)
                .reduce(0.0, Double::sum);

        // 3. Calculate average order value
        double averageOrderValue = processedOrders.stream()
                .mapToDouble(Order::getTotalAmount)
                .average()
                .orElse(0.0);

        // 4. Group revenue by Payment Method using groupingBy collector
        Map<String, Double> revenueByPayment = processedOrders.stream()
                .collect(Collectors.groupingBy(
                        Order::getPaymentMethod,
                        Collectors.summingDouble(Order::getTotalAmount)
                ));

        // 5. Partition orders into High Value (> $150) vs Low Value using partitioningBy collector
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
     * Fetch daily sales trends using CTE and running total Window Functions (Database-level analytics).
     */
    @Cacheable(value = "analytics", key = "'sales-trends'")
    @Transactional(readOnly = true)
    public List<OrderRepository.SalesTrendProjection> getDailySalesTrends() {
        log.info("Cache miss: Running Daily Sales Trend native SQL query");
        return orderRepository.findDailySalesTrends();
    }

    /**
     * Fetch top products by category using CTE and DENSE_RANK() Window Functions (Database-level analytics).
     */
    @Cacheable(value = "analytics", key = "'top-products-rank-' + #maxRank")
    @Transactional(readOnly = true)
    public List<OrderRepository.ProductRevenueRankProjection> getTopProductsByCategory(int maxRank) {
        log.info("Cache miss: Running Top Products Rank native SQL query with rank limit: {}", maxRank);
        return orderRepository.findTopProductsByCategory(maxRank);
    }
}
