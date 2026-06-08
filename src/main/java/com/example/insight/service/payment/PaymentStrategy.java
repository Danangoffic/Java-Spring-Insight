package com.example.insight.service.payment;

public interface PaymentStrategy {
    boolean supports(String paymentMethod);
    void process(double amount);
}
