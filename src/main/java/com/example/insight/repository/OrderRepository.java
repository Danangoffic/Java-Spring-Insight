package com.example.insight.repository;

import com.example.insight.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    // Projection for Daily Sales Trend
    interface SalesTrendProjection {
        java.sql.Date getSaleDate();
        Double getDailyAmount();
        Double getCumulativeAmount();
    }

    // Projection for Top Products Rank
    interface ProductRevenueRankProjection {
        Long getProductId();
        String getProductName();
        String getCategoryName();
        Double getTotalRevenue();
        Integer getRankVal();
    }

    /**
     * Query 1: Get daily sales and running total cumulative sales
     */
    @Query(value = """
            WITH DailySales AS (
                SELECT 
                    CAST(order_date AS DATE) AS sale_date,
                    SUM(total_amount) AS daily_amount
                FROM orders
                WHERE status = 'PROCESSED'
                GROUP BY CAST(order_date AS DATE)
            )
            SELECT 
                sale_date AS saleDate,
                daily_amount AS dailyAmount,
                SUM(daily_amount) OVER (ORDER BY sale_date ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS cumulativeAmount
            FROM DailySales
            ORDER BY sale_date DESC
            """, nativeQuery = true)
    List<SalesTrendProjection> findDailySalesTrends();

    /**
     * Query 2: Get top products ranked by total revenue per category
     */
    @Query(value = """
            WITH ProductRevenue AS (
                SELECT 
                    p.id AS productId,
                    p.name AS productName,
                    p.category AS categoryName,
                    SUM(oi.quantity * oi.price) AS totalRevenue
                FROM order_items oi
                JOIN products p ON oi.product_id = p.id
                JOIN orders o ON oi.order_id = o.id
                WHERE o.status = 'PROCESSED'
                GROUP BY p.id, p.name, p.category
            ),
            RankedProducts AS (
                SELECT 
                    productId,
                    productName,
                    categoryName,
                    totalRevenue,
                    DENSE_RANK() OVER (PARTITION BY categoryName ORDER BY totalRevenue DESC) AS rankVal
                FROM ProductRevenue
            )
            SELECT 
                productId,
                productName,
                categoryName,
                totalRevenue,
                rankVal
            FROM RankedProducts
            WHERE rankVal <= :maxRank
            ORDER BY categoryName, rankVal
            """, nativeQuery = true)
    List<ProductRevenueRankProjection> findTopProductsByCategory(@Param("maxRank") int maxRank);
}
