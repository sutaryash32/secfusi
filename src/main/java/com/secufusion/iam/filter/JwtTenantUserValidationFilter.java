package com.secufusion.iam.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secufusion.iam.dto.LoggedInUserDetailsBean;
import com.secufusion.iam.entity.*;
import com.secufusion.iam.entity.TenantApiMappingEntity;
import com.secufusion.iam.exception.*;
import com.secufusion.iam.repository.ApiFlagRepository;
import com.secufusion.iam.repository.TenantApiMappingRepository;
import com.secufusion.iam.repository.TenantRepository;
import com.secufusion.iam.service.UserService;

import com.secufusion.iam.util.JwtUtl;
import dev.openfeature.sdk.*;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;


/**
 * Filter that validates JWTs, tenant and user state, resolves scopes,
 * injects Spring Security authentication and evaluates feature flags.
 * <p>
 * Logging and comments have been added for observability of each major step.
 */
@Component
public class JwtTenantUserValidationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtTenantUserValidationFilter.class);

    // FEATURE FLAG CACHE
    // Map<tenantId, Map<path, enabled>>
    private volatile Map<String, Map<String, Boolean>> tenantPathEnabledCache = new ConcurrentHashMap<>();
    // Set of API paths that are managed by feature flags
    private volatile Set<String> managedApiPaths = ConcurrentHashMap.newKeySet();

    @Autowired
    private JwtUtl jwtUtil;
    @Autowired
    private TenantRepository tenantRepository;
    @Autowired
    private UserService userService;
    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ApiFlagRepository featureFlagRepository;
    @Autowired
    private TenantApiMappingRepository apiMappingRepository;


    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        log.info(">> JwtTenantUserValidationFilter executed for: " + request.getRequestURI());

        String authHeader = request.getHeader("Authorization");

        // ============================================================
        // 1️⃣ NO AUTHORIZATION → DO NOT HANDLE ANYTHING HERE
        // Spring Security will decide based on permitted URLs
        // ============================================================
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.debug("No Authorization header present or not Bearer - skipping filter for request URI: {}", request.getRequestURI());
            filterChain.doFilter(request, response);
            return;
        }

        try {
            // Bypass validations for one specific endpoint: POST /events
            if ("/events".contains(request.getRequestURI()) && "POST".equalsIgnoreCase(request.getMethod())) {
                log.debug("Skipping JWT/tenant/user validation for POST /events");
                filterChain.doFilter(request, response);
                return;
            }
            // ============================================================
            // 2️⃣ Extract details from JWT
            // ============================================================
            log.debug("Authorization header present - extracting JWT details for request URI: {}", request.getRequestURI());
            User userFromRequest = jwtUtil.getUserFromRequest(request);
            String email = (userFromRequest != null) ? userFromRequest.getEmail() : null;
            Tenant tenantFromJwt = jwtUtil.getTenantFromRequest(request);

            if (email == null || tenantFromJwt == null) {
                log.warn("Invalid token: email or tenant missing in JWT (email={}, tenant={})", email, tenantFromJwt);
                throw new InvalidTokenException("Invalid or missing JWT");
            }

            String tenantId = tenantFromJwt.getTenantID();
            log.debug("Extracted email='{}' tenantId='{}' from JWT", email, tenantId);

            // ============================================================
            // 3️⃣ Validate Tenant
            // ============================================================
            log.debug("Validating tenant with id: {}", tenantId);
            Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
            if (tenant == null) {
                log.info("Tenant not found: {}", tenantId);
                throw new ResourceNotFoundException("Tenant does not exist");
            }
            if (!"ACTIVE".equalsIgnoreCase(tenant.getStatus())) {
                log.info("Tenant inactive: {} status={}", tenantId, tenant.getStatus());
                throw new AccessDeniedException("Tenant is inactive");
            }
            log.debug("Tenant validated and active: {}", tenantId);

            // ============================================================
            // 4️⃣ Validate User
            // ============================================================
            log.debug("Resolving user by email '{}' for tenant '{}'", email, tenantId);
            User user = userService.findByEmailAndTenant(userFromRequest.getEmail(), tenantId);
            if (user == null) {
                log.info("User not found for email '{}' in tenant '{}'", email, tenantId);
                throw new ResourceNotFoundException("User not found in tenant");
            }
            if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
                log.info("User inactive: email='{}' tenant='{}' status='{}'", email, tenantId, user.getStatus());
                throw new AccessDeniedException("User account inactive");
            }
            log.debug("User validated and active: email='{}' tenant='{}'", email, tenantId);

            // ============================================================
            // 5️⃣ Extract scopes
            // ============================================================
            log.debug("Resolving scopes for user '{}'", email);
            Set<String> rawScopes = resolveUserScopes(user);
            if (rawScopes.isEmpty()) {
                log.info("User has no scopes assigned: email='{}' tenant='{}'", email, tenantId);
                throw new AccessDeniedException("User has no access scopes assigned");
            }
            Set<String> effectiveScopes = new HashSet<>(rawScopes);
            log.debug("Resolved scopes for user '{}': rawCount={} effectiveCount={}", email, rawScopes.size(), effectiveScopes.size());

            // ============================================================
            // 6️⃣ Build LoggedInUserDetailsBean
            // ============================================================
            log.debug("Building LoggedInUserDetailsBean for user '{}' tenant '{}'", email, tenantId);
            LoggedInUserDetailsBean loggedInUser =
                    LoggedInUserDetailsBean.from(user, tenant, effectiveScopes, rawScopes);

            // set attribute for downstream handlers/controllers
            request.setAttribute("loggedInUser", loggedInUser);
            log.debug("Attached loggedInUser to request attribute for '{}'", email);

            // ============================================================
            // 7️⃣ Create Spring Security Authentication
            // ============================================================
            log.debug("Creating Spring Security authentication for user '{}'", email);
            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(
                            user.getEmail(),
                            null,
                            effectiveScopes.stream().map(SimpleGrantedAuthority::new).toList()
                    );

            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authToken);
            log.debug("Authentication set in SecurityContext for user '{}'", email);

            // ============================================================
            // 8️⃣ Feature Flag Check (ONLY for authenticated requests)
            // ============================================================
            String path = request.getRequestURI();
            log.debug("Performing feature flag check for path='{}' tenant='{}'", path, tenantId);

            Client client = OpenFeatureAPI.getInstance().getClient();

            EvaluationContext ctx = new ImmutableContext(
                    null,
                    Map.of("tenantId", new Value(tenantId))
            );

            // OpenFeature → calls your DB provider
            boolean allow = client.getBooleanValue(path, true, ctx);
            log.debug("OpenFeature evaluation for path='{}' tenant='{}' returned allow={}", path, tenantId, allow);

            if (!allow) {
                log.info("API disabled by feature flag for path='{}' tenant='{}'", path, tenantId);
                throw new ResourceNotFoundException("API disabled for tenant");
            }

            // Continue normal flow
            log.debug("Request allowed - continuing filter chain for path='{}' tenant='{}'", path, tenantId);
            filterChain.doFilter(request, response);

        } catch (TokenExpiredException |
                TokenMismatchException |
                TokenValidationException |
                InvalidTokenException ex) {
            throw ex;
        }
        catch (Exception ex) {
            throw new TokenValidationException(ex.getMessage(), ex);
        }
    }

    // ============================================================
    // 💥 Cache Refresh (called from scheduler)
    // ============================================================
    public void refreshCache() {
        log.debug("Refreshing feature flag caches - starting");

        // Load which APIs are managed
        Set<String> newManagedPaths = ConcurrentHashMap.newKeySet();
        featureFlagRepository.findAll().forEach(api -> {
            if (api != null && api.getPath() != null) {
                newManagedPaths.add(api.getPath());
            }
        });
        this.managedApiPaths = newManagedPaths;
        log.debug("Loaded managed API paths count={}", this.managedApiPaths.size());

        // Load enabled/disabled per tenant
        Map<String, Map<String, Boolean>> newTenantMap = new ConcurrentHashMap<>();
        int mappingCount = 0;
        for (TenantApiMappingEntity mapping : apiMappingRepository.findAll()) {
            if (mapping == null) continue;
            mappingCount++;
            newTenantMap
                    .computeIfAbsent(mapping.getTenantId(), k -> new ConcurrentHashMap<>())
                    .put(mapping.getApi().getPath(), mapping.isEnabled());
        }

        this.tenantPathEnabledCache = newTenantMap;
        log.debug("Loaded tenant API mappings count={} tenants={}", mappingCount, newTenantMap.size());
//        log.info("Feature flag cache refresh complete: managedPaths={} tenantEntries={}", this.managedApiPaths.size(), this.tenantPathEnabledCache.size());
    }

    // ============================================================
    private Set<String> resolveUserScopes(User user) {
        // Defensive null checks and logging
        if (user == null) {
            log.warn("resolveUserScopes called with null user");
            throw new AccessDeniedException("User must not be null");
        }

        if (user.getTenant() == null || user.getTenant().getTenantType() == null) {
            log.warn("User '{}' has no tenant or tenant type", user.getEmail());
            throw new AccessDeniedException("User has no tenant or tenant type");
        }

        if (user.getMappedGroups() == null) {
            log.debug("User '{}' has no mapped groups", user.getEmail());
            throw new AccessDeniedException("User has no mapped groups");
        }

        // Collect scope names from user's groups -> roles -> scopes
        Set<String> scopes = user.getMappedGroups()
                .stream()
                .filter(Objects::nonNull)
                .flatMap(g -> Optional.ofNullable(g.getMappedRoles()).orElse(Collections.emptySet()).stream())
                .flatMap(r -> Optional.ofNullable(r.getScopes()).orElse(Collections.emptySet()).stream())
                .map(Scopes::getScopeName)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        log.debug("resolveUserScopes for user='{}' resolved {} scopes", user.getEmail(), scopes.size());
        return scopes;
    }

    private void writeError(HttpServletResponse res, HttpStatus status, String code, String message)
            throws IOException {
        // Log error before writing response to help trace failures
        log.debug("Returning error response status={} code={} message={}", status.value(), code, message);
        res.setStatus(status.value());
        res.setContentType("application/json");
        res.getWriter().write(objectMapper.writeValueAsString(
                Map.of("error", code, "message", message, "status", status.value())
        ));
    }

}