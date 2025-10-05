package com.cache_test.cache_test.repo;

import com.cache_test.cache_test.entity.Users;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<Users, Long> {

    Optional<Users> findByEmail(String email);

    @Query("SELECT u FROM Users u WHERE u.age > :age")
    List<Users> findUsersOlderThan(@Param("age") Integer age);

    @Query("SELECT u FROM Users u JOIN FETCH u.products")
    List<Users> findAllWithProducts();
}