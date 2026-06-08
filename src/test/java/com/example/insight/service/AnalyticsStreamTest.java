package com.example.insight.service;

import com.example.insight.dto.AnalyticsSummaryDto;
import com.example.insight.model.Order;
import com.example.insight.model.OrderStatus;
import com.example.insight.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsStreamTest {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private AnalyticsService analyticsService;

    private List<Order> mockOrders;

    @BeforeEach
    void setUp() {
        // Build mock processed order 1 ($200, credit card)
        Order o1 = Order.builder()
                .id(1L)
                .customerName("John Doe")
                .paymentMethod("CREDIT_CARD")
                .status(OrderStatus.PROCESSED)
                .totalAmount(200.0)
                .orderDate(LocalDateTime.now())
                .build();

        // Build mock processed order 2 ($100, e-wallet)
        Order o2 = Order.builder()
                .id(2L)
                .customerName("Jane Doe")
                .paymentMethod("E_WALLET")
                .status(OrderStatus.PROCESSED)
                .totalAmount(100.0)
                .orderDate(LocalDateTime.now())
                .build();

        // Build mock pending order 3 (should be ignored in processed metrics)
        Order o3 = Order.builder()
                .id(3L)
                .customerName("Jack Doe")
                .paymentMethod("CREDIT_CARD")
                .status(OrderStatus.PENDING)
                .totalAmount(300.0)
                .orderDate(LocalDateTime.now())
                .build();

        mockOrders = Arrays.asList(o1, o2, o3);
    }

    @Test
    void testGetStreamsSummary() {
        when(orderRepository.findAll()).thenReturn(mockOrders);

        AnalyticsSummaryDto summary = analyticsService.getStreamsSummary();

        // Check total revenue: 200 + 100 = 300 (Pending order ignored)
        assertEquals(300.0, summary.getTotalRevenue());

        // Check processed count
        assertEquals(2L, summary.getProcessedOrdersCount());

        // Check average order value: 300 / 2 = 150
        assertEquals(150.0, summary.getAverageOrderValue());

        // Check grouping by payment method
        assertEquals(200.0, summary.getRevenueByPaymentMethod().get("CREDIT_CARD"));
        assertEquals(100.0, summary.getRevenueByPaymentMethod().get("E_WALLET"));

        // Check partitioning: order 1 is high value (>150), order 2 is low value
        assertTrue(summary.getHighValueOrderIds().contains(1L));
        assertTrue(summary.getLowValueOrderIds().contains(2L));
        assertEquals(1, summary.getHighValueOrderIds().size());
        assertEquals(1, summary.getLowValueOrderIds().size());
    }
}
