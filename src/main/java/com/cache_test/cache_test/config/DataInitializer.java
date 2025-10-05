package com.cache_test.cache_test.config;

import com.cache_test.cache_test.entity.Product;
import com.cache_test.cache_test.entity.Users;
import com.cache_test.cache_test.repo.ProductRepository;
import com.cache_test.cache_test.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Arrays;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    @Override
    public void run(String... args) throws Exception {
        log.info("Initializing test data...");

        Users user1 = new Users(null, "John Doe", "john@example.com", 25, null);
        Users user2 = new Users(null, "Jane Smith", "jane@example.com", 30, null);
        Users user3 = new Users(null, "Bob Johnson", "bob@example.com", 35, null);

        userRepository.saveAll(Arrays.asList(user1, user2, user3));

        Product product1 = new Product(null, "Laptop", "Gaming laptop", new BigDecimal("1500.00"), user1);
        Product product2 = new Product(null, "Mouse", "Wireless mouse", new BigDecimal("50.00"), user1);
        Product product3 = new Product(null, "Keyboard", "Mechanical keyboard", new BigDecimal("120.00"), user2);
        Product product4 = new Product(null, "Monitor", "4K monitor", new BigDecimal("400.00"), user2);
        Product product5 = new Product(null, "Headphones", "Noise-cancelling headphones", new BigDecimal("300.00"), user3);

        productRepository.saveAll(Arrays.asList(product1, product2, product3, product4, product5));

        log.info("Initializing test data done.");
    }
}