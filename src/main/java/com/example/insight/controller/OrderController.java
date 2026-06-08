package com.example.insight.controller;

import com.example.insight.dto.OrderRequest;
import com.example.insight.model.Order;
import com.example.insight.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<Order> placeOrder(@Valid @RequestBody OrderRequest request) {
        Order order = orderService.placeOrder(
                request.getCustomerName(),
                request.getPaymentMethod(),
                request.getItems()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }
}
