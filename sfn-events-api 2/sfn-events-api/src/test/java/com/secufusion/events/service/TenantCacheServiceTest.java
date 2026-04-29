package com.secufusion.events.service;

import com.secufusion.events.entity.Tenant;
import com.secufusion.events.exception.ResourceNotFoundException;
import com.secufusion.events.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TenantCacheServiceCacheTest.TestConfig.class)
@DisplayName("TenantCacheService — Cache Behaviour")
class TenantCacheServiceCacheTest {

    @Configuration
    @EnableCaching
    static class TestConfig {

        // ✅ Declare mock directly as a @Bean — no @MockBean needed
        @Bean
        TenantRepository tenantRepository() {
            return mock(TenantRepository.class);
        }

        @Bean
        TenantCacheService tenantCacheService(TenantRepository tenantRepository) {
            return new TenantCacheService(tenantRepository);
        }

        @Bean
        CacheManager cacheManager() {
            return new CaffeineCacheManager("tenants");
        }
    }

    @Autowired
    private TenantCacheService tenantCacheService;

    @Autowired
    private TenantRepository tenantRepository; // ✅ injected from TestConfig

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        // ✅ Reset mock AND clear cache before each test
        reset(tenantRepository);
        cacheManager.getCache("tenants").clear();
    }

    @Test
    @DisplayName("Cache — second call does not hit repository")
    void cache_secondCall_doesNotHitRepository() {
        Tenant tenant = new Tenant();
        tenant.setTenantID("tenant-001");
        when(tenantRepository.findByTenantID("tenant-001"))
                .thenReturn(Optional.of(tenant));

        tenantCacheService.findByTenantId("tenant-001"); // miss — hits DB
        tenantCacheService.findByTenantId("tenant-001"); // hit  — from cache

        verify(tenantRepository, times(1)).findByTenantID("tenant-001");
    }

    @Test
    @DisplayName("Cache — different tenantIds each hit repository exactly once")
    void cache_differentTenantIds_eachHitRepositoryOnce() {
        Tenant t1 = new Tenant(); t1.setTenantID("tenant-001");
        Tenant t2 = new Tenant(); t2.setTenantID("tenant-002");
        when(tenantRepository.findByTenantID("tenant-001")).thenReturn(Optional.of(t1));
        when(tenantRepository.findByTenantID("tenant-002")).thenReturn(Optional.of(t2));

        tenantCacheService.findByTenantId("tenant-001");
        tenantCacheService.findByTenantId("tenant-001");
        tenantCacheService.findByTenantId("tenant-002");
        tenantCacheService.findByTenantId("tenant-002");

        verify(tenantRepository, times(1)).findByTenantID("tenant-001");
        verify(tenantRepository, times(1)).findByTenantID("tenant-002");
    }

    @Test
    @DisplayName("Cache — cleared cache causes repository to be hit again")
    void cache_afterCacheCleared_repositoryCalledAgain() {
        Tenant tenant = new Tenant();
        tenant.setTenantID("tenant-001");
        when(tenantRepository.findByTenantID("tenant-001"))
                .thenReturn(Optional.of(tenant));

        tenantCacheService.findByTenantId("tenant-001"); // miss — cached
        cacheManager.getCache("tenants").clear();         // evict
        tenantCacheService.findByTenantId("tenant-001"); // miss again

        verify(tenantRepository, times(2)).findByTenantID("tenant-001");
    }

    @Test
    @DisplayName("Cache — exception not cached; repository retried on next call")
    void cache_exceptionNotCached_repositoryRetriedOnNextCall() {
        Tenant tenant = new Tenant();
        tenant.setTenantID("tenant-001");
        when(tenantRepository.findByTenantID("tenant-001"))
                .thenReturn(Optional.empty())       // first call → throws
                .thenReturn(Optional.of(tenant));   // second call → succeeds

        assertThrows(ResourceNotFoundException.class,
                () -> tenantCacheService.findByTenantId("tenant-001"));

        Tenant result = tenantCacheService.findByTenantId("tenant-001");
        assertNotNull(result);

        verify(tenantRepository, times(2)).findByTenantID("tenant-001");
    }
}