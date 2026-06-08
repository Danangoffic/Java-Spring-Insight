package com.example.insight.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductLeaderboardDto {
    private Long productId;
    private String productName;
    private Double unitsSold;
}
