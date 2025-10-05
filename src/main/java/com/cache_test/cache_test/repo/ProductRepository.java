package com.cache_test.cache_test.repo;

import com.cache_test.cache_test.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByUserId(Long userId);

    @Query("SELECT p FROM Product p WHERE p.price > :price")
    List<Product> findProductsWithPriceGreaterThan(@Param("price") BigDecimal price);

    @Query("SELECT p FROM Product p JOIN FETCH p.user")
    List<Product> findAllWithUser();
}
