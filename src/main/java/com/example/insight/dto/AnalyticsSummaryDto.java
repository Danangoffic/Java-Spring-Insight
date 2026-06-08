package com.example.insight.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsSummaryDto {
    private Double totalRevenue;
    private Double averageOrderValue;
    private Long processedOrdersCount;
    private Map<String, Double> revenueByPaymentMethod;
    private List<Long> highValueOrderIds; // Orders > $150
    private List<Long> lowValueOrderIds;  // Orders <= $150
}
