package com.example.insight.service.payment;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class EWalletPayment implements PaymentStrategy {

    @Override
    public boolean supports(String paymentMethod) {
        return "E_WALLET".equalsIgnoreCase(paymentMethod);
    }

    @Override
    public void process(double amount) {
        log.info("Processing e-wallet payment of ${} using E-Wallet API", amount);
        // In a real application, integration with PayPal/Doku/GoPay would occur here
    }
}
