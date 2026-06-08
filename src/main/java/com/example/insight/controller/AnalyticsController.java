package com.example.insight.controller;

import com.example.insight.dto.AnalyticsSummaryDto;
import com.example.insight.dto.ProductLeaderboardDto;
import com.example.insight.repository.OrderRepository;
import com.example.insight.service.AnalyticsService;
import com.example.insight.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final ProductService productService;

    /**
     * Endpoint for Java Streams metrics calculation.
     */
    @GetMapping("/streams/summary")
    public ResponseEntity<AnalyticsSummaryDto> getStreamsSummary() {
        return ResponseEntity.ok(analyticsService.getStreamsSummary());
    }

    /**
     * Endpoint for Advanced Native SQL (CTE + Window function for Daily Cumulative Sales).
     */
    @GetMapping("/native/sales-trends")
    public ResponseEntity<List<OrderRepository.SalesTrendProjection>> getDailySalesTrends() {
        return ResponseEntity.ok(analyticsService.getDailySalesTrends());
    }

    /**
     * Endpoint for Advanced Native SQL (CTE + Joins + DENSE_RANK() for top products in category).
     */
    @GetMapping("/native/top-products")
    public ResponseEntity<List<OrderRepository.ProductRevenueRankProjection>> getTopProducts(
            @RequestParam(value = "maxRank", defaultValue = "3") int maxRank) {
        return ResponseEntity.ok(analyticsService.getTopProductsByCategory(maxRank));
    }

    /**
     * Endpoint for Redis Sorted Set popular products leaderboard.
     */
    @GetMapping("/leaderboard")
    public ResponseEntity<List<ProductLeaderboardDto>> getLeaderboard(
            @RequestParam(value = "limit", defaultValue = "5") int limit) {
        return ResponseEntity.ok(productService.getSalesLeaderboard(limit));
    }
}
