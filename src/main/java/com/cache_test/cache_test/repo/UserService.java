package com.cache_test.cache_test.repo;

import com.cache_test.cache_test.entity.Users;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;

    @Cacheable(value = "users", key = "#id")
    public Optional<Users> findById(Long id) {

        log.info("Fetching Users with id: {}", id);
        return userRepository.findById(id);
    }

    @Cacheable(value = "users", key = "#email")
    public Optional<Users> findByEmail(String email) {
        log.info("Fetching Users with email: {}", email);
        return userRepository.findByEmail(email);
    }

    @CachePut(value = "users", key = "#result.id")
    @Transactional
    public Users save(Users users) {
        log.info("Saving Users: {}", users.getName());
        return userRepository.save(users);
    }

    @CacheEvict(value = "users", key = "#id")
    @Transactional
    public void deleteById(Long id) {
        log.info("Deleting Users with id: {}", id);
        userRepository.deleteById(id);
    }

    @Cacheable(value = "users", key = "'older_than_' + #age")
    public List<Users> findUsersOlderThan(Integer age) {
        log.info("Fetching Users older than: {}", age);
        return userRepository.findUsersOlderThan(age);
    }

    public List<Users> findAllWithProducts() {
        log.info("Fetching all Users with products");
        return userRepository.findAllWithProducts();
    }
}
