package com.example.insight.config;

import com.example.insight.model.Order;
import com.example.insight.model.OrderItem;
import com.example.insight.model.OrderStatus;
import com.example.insight.model.Product;
import com.example.insight.repository.OrderRepository;
import com.example.insight.repository.ProductRepository;
import com.example.insight.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final ProductService productService;

    @Override
    public void run(String... args) throws Exception {
        if (productRepository.count() > 0) {
            log.info("Database already initialized with products. Skipping seeding.");
            return;
        }

        log.info("Starting database initialization...");

        // 1. Seed Products
        Product laptop = Product.builder().name("MacBook Pro").category("Electronics").price(1500.0).stock(20).build();
        Product phone = Product.builder().name("iPhone 15").category("Electronics").price(1000.0).stock(30).build();
        Product headphones = Product.builder().name("Sony WH-1000XM5").category("Electronics").price(350.0).stock(50).build();
        Product coffeeMaker = Product.builder().name("Espresso Machine").category("Appliances").price(500.0).stock(15).build();
        Product airFryer = Product.builder().name("Digital Air Fryer").category("Appliances").price(120.0).stock(25).build();
        Product deskLamp = Product.builder().name("LED Desk Lamp").category("Office").price(45.0).stock(40).build();

        List<Product> products = Arrays.asList(laptop, phone, headphones, coffeeMaker, airFryer, deskLamp);
        productRepository.saveAll(products);
        log.info("Seeded {} products", products.size());

        // 2. Seed Historical Orders (for window functions and CTE trends)
        LocalDateTime now = LocalDateTime.now();

        // Order 1: Alice, 2 days ago
        Order order1 = Order.builder()
                .customerName("Alice Smith")
                .paymentMethod("CREDIT_CARD")
                .status(OrderStatus.PROCESSED)
                .orderDate(now.minusDays(2))
                .totalAmount(0.0) // calculated below
                .build();
        OrderItem item1 = OrderItem.builder().order(order1).product(laptop).quantity(1).price(laptop.getPrice()).build();
        OrderItem item2 = OrderItem.builder().order(order1).product(headphones).quantity(2).price(headphones.getPrice()).build();
        order1.addOrderItem(item1);
        order1.addOrderItem(item2);
        order1.setTotalAmount(item1.getPrice() * item1.getQuantity() + item2.getPrice() * item2.getQuantity());

        // Order 2: Bob, 1 day ago
        Order order2 = Order.builder()
                .customerName("Bob Jones")
                .paymentMethod("E_WALLET")
                .status(OrderStatus.PROCESSED)
                .orderDate(now.minusDays(1))
                .totalAmount(0.0)
                .build();
        OrderItem item3 = OrderItem.builder().order(order2).product(coffeeMaker).quantity(1).price(coffeeMaker.getPrice()).build();
        OrderItem item4 = OrderItem.builder().order(order2).product(airFryer).quantity(2).price(airFryer.getPrice()).build();
        order2.addOrderItem(item3);
        order2.addOrderItem(item4);
        order2.setTotalAmount(item3.getPrice() * item3.getQuantity() + item4.getPrice() * item4.getQuantity());

        // Order 3: Charlie, today
        Order order3 = Order.builder()
                .customerName("Charlie Brown")
                .paymentMethod("CREDIT_CARD")
                .status(OrderStatus.PROCESSED)
                .orderDate(now)
                .totalAmount(0.0)
                .build();
        OrderItem item5 = OrderItem.builder().order(order3).product(phone).quantity(2).price(phone.getPrice()).build();
        OrderItem item6 = OrderItem.builder().order(order3).product(deskLamp).quantity(3).price(deskLamp.getPrice()).build();
        order3.addOrderItem(item5);
        order3.addOrderItem(item6);
        order3.setTotalAmount(item5.getPrice() * item5.getQuantity() + item6.getPrice() * item6.getQuantity());

        // Save historical orders
        orderRepository.saveAll(Arrays.asList(order1, order2, order3));
        log.info("Seeded 3 historical orders");

        // 3. Seed Redis Leaderboard (to match the seeded transactions)
        log.info("Pre-populating Redis sales leaderboard based on seeded historical orders...");
        productService.recordProductSales(laptop.getId(), 1);
        productService.recordProductSales(headphones.getId(), 2);
        productService.recordProductSales(coffeeMaker.getId(), 1);
        productService.recordProductSales(airFryer.getId(), 2);
        productService.recordProductSales(phone.getId(), 2);
        productService.recordProductSales(deskLamp.getId(), 3);
        
        log.info("Data initialization completed successfully!");
    }
}
