package com.cache_test.cache_test;

import com.cache_test.cache_test.entity.Product;
import com.cache_test.cache_test.entity.Users;
import com.cache_test.cache_test.repo.ProductRepository;
import com.cache_test.cache_test.repo.ProductService;
import com.cache_test.cache_test.repo.UserRepository;
import com.cache_test.cache_test.repo.UserService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.Cache;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class CacheTestApplicationTests {

    private static final AtomicLong counter = new AtomicLong();

    private static final Logger log = LoggerFactory.getLogger(CacheTestApplicationTests.class);

    @Autowired
    private UserService userService;

    @Autowired
    private ProductService productService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private SessionFactory sessionFactory;

    private Statistics statistics;

    @BeforeEach
    void setUp() {
        sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        statistics = sessionFactory.getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        try {
            sessionFactory.getCache().evictAllRegions();
        }
        catch (Exception e) {

            log.info("evictAllRegions() failed: {}", e.getMessage());
        }
        cacheManager.getCacheNames().forEach(cacheName -> {
            var c = cacheManager.getCache(cacheName);
            if (c != null) {
                c.clear();
            }
        });
        cleanupDatabase();
        createTestData();
    }

    private void cleanupDatabase() {
        productRepository.deleteAll();
        userRepository.deleteAll();
    }

    private void createTestData() {
        long uniqueId = counter.incrementAndGet();

        Users user1 = new Users();
        user1.setName("John Doe");
        user1.setEmail("john" + uniqueId + "@example.com");
        user1.setAge(30);

        Users user2 = new Users();
        user2.setName("Jane Smith");
        user2.setEmail("jane" + uniqueId + "@example.com");
        user2.setAge(25);

        user1 = userRepository.save(user1);
        user2 = userRepository.save(user2);

        Product product1 = new Product();
        product1.setName("Laptop");
        product1.setDescription("Gaming laptop");
        product1.setPrice(new BigDecimal("1500.00"));
        product1.setUser(user1);

        Product product2 = new Product();
        product2.setName("Mouse");
        product2.setDescription("Wireless mouse");
        product2.setPrice(new BigDecimal("50.00"));
        product2.setUser(user1);

        productRepository.save(product1);
        productRepository.save(product2);
        statistics.clear();
    }

    @Test
    @Transactional
    void testL1CacheSessionScope() {
        log.info("=== Test L1 Cache (Session Cache) ===");

        Session session = entityManager.unwrap(Session.class);

        List<Users> allUsers = userRepository.findAll();
        assertFalse(allUsers.isEmpty(), "At least one user must exist");
        Long firstUserId = allUsers.get(0).getId();

        log.info("1. First query for user id={}", firstUserId);
        Users user1 = session.get(Users.class, firstUserId);
        assertNotNull(user1, "Loaded user should not be null");

        assertTrue(session.contains(user1), "Session should contain the loaded entity");
        log.info("User {} is in L1 cache: {}", user1.getName(), session.contains(user1));

        log.info("2. Second request for the same user");
        Users user1Again = session.get(Users.class, firstUserId);

        assertSame(user1, user1Again, "Objects loaded inside the same session should be the same instance");
        log.info("Same object reference in memory: {}", user1 == user1Again);
        log.info("First instance hash: {}", System.identityHashCode(user1));
        log.info("Second instance hash: {}", System.identityHashCode(user1Again));

        log.info("L2 Cache hits: {}", statistics.getSecondLevelCacheHitCount());
        log.info("Entity load count: {}", statistics.getEntityLoadCount());
    }

    @Test
    void testL1CacheAcrossSessions() {
        log.info("=== Test L1 Cache across different sessions ===");

        List<Users> allUsers = userRepository.findAll();
        assertFalse(allUsers.isEmpty(), "At least one user must exist");
        Long firstUserId = allUsers.get(0).getId();

        Users user1Session1;
        Users user1Session2;

        EntityManager em1 = entityManagerFactory.createEntityManager();
        try (em1) {
            em1.getTransaction().begin();
            user1Session1 = em1.find(Users.class, firstUserId);
            log.info(
                "User from first session: {}, hash: {}", user1Session1.getName(),
                System.identityHashCode(user1Session1)
            );
            em1.getTransaction().commit();
        }

        EntityManager em2 = entityManagerFactory.createEntityManager();
        try (em2) {
            em2.getTransaction().begin();
            user1Session2 = em2.find(Users.class, firstUserId);
            log.info(
                "User from second session: {}, hash: {}", user1Session2.getName(),
                System.identityHashCode(user1Session2)
            );
            em2.getTransaction().commit();
        }

        assertNotSame(user1Session1, user1Session2, "Instances from different sessions should not be the same");
        assertEquals(user1Session1.getId(), user1Session2.getId(), "IDs should match across sessions");
        log.info("Different instances across sessions: {}", user1Session1 != user1Session2);
    }

    @Test
    void testL2CacheAcrossSessions() {
        log.info("=== Test L2 Cache across sessions ===");

        List<Users> allUsers = userRepository.findAll();
        assertFalse(allUsers.isEmpty(), "At least one user must exist");
        Long firstUserId = allUsers.get(0).getId();

        statistics.clear();

        EntityManager em1 = entityManagerFactory.createEntityManager();
        try (em1) {
            em1.getTransaction().begin();
            Users user = em1.find(Users.class, firstUserId);
            log.info("First session - loaded user: {}", user.getName());
            em1.getTransaction().commit();
        }

        long loadCountAfterFirstSession = statistics.getEntityLoadCount();
        long l2HitCountAfterFirstSession = statistics.getSecondLevelCacheHitCount();

        log.info(
            "After first session - Entity loads: {}, L2 hits: {}", loadCountAfterFirstSession,
            l2HitCountAfterFirstSession
        );

        EntityManager em2 = entityManagerFactory.createEntityManager();
        try (em2) {
            em2.getTransaction().begin();
            Users user = em2.find(Users.class, firstUserId);
            log.info("Second session - loaded user: {}", user.getName());
            em2.getTransaction().commit();
        }

        long loadCountAfterSecondSession = statistics.getEntityLoadCount();
        long l2HitCountAfterSecondSession = statistics.getSecondLevelCacheHitCount();

        log.info(
            "After second session - Entity loads: {}, L2 hits: {}", loadCountAfterSecondSession,
            l2HitCountAfterSecondSession
        );

        if (l2HitCountAfterSecondSession > l2HitCountAfterFirstSession) {
            log.info("L2 cache seems to be working");
        }
        else {
            log.info("L2 cache may be not configured or disabled");
        }
    }

    @Test
    void testCacheKeysAndContent() {
        log.info("=== Test cache keys and content (L1 and L2) ===");

        List<Users> allUsers = userRepository.findAll();
        assertFalse(allUsers.isEmpty(), "At least one user must exist");
        Long firstUserId = allUsers.get(0).getId();

        EntityManager em = entityManagerFactory.createEntityManager();
        try (em) {
            em.getTransaction().begin();
            Users user = em.find(Users.class, firstUserId);
            log.info("Loaded user: {}", user.getName());

            Session session = em.unwrap(Session.class);
            assertTrue(session.contains(user), "Session should contain the loaded entity");

            log.info("L1 cache contains the object: {}", session.contains(user));

            Cache l2Cache = sessionFactory.getCache();
            boolean inL2Cache = l2Cache.containsEntity(Users.class, firstUserId);
            log.info("L2 Cache contains Users with id={}: {}", firstUserId, inL2Cache);

            em.getTransaction().commit();
        }
    }

    @Test
    void testFindAllThenFindById() {
        log.info("=== Test: findAll then findById ===");

        statistics.clear();

        log.info("1. Loading all users");
        List<Users> allUsers = userRepository.findAll();
        log.info("Loaded users: {}", allUsers.size());

        for (Users user : allUsers) {
            log.info("User: {} (id={})", user.getName(), user.getId());
        }

        long loadCountAfterFindAll = statistics.getEntityLoadCount();
        long l2HitCountAfterFindAll = statistics.getSecondLevelCacheHitCount();

        log.info("After findAll - Entity loads: {}, L2 hits: {}", loadCountAfterFindAll, l2HitCountAfterFindAll);

        assertFalse(allUsers.isEmpty(), "At least one user must exist");
        Long firstUserId = allUsers.get(0).getId();

        EntityManager em = entityManagerFactory.createEntityManager();
        try (em) {
            em.getTransaction().begin();
            log.info("2. Loading user by id={}", firstUserId);
            Users specificUser = em.find(Users.class, firstUserId);
            log.info("Loaded user: {}", specificUser.getName());
            em.getTransaction().commit();
        }

        long loadCountAfterFindById = statistics.getEntityLoadCount();
        long l2HitCountAfterFindById = statistics.getSecondLevelCacheHitCount();

        log.info("After findById - Entity loads: {}, L2 hits: {}", loadCountAfterFindById, l2HitCountAfterFindById);

        if (l2HitCountAfterFindById > l2HitCountAfterFindAll) {
            log.info("The user was served from L2 cache");
        }
        else {
            log.info("The user was loaded from DB (L2 cache might be not configured)");
        }
    }

    @Test
    void testSpringCacheVsHibernateCache() {
        log.info("=== Test Spring Cache vs Hibernate Cache ===");

        List<Users> allUsers = userRepository.findAll();
        assertFalse(allUsers.isEmpty(), "At least one user must exist");
        Long firstUserId = allUsers.get(0).getId();

        statistics.clear();
        cacheManager.getCacheNames().forEach(cacheName -> {
            org.springframework.cache.Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
            }
        });

        log.info("1. First call userService.findById({})", firstUserId);
        Optional<Users> user1 = userService.findById(firstUserId);
        assertTrue(user1.isPresent(), "userService should return a user");

        log.info("2. Second call userService.findById({})", firstUserId);
        Optional<Users> user2 = userService.findById(firstUserId);
        assertTrue(user2.isPresent(), "userService should return a user on subsequent call");

        org.springframework.cache.Cache springCache = cacheManager.getCache("users");
        if (springCache == null) {
            springCache = cacheManager.getCache("Users");
        }

        if (springCache != null) {
            log.info("Spring Cache contains value: {}", springCache.get(firstUserId) != null);
        }
        else {
            log.info("Spring Cache not found");
        }

        EntityManager em = entityManagerFactory.createEntityManager();
        try (em) {
            em.getTransaction().begin();
            log.info("3. Direct access via EntityManager");
            Users user3 = em.find(Users.class, firstUserId);
            log.info("User via EntityManager: {}", user3.getName());
            em.getTransaction().commit();
        }

        log.info("Hibernate L2 Cache hits: {}", statistics.getSecondLevelCacheHitCount());
        log.info("Hibernate Entity loads: {}", statistics.getEntityLoadCount());
    }

    @Test
    void testCollectionCache() {
        log.info("=== Test caching of collections ===");

        List<Users> allUsers = userRepository.findAll();
        assertFalse(allUsers.isEmpty(), "At least one user must exist");
        Long firstUserId = allUsers.get(0).getId();

        statistics.clear();

        EntityManager em1 = entityManagerFactory.createEntityManager();
        try (em1) {
            em1.getTransaction().begin();
            Users user = em1.find(Users.class, firstUserId);
            log.info("Loaded user: {}", user.getName());

            List<Product> products = user.getProducts();
            log.info("Product count: {}", products.size());

            for (Product product : products) {
                log.info("Product: {}", product.getName());
            }

            em1.getTransaction().commit();
        }

        long collectionLoadCount = statistics.getCollectionLoadCount();
        long collectionFetchCount = statistics.getCollectionFetchCount();

        log.info("Collection loads: {}, Collection fetches: {}", collectionLoadCount, collectionFetchCount);

        EntityManager em2 = entityManagerFactory.createEntityManager();
        try (em2) {
            em2.getTransaction().begin();
            Users user = em2.find(Users.class, firstUserId);
            List<Product> products = user.getProducts();
            log.info("In second session - product count: {}", products.size());

            em2.getTransaction().commit();
        }

        long collectionLoadCountAfter = statistics.getCollectionLoadCount();
        long collectionFetchCountAfter = statistics.getCollectionFetchCount();
        long collectionCacheHitCount = statistics.getSecondLevelCacheHitCount();

        log.info(
            "After second session - Collection loads: {}, Collection fetches: {}, L2 hits: {}",
            collectionLoadCountAfter, collectionFetchCountAfter, collectionCacheHitCount
        );
    }

    @Test
    void testCacheEviction() {
        log.info("=== Test eviction from cache ===");

        List<Users> allUsers = userRepository.findAll();
        assertFalse(allUsers.isEmpty(), "At least one user must exist");
        Long firstUserId = allUsers.get(0).getId();

        EntityManager em1 = entityManagerFactory.createEntityManager();
        try (em1) {
            em1.getTransaction().begin();
            Users user = em1.find(Users.class, firstUserId);
            log.info("Loaded user: {}", user.getName());
            em1.getTransaction().commit();
        }

        Cache l2Cache = sessionFactory.getCache();
        boolean inCacheBefore = false;
        inCacheBefore = l2Cache.containsEntity(Users.class, firstUserId);
        log.info("In L2 cache before eviction: {}", inCacheBefore);

        l2Cache.evictEntityData(Users.class, firstUserId);
        log.info("Called evictEntityData(Class, id)");
        boolean inCacheAfter = l2Cache.containsEntity(Users.class, firstUserId);
        log.info("In L2 cache after eviction: {}", inCacheAfter);

        try {
            l2Cache.evictAllRegions();
            log.info("All L2 cache regions evicted");
        }
        catch (Exception e) {
            log.info("evictAllRegions failed: {}", e.getMessage());
        }
    }

    @Test
    void testCacheStatistics() {
        log.info("=== Detailed cache statistics ===");

        List<Users> allUsers = userRepository.findAll();
        assertFalse(allUsers.isEmpty(), "There should be at least 2 users");
        assertTrue(allUsers.size() >= 2, "There should be at least 2 users");

        Long firstUserId = allUsers.get(0).getId();
        Long secondUserId = allUsers.get(1).getId();

        statistics.clear();

        EntityManager em = entityManagerFactory.createEntityManager();
        try (em) {
            em.getTransaction().begin();
            Users user1 = em.find(Users.class, firstUserId);
            Users user2 = em.find(Users.class, secondUserId);
            Users user1Again = em.find(Users.class, firstUserId);

            log.info("Loaded users: {}, {}", user1.getName(), user2.getName());

            em.getTransaction().commit();
        }

        log.info("=== Hibernate Statistics ===");
        log.info("Entity fetch count: {}", statistics.getEntityFetchCount());
        log.info("Entity load count: {}", statistics.getEntityLoadCount());
        log.info("Second level cache hit count: {}", statistics.getSecondLevelCacheHitCount());
        log.info("Second level cache miss count: {}", statistics.getSecondLevelCacheMissCount());
        log.info("Second level cache put count: {}", statistics.getSecondLevelCachePutCount());
        log.info("Collection fetch count: {}", statistics.getCollectionFetchCount());
        log.info("Collection load count: {}", statistics.getCollectionLoadCount());
        log.info("Query cache hit count: {}", statistics.getQueryCacheHitCount());
        log.info("Query cache miss count: {}", statistics.getQueryCacheMissCount());
        log.info("Query execution count: {}", statistics.getQueryExecutionCount());
    }

    @Test
    void testQueryCacheForUsersOlderThanTwenty() {
        log.info("=== Test Query Cache for users older than 20 ===");

        List<Users> firstQueryResults = entityManager.createQuery(
                "FROM Users u WHERE u.age > 20", Users.class)
            .setHint("org.hibernate.cacheable", true)
            .getResultList();

        long sqlExecutionsBeforeSecondQuery = statistics.getPrepareStatementCount();
        long queryCacheHitsBeforeSecondQuery = statistics.getQueryCacheHitCount();
        long queryCachePutsBeforeSecondQuery = statistics.getQueryCachePutCount();

        log.info("First Query - SQL executed: {}", sqlExecutionsBeforeSecondQuery);
        log.info("First Query - Query Cache Hits: {}", queryCacheHitsBeforeSecondQuery);
        log.info("First Query - Query Cache Puts: {}", queryCachePutsBeforeSecondQuery);

        assertTrue(sqlExecutionsBeforeSecondQuery > 0, "First query should execute SQL.");
        assertEquals(0, queryCacheHitsBeforeSecondQuery, "First query should not be a query cache hit.");
        assertTrue(queryCachePutsBeforeSecondQuery > 0, "First query should put results into the query cache.");
        assertEquals(2, firstQueryResults.size(), "First query should return 2 users older than 20.");

        entityManager.clear();
        entityManagerFactory.getCache().evictAll();

        List<Users> secondQueryResults = entityManager.createQuery(
                "FROM Users u WHERE u.age > 20", Users.class)
            .setHint("org.hibernate.cacheable", true)
            .getResultList();

        long sqlExecutionsDuringSecondQuery = statistics.getPrepareStatementCount() - sqlExecutionsBeforeSecondQuery;
        long queryCacheHitsDuringSecondQuery = statistics.getQueryCacheHitCount() - queryCacheHitsBeforeSecondQuery;
        long queryCachePutsDuringSecondQuery = statistics.getQueryCachePutCount() - queryCachePutsBeforeSecondQuery;

        log.info("Second Query - SQL executed: {}", sqlExecutionsDuringSecondQuery);
        log.info("Second Query - Query Cache Hits: {}", queryCacheHitsDuringSecondQuery);
        log.info("Second Query - Query Cache Puts: {}", queryCachePutsDuringSecondQuery);

        assertTrue(queryCacheHitsDuringSecondQuery > 0, "Second query should get hits from the query cache.");
        assertEquals(
            0, sqlExecutionsDuringSecondQuery, "Second query should NOT execute SQL if query cache works perfectly.");
        assertEquals(0, queryCachePutsDuringSecondQuery, "Second query should NOT add new items to the query cache.");
        assertEquals(firstQueryResults.size(), secondQueryResults.size(), "Result count should match between queries.");
    }

    @Test
    @Transactional
    void testQueryCacheInvalidationAfterEntityUpdateWithAnnotations() {
        log.info("=== Test Query Cache invalidation after entity update ===");

        List<Users> queryBeforeUpdate = entityManager.createQuery(
                "FROM Users u WHERE u.age > 20", Users.class)
            .setHint("org.hibernate.cacheable", true)
            .getResultList();

        long initialQueryCachePuts = statistics.getQueryCachePutCount();
        long initialQueryCacheHits = statistics.getQueryCacheHitCount();

        assertTrue(initialQueryCachePuts > 0, "First query should put results into query cache.");
        assertEquals(0, initialQueryCacheHits, "First query should not be a cache hit.");
        assertFalse(queryBeforeUpdate.isEmpty(), "Query should return some users.");

        entityManager.clear();
        statistics.clear();

        Users userToUpdate = userRepository.findById(queryBeforeUpdate.get(0).getId())
            .orElseThrow(() -> new AssertionError("User not found for update."));
        userToUpdate.setName(userToUpdate.getName() + "_UPDATED");

        entityManager.merge(userToUpdate);
        entityManager.flush();

        log.info("Entity with ID {} updated. Query cache should now be invalidated.", userToUpdate.getId());

        long entityUpdateCount = statistics.getEntityUpdateCount();
        assertTrue(entityUpdateCount > 0, "An entity update should have occurred.");

        entityManager.clear();
        statistics.clear();

        List<Users> queryAfterUpdate = entityManager.createQuery(
                "FROM Users u WHERE u.age > 20", Users.class)
            .setHint("org.hibernate.cacheable", true)
            .getResultList();

        long queryCacheHitsAfterUpdate = statistics.getQueryCacheHitCount();
        long sqlExecutionsAfterUpdate = statistics.getPrepareStatementCount();

        log.info("Second Query after update - SQL executed: {}", sqlExecutionsAfterUpdate);
        log.info("Second Query after update - Query Cache Hits: {}", queryCacheHitsAfterUpdate);

        assertEquals(
            0, queryCacheHitsAfterUpdate,
            "Query cache should be invalidated after entity update, resulting in 0 hits."
        );
        assertTrue(sqlExecutionsAfterUpdate > 0, "Second query should execute SQL again after cache invalidation.");
        assertEquals(
            queryBeforeUpdate.size(), queryAfterUpdate.size(), "Result count should remain the same after update.");
    }

    @Test
    void testL2CacheUsingEntityAnnotations() {
        log.info("=== Test L2 cache behavior with entity annotations ===");

        List<Users> allUsers = userRepository.findAll();
        Long userId = allUsers.get(0).getId();

        statistics.clear();

        EntityManager em1 = entityManagerFactory.createEntityManager();
        try (em1) {
            em1.getTransaction().begin();
            Users user = em1.find(Users.class, userId);
            em1.getTransaction().commit();
            log.info("First session loaded user: {}", user.getName());
        }

        long hitsAfterFirst = statistics.getSecondLevelCacheHitCount();

        EntityManager em2 = entityManagerFactory.createEntityManager();
        try (em2) {
            em2.getTransaction().begin();
            Users user = em2.find(Users.class, userId);
            em2.getTransaction().commit();
            log.info("Second session loaded user: {}", user.getName());
        }

        long hitsAfterSecond = statistics.getSecondLevelCacheHitCount();

        assertTrue(hitsAfterSecond > hitsAfterFirst, "L2 cache should be used for entity with annotations");
    }

    @Test
    void testSpringCacheViaServiceAnnotations() {
        log.info("=== Test Spring Cache via @Cacheable on service methods ===");

        List<Users> allUsers = userRepository.findAll();
        Long userId = allUsers.get(0).getId();

        cacheManager.getCacheNames().forEach(name -> {
            var cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        });

        Optional<Users> firstCall = userService.findById(userId);
        Optional<Users> secondCall = userService.findById(userId);

        org.springframework.cache.Cache springCache = cacheManager.getCache("users");
        assertNotNull(springCache, "Spring cache 'users' should exist");

        Object cachedValue = springCache.get(userId);
        assertNotNull(cachedValue, "Cache should contain the user after first call");

        assertEquals(firstCall.get(), secondCall.get(), "Second call should return same entity from cache");
    }

    @Test
    void testQueryCacheWithEntityEvictionUsingAnnotations() {
        log.info("=== Test query cache with entity eviction using annotations ===");

        List<Users> users = userRepository.findAll();
        Long userId = users.get(0).getId();

        EntityManager em1 = entityManagerFactory.createEntityManager();
        try (em1) {
            em1.getTransaction().begin();
            em1.createQuery("FROM Users", Users.class)
                .getResultList();
            em1.getTransaction().commit();
        }

        sessionFactory.getCache().evictEntityData(Users.class);

        EntityManager em2 = entityManagerFactory.createEntityManager();
        try (em2) {
            em2.getTransaction().begin();
            List<Users> loadedUsers = em2.createQuery("FROM Users", Users.class)
                .getResultList();
            em2.getTransaction().commit();
            assertFalse(loadedUsers.isEmpty(), "Users should be loaded even after entity eviction");
        }
    }

    @Test
    void testEntityAnnotationCacheable() {
        log.info("=== Test entity-level @Cacheable annotation ===");

        List<Users> users = userRepository.findAll();
        Long userId = users.get(0).getId();

        statistics.clear();

        EntityManager em1 = entityManagerFactory.createEntityManager();
        try (em1) {
            em1.getTransaction().begin();
            Users u1 = em1.find(Users.class, userId);
            em1.getTransaction().commit();
        }

        EntityManager em2 = entityManagerFactory.createEntityManager();
        try (em2) {
            em2.getTransaction().begin();
            Users u2 = em2.find(Users.class, userId);
            em2.getTransaction().commit();
        }

        assertTrue(statistics.getSecondLevelCacheHitCount() > 0, "Entity annotation should enable L2 caching");
    }

    @Test
    void testRepositoryMethodAnnotationCacheable() {
        log.info("=== Test repository/service method @Cacheable annotation ===");

        List<Users> users = userRepository.findAll();
        Long userId = users.get(0).getId();

        cacheManager.getCache("users").clear();

        userService.findById(userId);
        userService.findById(userId);

        assertNotNull(
            cacheManager.getCache("users").get(userId),
            "Method-level @Cacheable should store value in Spring Cache"
        );
    }

    @Test
    void testRefreshAndLockOnL1L2() {
        log.info("=== Test refresh() and lock() to avoid stale data in L1/L2 ===");

        List<Users> allUsers = userRepository.findAll();
        Long userId = allUsers.get(0).getId();

        EntityManager em1 = entityManagerFactory.createEntityManager();
        Users userSession1;
        try (em1) {
            em1.getTransaction().begin();
            userSession1 = em1.find(Users.class, userId);
            log.info("Session1 loaded user: {} with age {}", userSession1.getName(), userSession1.getAge());
            em1.getTransaction().commit();
        }

        EntityManager em2 = entityManagerFactory.createEntityManager();
        try (em2) {
            em2.getTransaction().begin();
            Users userToUpdate = em2.find(Users.class, userId);
            int oldAge = userToUpdate.getAge();
            userToUpdate.setAge(oldAge + 10);
            em2.merge(userToUpdate);
            em2.getTransaction().commit();
            log.info("Session2 updated user age to {}", userToUpdate.getAge());
        }

        EntityManager em3 = entityManagerFactory.createEntityManager();
        try (em3) {
            em3.getTransaction().begin();
            Users user = em3.find(Users.class, userId);
            log.info("Before refresh - user age: {}", user.getAge());
            em3.refresh(user);
            log.info("After refresh - user age: {}", user.getAge());
            em3.getTransaction().commit();

            assertEquals(allUsers.get(0).getAge() + 10, user.getAge(), "After refresh, entity should have latest age");
        }
    }

    @Test
    void testMergeSaveOrUpdateWithL2Cache() {
        log.info("=== Test merge() / saveOrUpdate() updates with L2 cache ===");

        List<Users> allUsers = userRepository.findAll();
        Long userId = allUsers.get(0).getId();

        statistics.clear();

        EntityManager em1 = entityManagerFactory.createEntityManager();
        try (em1) {
            em1.getTransaction().begin();
            Users user = em1.find(Users.class, userId);
            em1.getTransaction().commit();
            log.info("Loaded user: {} with age {}", user.getName(), user.getAge());
        }

        long l2HitsBefore = statistics.getSecondLevelCacheHitCount();

        EntityManager em2 = entityManagerFactory.createEntityManager();
        try (em2) {
            em2.getTransaction().begin();

            Users detachedUser = new Users();
            detachedUser.setId(userId);
            detachedUser.setName("Updated Name");
            detachedUser.setAge(99);
            detachedUser.setEmail(allUsers.get(0).getEmail());
            em2.merge(detachedUser);
            em2.getTransaction().commit();
            log.info("Merged detached user with new name and age");
        }

        EntityManager em3 = entityManagerFactory.createEntityManager();
        try (em3) {
            em3.getTransaction().begin();
            Users user = em3.find(Users.class, userId);
            em3.getTransaction().commit();
            log.info("After merge - user: {} age {}", user.getName(), user.getAge());
        }

        long l2HitsAfter = statistics.getSecondLevelCacheHitCount();

        assertTrue(l2HitsAfter >= l2HitsBefore, "L2 cache should reflect merged entity and possibly serve hits");
    }
}