package com.example.insight.service.payment;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CreditCardPayment implements PaymentStrategy {

    @Override
    public boolean supports(String paymentMethod) {
        return "CREDIT_CARD".equalsIgnoreCase(paymentMethod);
    }

    @Override
    public void process(double amount) {
        log.info("Processing credit card payment of ${} using Stripe gateway", amount);
        // In a real application, integration with Stripe/Adyen would occur here
    }
}
