package com.cache_test.cache_test.repo;

import com.cache_test.cache_test.entity.Product;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;

    @Cacheable(value = "products", key = "#id")
    public Optional<Product> findById(Long id) {
        log.info("Fetching product with id: {}", id);
        return productRepository.findById(id);
    }

    @Cacheable(value = "products", key = "'user_' + #userId")
    public List<Product> findByUserId(Long userId) {
        log.info("Fetching products for user: {}", userId);
        return productRepository.findByUserId(userId);
    }

    @CachePut(value = "products", key = "#result.id")
    @Transactional
    public Product save(Product product) {
        log.info("Saving product: {}", product.getName());
        return productRepository.save(product);
    }

    @CacheEvict(value = "products", key = "#id")
    @Transactional
    public void deleteById(Long id) {
        log.info("Deleting product with id: {}", id);
        productRepository.deleteById(id);
    }

    @Cacheable(value = "products", key = "'price_gt_' + #price")
    public List<Product> findProductsWithPriceGreaterThan(BigDecimal price) {
        log.info("Fetching products with price greater than: {}", price);
        return productRepository.findProductsWithPriceGreaterThan(price);
    }

    public List<Product> findAllWithUser() {
        log.info("Fetching all products with user");
        return productRepository.findAllWithUser();
    }
}
