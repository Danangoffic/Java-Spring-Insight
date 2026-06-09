package com.example.insight.controller;

import com.example.insight.dto.OrderRequest;
import com.example.insight.model.Order;
import com.example.insight.model.mongodb.OrderAudit;
import com.example.insight.repository.mongodb.OrderAuditRepository;
import com.example.insight.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final OrderAuditRepository orderAuditRepository;

    @PostMapping
    public ResponseEntity<Order> placeOrder(@Valid @RequestBody OrderRequest request) {
        Order order = orderService.placeOrder(
                request.getCustomerName(),
                request.getPaymentMethod(),
                request.getItems()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }

    @GetMapping("/{id}/audit")
    public ResponseEntity<List<OrderAudit>> getOrderAudit(@PathVariable("id") Long id) {
        return ResponseEntity.ok(orderAuditRepository.findByOrderIdOrderByTimestampDesc(id));
    }
}
