package com.example.insight.service;

import com.example.insight.service.payment.CreditCardPayment;
import com.example.insight.service.payment.EWalletPayment;
import com.example.insight.service.payment.PaymentResolver;
import com.example.insight.service.payment.PaymentStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class PaymentStrategyTest {

    private PaymentResolver paymentResolver;
    private PaymentStrategy creditCardPayment;
    private PaymentStrategy eWalletPayment;

    @BeforeEach
    void setUp() {
        creditCardPayment = new CreditCardPayment();
        eWalletPayment = new EWalletPayment();
        
        // Simulating Spring IoC by passing the list of strategy beans to the constructor
        paymentResolver = new PaymentResolver(Arrays.asList(creditCardPayment, eWalletPayment));
    }

    @Test
    void testResolveCreditCard() {
        PaymentStrategy strategy = paymentResolver.resolve("CREDIT_CARD");
        assertNotNull(strategy);
        assertTrue(strategy instanceof CreditCardPayment);
    }

    @Test
    void testResolveEWallet() {
        PaymentStrategy strategy = paymentResolver.resolve("E_WALLET");
        assertNotNull(strategy);
        assertTrue(strategy instanceof EWalletPayment);
    }

    @Test
    void testResolveUnsupported() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            paymentResolver.resolve("BITCOIN");
        });
        
        String expectedMessage = "No payment strategy registered for method: BITCOIN";
        String actualMessage = exception.getMessage();
        
        assertTrue(actualMessage.contains(expectedMessage));
    }
}
