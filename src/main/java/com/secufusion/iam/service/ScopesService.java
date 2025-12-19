package com.secufusion.iam.service;

import com.secufusion.iam.dto.LoggedInUserDetailsBean;
import com.secufusion.iam.entity.Scopes;
import com.secufusion.iam.entity.Tenant;
import com.secufusion.iam.entity.TenantType;
import com.secufusion.iam.exception.ResourceNotFoundException;
import com.secufusion.iam.repository.ScopesRepository;
import com.secufusion.iam.repository.TenantTypeRepository;
import com.secufusion.iam.util.JwtUtl;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
public class ScopesService {

    @Autowired
    private ScopesRepository scopesRepository;

    @Autowired
    private JwtUtl jwtUtl;

    @Autowired
    private TenantTypeRepository tenantTypeRepository;

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

        long start = System.currentTimeMillis();
        Tenant tenant = jwtUtl.getTenantFromRequest(request);
        String tenantType = tenant != null && tenant.getTenantType() != null
                ? tenant.getTenantType().trim().toLowerCase()
                : "";

        LoggedInUserDetailsBean user =
                (LoggedInUserDetailsBean) request.getAttribute("loggedInUser");

        // No user → return empty
        if (user == null || user.getScopes() == null || user.getScopes().isEmpty()) {
            log.warn("User or user scopes missing → returning empty list");
            return List.of();
        }

        Set<String> userScopes = user.getScopes();
        log.debug("Fetching scopes for tenantType={}", tenantType);

        // 1️⃣ Get scopes based on tenant type
        List<Scopes> tenantScopes = switch (tenantType) {
            case "master mssp" -> scopesRepository.findAll();
            case "mssp"       -> scopesRepository.findByUserTypes(List.of("MSSP", "ENTERPRISE"));
            case "enterprise" -> scopesRepository.findByUserTypes(List.of("ENTERPRISE"));
            default -> {
                log.warn("Unknown tenant type '{}' → returning empty list", tenantType);
                yield List.of();
            }
        };

        if (tenantScopes.isEmpty()) {
            return List.of();
        }

        // 2️⃣ Filter only scopes that the user has
        List<Scopes> filtered = tenantScopes.stream()
                .filter(s -> userScopes.contains(s.getScopeName()))
                .toList();

        log.info("Returning {} scopes for tenantType={}", filtered.size(), tenantType);
        log.debug("Completed getAllScopes in {}ms", System.currentTimeMillis() - start);

        return filtered;
    }

    @Transactional
    public Scopes updateScopeTenantTypes(
            String scopeId,
            Set<String> tenantTypes
    ) {

        Scopes scope = scopesRepository.findByPkScopeId(scopeId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Scope not found: " + scopeId));

        List<TenantType> resolvedTenantTypes =
                tenantTypeRepository.findByTenantTypeNameIgnoreCaseIn(
                        tenantTypes.stream()
                                .map(String::trim)
                                .toList()
                );

        if (resolvedTenantTypes.isEmpty()) {
            throw new ResourceNotFoundException("No valid tenant types provided");
        }

        scope.setTenantTypes(new HashSet<>(resolvedTenantTypes));

        Scopes saved = scopesRepository.save(scope);
        log.info("Updated tenantTypes for scopeId={} -> {}",
                scopeId, tenantTypes);

        return saved;
    }

    @Transactional(readOnly = true)
    public List<Scopes> getAllScopes() {
        return scopesRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Scopes getScopeById(String scopeId) {
        return scopesRepository.findByPkScopeId(scopeId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Scope not found: " + scopeId));
    }

    // ---------------------------------------------------
    // GET SCOPES BY MENU NAME
    // ---------------------------------------------------
    @Transactional(readOnly = true)
    public List<Scopes> getScopesByMenu(String menuName) {
        return scopesRepository.findByMenuNameIgnoreCase(menuName);
    }

    // ---------------------------------------------------
    // GET SCOPES BY MENU + SUBMENU
    // ---------------------------------------------------
    @Transactional(readOnly = true)
    public List<Scopes> getScopesByMenuAndSubMenu(
            String menuName,
            String subMenu
    ) {
        return scopesRepository
                .findByMenuNameIgnoreCaseAndSubMenuIgnoreCase(
                        menuName, subMenu
                );
    }

}