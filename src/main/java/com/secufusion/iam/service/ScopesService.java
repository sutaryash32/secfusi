package com.secufusion.iam.service;

import com.secufusion.iam.entity.Scopes;
import com.secufusion.iam.entity.Tenant;
import com.secufusion.iam.repository.ScopesRepository;
import com.secufusion.iam.util.JwtUtl;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class ScopesService {

    @Autowired
    private ScopesRepository scopesRepository;

    @Autowired
    private JwtUtl jwtUtl;


    /**
     * Retrieve the list of scopes applicable for the tenant extracted from the provided HTTP request.
     * <p>
     * Behavior:
     * - If the tenant type is {@code "master mssp"} (case-insensitive), all scopes are returned.
     * - If the tenant type is {@code "mssp"} (case-insensitive), scopes for user types {@code "MSSP"} and {@code "ENTERPRISE"} are returned.
     * - If the tenant type is {@code "enterprise"} (case-insensitive), scopes for user type {@code "ENTERPRISE"} are returned.
     * - For unknown or missing tenant type, an empty list is returned.
     * <p>
     * The method logs entry, branch decisions and exit including timings and any errors encountered.
     *
     * @param request the HTTP servlet request from which tenant information will be extracted via {@link JwtUtl}
     * @return a list of matching {@link Scopes}, or an empty list if none are found or on error
     */
    public List<Scopes> getAllScopes(HttpServletRequest request) {
        long startTime = System.currentTimeMillis();
        Tenant tenantFromRequest = jwtUtl.getTenantFromRequest(request);
        String tenantType = tenantFromRequest != null && tenantFromRequest.getTenantType() != null
                ? tenantFromRequest.getTenantType().trim()
                : "";

        log.debug("Entering getAllScopes - tenantFromRequest={}, tenantType='{}'", tenantFromRequest, tenantType);

        try {
            List<Scopes> scopesToAssign;
            String normalized = tenantType.toLowerCase();

            switch (normalized) {
                case "master mssp":
                    log.info("Assigning ALL scopes to Master MSSP...");
                    scopesToAssign = scopesRepository.findAll();
                    break;

                case "mssp":
                    log.info("Assigning MSSP + Enterprise scopes to MSSP...");
                    scopesToAssign = scopesRepository.findByUserTypeIn(List.of("MSSP", "ENTERPRISE"));
                    break;

                case "enterprise":
                    log.info("Assigning Enterprise scopes to Enterprise...");
                    scopesToAssign = scopesRepository.findByUserType("ENTERPRISE");
                    break;

                default:
                    log.warn("Unknown tenant type '{}' – no scopes assigned", tenantType);
                    log.debug("Exiting getAllScopes - returning empty list for tenantType='{}' (elapsed={}ms)", tenantType, System.currentTimeMillis() - startTime);
                    return List.of();
            }

            if (scopesToAssign == null || scopesToAssign.isEmpty()) {
                log.warn("No scopes found for tenant type '{}' → returning empty list", tenantType);
                log.debug("Exiting getAllScopes - returning empty list for tenantType='{}' (elapsed={}ms)", tenantType, System.currentTimeMillis() - startTime);
                return List.of();
            }

            log.info("Found {} scopes for tenantType='{}'", scopesToAssign.size(), tenantType);
            log.debug("Exiting getAllScopes - success (elapsed={}ms)", System.currentTimeMillis() - startTime);
            return scopesToAssign;
        } catch (Exception ex) {
            log.error("Error retrieving scopes for tenant type '{}': {}", tenantType, ex.getMessage(), ex);
            log.debug("Exiting getAllScopes - error (elapsed={}ms)", System.currentTimeMillis() - startTime);
            return List.of();
        }
    }
}