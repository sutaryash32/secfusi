package com.secufusion.iam.openFeatureService.config;

import com.secufusion.iam.openFeatureService.entity.TenantApiMappingEntity;
import com.secufusion.iam.openFeatureService.repository.ApiFlagRepository;
import com.secufusion.iam.openFeatureService.repository.TenantApiMappingRepository;
import com.secufusion.iam.util.JwtUtl;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class FeatureFlagFilter extends OncePerRequestFilter {

    @Autowired
    private ApiFlagRepository apiFlagRepository;
    @Autowired
    private TenantApiMappingRepository tenantMappingRepository;

    // Cache: tenantId -> path -> enabled
    private volatile Map<String, Map<String, Boolean>> tenantPathEnabledCache = new ConcurrentHashMap<>();

    @Autowired
    private JwtUtl jwtUtl;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String requestUri = request.getRequestURI();

        // exclude filter for APIs not present in ApiFlag (treat as open/permitAll)
        var apiOpt = apiFlagRepository.findByPath(requestUri);
        if (apiOpt == null || apiOpt.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        String tenantId = null;
        var tenant = jwtUtl.getTenantFromRequest(request);
        if (tenant != null) {
            tenantId = tenant.getTenantID();
        }
        if (tenantId != null) {
            Map<String, Boolean> tenantPaths = tenantPathEnabledCache.get(tenantId);
            if (tenantPaths != null && Boolean.FALSE.equals(tenantPaths.get(requestUri))) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND,
                        "API disabled for tenant: " + tenantId);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    public void refreshCache() {
        Map<String, Map<String, Boolean>> newCache = new ConcurrentHashMap<>();

        for (TenantApiMappingEntity mapping : tenantMappingRepository.findAll()) {
            String path = mapping.getApi().getPath();
            newCache.computeIfAbsent(mapping.getTenantId(), k -> new ConcurrentHashMap<>())
                    .put(path, mapping.isEnabled());
        }

        this.tenantPathEnabledCache = newCache;
    }
}

