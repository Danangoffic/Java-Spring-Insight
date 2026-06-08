package com.example.insight.service.payment;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PaymentResolver {

    private final List<PaymentStrategy> strategies;

    // Spring IoC automatically wires all beans implementing PaymentStrategy here
    public PaymentResolver(List<PaymentStrategy> strategies) {
        this.strategies = strategies;
    }

    public PaymentStrategy resolve(String paymentMethod) {
        return strategies.stream()
                .filter(strategy -> strategy.supports(paymentMethod))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No payment strategy registered for method: " + paymentMethod));
    }
}
