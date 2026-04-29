package com.secufusion.events.service;

import com.secufusion.events.entity.Tenant;
import com.secufusion.events.exception.ResourceNotFoundException;
import com.secufusion.events.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Thin caching wrapper around TenantRepository.
 *
 * Tenant data is essentially static for the lifetime of a request — it never changes
 * between two calls in the same session. Caching avoids a redundant SELECT on every
 * incident operation.
 *
 * Cache: "tenants"  key: tenantId  TTL: configured in application.properties (default 10 min)
 */
@Service
@RequiredArgsConstructor
public class TenantCacheService {

    private final TenantRepository tenantRepository;

    @Cacheable(value = "tenants", key = "#tenantId")
    public Tenant findByTenantId(String tenantId) {
        return tenantRepository.findByTenantID(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));
    }
}
