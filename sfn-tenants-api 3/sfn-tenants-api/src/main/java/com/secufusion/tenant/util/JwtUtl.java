package com.secufusion.tenant.util;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.secufusion.tenant.entity.Tenant;
import com.secufusion.tenant.entity.User;
import com.secufusion.tenant.exception.*;
import com.secufusion.tenant.repository.TenantRepository;
import com.secufusion.tenant.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Utility service to extract and decode JWT tokens from HTTP requests and
 * obtain user details such as username, email, userId and tenant.
 * <p>
 * All exceptions are caught and logged; methods return null on failure.
 */
@Slf4j
@Service
public class JwtUtl {

    private static final Logger logger = LoggerFactory.getLogger(JwtUtl.class);

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserRepository userRepository;

    /**
     * Extract full token from Authorization header.
     *
     * @param request incoming HTTP request
     * @return token without "Bearer " prefix, or null if missing/invalid
     */
    private String extractToken(HttpServletRequest request) {
        if (request == null) {
            logger.warn("extractToken: request is null");
            return null;
        }
        try {
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                if (token.isEmpty()) {
                    logger.warn("extractToken: bearer token is empty");
                    return null;
                }
                return token;
            } else {
                logger.debug("extractToken: Authorization header missing or does not start with Bearer");
                return null;
            }
        } catch (Exception e) {
            logger.error("extractToken: unexpected error while extracting token", e);
            return null;
        }
    }

    /**
     * Decode JWT safely. Returns null on parse/validation errors.
     *
     * @param token raw JWT string
     * @return JWTClaimsSet or null if invalid
     */
    public JWTClaimsSet decodeToken(String token) {
        if (token == null) {
            logger.debug("decodeToken: token is null");
            return null;
        }
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            if (claims == null) {
                logger.warn("decodeToken: JWT claims set is null");
            }
            return claims;
        } catch (java.text.ParseException pe) {
            logger.warn("decodeToken: failed to parse JWT token: {}", pe.getMessage());
            return null;
        } catch (Exception e) {
            logger.error("decodeToken: unexpected error decoding JWT", e);
            return null;
        }
    }

    /**
     * Get username (preferred_username) from request's JWT.
     *
     * @param request incoming HTTP request
     * @return preferred_username or null if not present or on error
     */
    public String getUsername(HttpServletRequest request) {
        try {
            String token = extractToken(request);
            if (token == null) return null;

            JWTClaimsSet claims = decodeToken(token);
            if (claims == null) return null;

            String username = claims.getStringClaim("preferred_username");
            if (username == null) {
                logger.debug("getUsername: 'preferred_username' claim not found");
            }
            return username;
        } catch (Exception e) {
            logger.error("getUsername: unexpected error", e);
            return null;
        }
    }

    /**
     * Get email from request's JWT.
     *
     * @param request incoming HTTP request
     * @return email claim or null if not present or on error
     */
    public String getEmail(HttpServletRequest request) {
        try {
            String token = extractToken(request);
            if (token == null) return null;

            JWTClaimsSet claims = decodeToken(token);
            if (claims == null) return null;

            String email = claims.getStringClaim("email");
            if (email == null) {
                logger.debug("getEmail: 'email' claim not found");
            }
            return email;
        } catch (Exception e) {
            logger.error("getEmail: unexpected error", e);
            return null;
        }
    }

    /**
     * Resolve Tenant by email extracted from JWT in the request.
     *
     * @param request incoming HTTP request
     * @return Tenant or null if not found or on error
     */
    public Tenant getTenantFromRequest(HttpServletRequest request) {
        try {
            String token = extractToken(request);
            if (token == null) return null;

            JWTClaimsSet claims = decodeToken(token);
            if (claims == null) return null;

            if (tenantRepository == null) {
                logger.error("getTenantFromRequest: tenantRepository is not initialized");
                return null;
            }

            // 1. Try azp claim first (regular user tokens: azp = tenant name)
            String azp = claims.getStringClaim("azp");
            if (azp != null) {
                try {
                    Tenant tenant = tenantRepository.findByTenantName(azp).orElse(null);
                    if (tenant != null) {
                        logger.debug("getTenantFromRequest: resolved tenant via azp='{}'", azp);
                        return tenant;
                    }
                    logger.debug("getTenantFromRequest: no tenant found for azp='{}', trying iss fallback", azp);
                } catch (Exception repoEx) {
                    logger.error("getTenantFromRequest: error querying tenantRepository for azp={}", azp, repoEx);
                }
            }

            // 2. Fallback: extract realm name from iss claim
            // Machine tokens (client_credentials) have azp = client ID (e.g. "zolocuge-extension-client")
            // but the actual tenant name is the Keycloak realm embedded in iss:
            // iss = "https://auth.example.com/realms/<tenant-name>"
            String iss = claims.getIssuer();
            if (iss != null && iss.contains("/realms/")) {
                String realmName = iss.substring(iss.lastIndexOf("/realms/") + 8);
                if (realmName.contains("/")) {
                    realmName = realmName.substring(0, realmName.indexOf("/"));
                }
                logger.debug("getTenantFromRequest: trying iss realm fallback, realmName='{}'", realmName);
                try {
                    Tenant tenant = tenantRepository.findByTenantName(realmName).orElse(null);
                    if (tenant != null) {
                        logger.debug("getTenantFromRequest: resolved tenant via iss realm='{}'", realmName);
                        return tenant;
                    }
                } catch (Exception repoEx) {
                    logger.error("getTenantFromRequest: error querying tenantRepository for realm={}", realmName, repoEx);
                }
            }

            logger.debug("getTenantFromRequest: could not resolve tenant from azp='{}' or iss='{}'", azp, iss);
            return null;

        } catch (Exception e) {
            logger.error("getTenantFromRequest: unexpected error", e);
            return null;
        }
    }

    /**
     * Resolve User by email extracted from JWT in the request.
     *
     * @param request incoming HTTP request
     * @return User or null if not found or on error
     */
    public User getUserFromRequest(HttpServletRequest request) {
        try {
            String token = extractToken(request);
            if (token == null) return null;

            JWTClaimsSet claims = decodeToken(token);
            if (claims == null) return null;

            if (userRepository == null) {
                logger.error("getUserFromRequest: userRepository is not initialized");
                return null;
            }

            String email = claims.getStringClaim("email");
            String preferred = claims.getStringClaim("preferred_username");

            logger.debug("Lookup claims - email: {}, preferred: {}", email, preferred);

            // Use a Set to avoid duplicate queries
            Set<String> candidates = new LinkedHashSet<>();

            if (email != null) candidates.add(email);
            if (preferred != null) candidates.add(preferred);

            // Also add lowercase variants (case ignore safety)
            candidates.addAll(
                    candidates.stream()
                            .map(String::toLowerCase)
                            .toList()
            );

            // Now try all combinations: email, preferred, username
            for (String candidate : candidates) {

                // Try email lookup
                try {
                    User u = userRepository.findByEmailIgnoreCase(candidate).orElse(null);
                    if (u != null) {
                        logger.debug("Found user by emailIgnoreCase({})", candidate);
                        return u;
                    }
                } catch (Exception ex) {
                    logger.warn("Failed lookup: findByEmailIgnoreCase({})", candidate, ex);
                }

                // Try username lookup
                try {
                    User u = userRepository.findByUserNameIgnoreCase(candidate).orElse(null);
                    if (u != null) {
                        logger.debug("Found user by userNameIgnoreCase({})", candidate);
                        return u;
                    }
                } catch (Exception ex) {
                    logger.warn("Failed lookup: findByUserNameIgnoreCase({})", candidate, ex);
                }
            }

            logger.debug("No user found using any lookup strategy (email/preferred/username)");
            return null;

        } catch (Exception ex) {
            logger.error("getUserFromRequest: unexpected error", ex);
            return null;
        }
    }

    /**
     * Helper to extract all possible matching IDs from the JWT.
     * We combine "groups" (Azure OIDs), "roles" (Azure App Roles),
     * and "realm_access.roles" (Keycloak) into one list.
     * * @param request incoming HTTP request
     * @return List of strings containing Group IDs and Role names
     */
    public List<String> extractUserClaims(HttpServletRequest request) {
        try {
            // 1. Get the raw token string
            String token = extractToken(request);
            if (token == null) {
                return java.util.Collections.emptyList();
            }

            // 2. Decode the token using your existing helper
            JWTClaimsSet claims = decodeToken(token);
            if (claims == null) {
                return java.util.Collections.emptyList();
            }

            List<String> combinedClaims = new java.util.ArrayList<>();

            // 3. Extract "groups" claim (Standard Azure Object IDs)
            try {
                List<String> groups = claims.getStringListClaim("groups");
                if (groups != null) {
                    combinedClaims.addAll(groups);
                }
            } catch (java.text.ParseException e) {
                log.warn("extractUserClaims: Failed to parse 'groups' claim as list: {}", e.getMessage());
            }

            // 4. Extract "roles" claim (Standard Azure App Roles)
            try {
                List<String> roles = claims.getStringListClaim("roles");
                if (roles != null) {
                    combinedClaims.addAll(roles);
                }
            } catch (java.text.ParseException e) {
                log.warn("extractUserClaims: Failed to parse 'roles' claim as list: {}", e.getMessage());
            }

            // 5. Extract "realm_access.roles" (Keycloak specific structure)
            // Keycloak nests roles like: "realm_access": { "roles": ["admin", "user"] }
//            try {
//                java.util.Map<String, Object> realmAccess = claims.getJSONObjectClaim("realm_access");
//                if (realmAccess != null && realmAccess.containsKey("roles")) {
//                    Object rolesObj = realmAccess.get("roles");
//                    if (rolesObj instanceof List<?>) {
//                        for (Object item : (List<?>) rolesObj) {
//                            if (item instanceof String) {
//                                combinedClaims.add((String) item);
//                            }
//                        }
//                    }
//                }
//            } catch (java.text.ParseException e) {
//                // Ignore: valid scenario for Azure tokens which don't have this structure
//                log.trace("extractUserClaims: No 'realm_access' claim found (likely not a Keycloak token)");
//            }

            return combinedClaims;

        } catch (Exception e) {
            log.warn("extractUserClaims: Unexpected error: {}", e.getMessage());
            return java.util.Collections.emptyList();
        }
    }

    public String getPreferredUsernameFromRequest(HttpServletRequest request) {
        try {
            String token = extractToken(request);
            if (token == null) return null;

            JWTClaimsSet claims = decodeToken(token);
            if (claims == null) return null;

            String username = claims.getStringClaim("preferred_username");
            if (username == null) {
                logger.debug("getPreferredUsernameFromRequest: 'preferred_username' claim not found");
            }
            return username;
        } catch (Exception e) {
            logger.error("getPreferredUsernameFromRequest: unexpected error", e);
            return null;
        }
    }
    /**
     * Get userId (sub) from request's JWT.
     *
     * @param request incoming HTTP request
     * @return subject claim or null if not present or on error
     */
    public String getUserId(HttpServletRequest request) {
        try {
            String token = extractToken(request);
            if (token == null) return null;

            JWTClaimsSet claims = decodeToken(token);
            if (claims == null) return null;

            String sub = claims.getSubject();
            if (sub == null) {
                logger.debug("getUserId: subject (sub) claim not found");
            }
            return sub;
        } catch (Exception e) {
            logger.error("getUserId: unexpected error", e);
            return null;
        }
    }

    public boolean validateRequestToken(HttpServletRequest request, String token) {
        log.info("validateRequestToken: start");
        if (token == null) {
            log.error("validateRequestToken: provided token is null");
            throw new InvalidTokenException("Token is null");
        }

        String headerToken = extractToken(request);
        if (headerToken == null) {
            log.error("validateRequestToken: authorization header is missing or token not present");
            throw new MissingAuthorizationException("Authorization header is missing");
        }

        // Compare exact tokens; log masked values to avoid exposing them
        if (!headerToken.equals(token)) {
            log.warn("validateRequestToken: token mismatch (header vs provided). headerTokenMask={}, providedTokenMask={}",
                    maskToken(headerToken), maskToken(token));
            throw new TokenMismatchException("Token mismatch");
        }

        try {
            // Decode token to check expiration
            JWTClaimsSet claims = decodeToken(token);
            if (claims == null) {
                log.error("validateRequestToken: failed to decode token");
                throw new TokenValidationException("Failed to decode token");
            }

            java.util.Date exp = claims.getExpirationTime();
            log.debug("validateRequestToken: token expiration: {}", exp);
            if (exp != null && exp.before(new java.util.Date())) {
                log.info("validateRequestToken: token is expired");
                throw new TokenExpiredException("Token is expired");
            }

            log.info("validateRequestToken: token is valid");
            return true;
        } catch (TokenValidationException | TokenExpiredException | TokenMismatchException |
                 MissingAuthorizationException | InvalidTokenException e) {
            // Re-throw known custom exceptions unchanged
            throw e;
        } catch (Exception e) {
            log.error("validateRequestToken: token validation failed: {}", e.getMessage(), e);
            throw new TokenValidationException("Unexpected error during token validation");
        }
    }

    private String maskToken(String token) {
        if (token == null) return "null";
        int len = token.length();
        if (len <= 8) return "****";
        return token.substring(0, 4) + "..." + token.substring(len - 4);
    }

    public Tenant getTenantFromToken(String token) {
        try {
            JWTClaimsSet claims = decodeToken(token);
            if (claims == null) return null;

            if (tenantRepository == null) {
                logger.error("getTenantFromToken: tenantRepository is not initialized");
                return null;
            }

            // 1. Try azp claim first
            String azp = claims.getStringClaim("azp");
            if (azp != null) {
                try {
                    Tenant tenant = tenantRepository.findByTenantName(azp).orElse(null);
                    if (tenant != null) return tenant;
                    logger.debug("getTenantFromToken: no tenant for azp='{}', trying iss fallback", azp);
                } catch (Exception repoEx) {
                    logger.error("getTenantFromToken: error querying tenantRepository for azp={}", azp, repoEx);
                }
            }

            // 2. Fallback: extract realm from iss claim
            String iss = claims.getIssuer();
            if (iss != null && iss.contains("/realms/")) {
                String realmName = iss.substring(iss.lastIndexOf("/realms/") + 8);
                if (realmName.contains("/")) {
                    realmName = realmName.substring(0, realmName.indexOf("/"));
                }
                try {
                    return tenantRepository.findByTenantName(realmName).orElse(null);
                } catch (Exception repoEx) {
                    logger.error("getTenantFromToken: error querying tenantRepository for realm={}", realmName, repoEx);
                }
            }

            return null;
        } catch (Exception e) {
            logger.error("getTenantFromToken: unexpected error", e);
            return null;
        }
    }

    public List<String> getClaimAsStringList(HttpServletRequest request, String claimName) {
        try {
            String token = extractToken(request);
            if (token == null) {
                return Collections.emptyList();
            }

            JWTClaimsSet claims = decodeToken(token);
            if (claims == null) {
                return Collections.emptyList();
            }

            Object claimValue = claims.getClaim(claimName);

            if (claimValue == null) {
                logger.debug("Claim '{}' is missing", claimName);
                return Collections.emptyList();
            }

            // Case 1: Proper List<String>
            if (claimValue instanceof List<?>) {
                return ((List<?>) claimValue).stream()
                        .filter(Objects::nonNull)
                        .map(String::valueOf)
                        .collect(Collectors.toList());
            }

            // Case 2: Single String
            if (claimValue instanceof String) {
                String value = (String) claimValue;

                // If comma-separated → split
                if (value.contains(",")) {
                    return Arrays.stream(value.split(","))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .collect(Collectors.toList());
                }

                // Single value → wrap as list
                return Collections.singletonList(value);
            }

            logger.warn("Claim '{}' has unsupported type: {}", claimName, claimValue.getClass());
            return Collections.emptyList();

        } catch (Exception e) {
            logger.error("Failed to extract claim '{}'", claimName, e);
            return Collections.emptyList();
        }
    }


}