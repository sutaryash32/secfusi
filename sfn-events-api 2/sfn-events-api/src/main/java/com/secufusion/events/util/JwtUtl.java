package com.secufusion.events.util;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.secufusion.events.entity.Tenant;
import com.secufusion.events.entity.User;
import com.secufusion.events.exception.*;
import com.secufusion.events.repository.TenantRepository;
import com.secufusion.events.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

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
    private JWTClaimsSet decodeToken(String token) {
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
                    logger.error("getTenantFromRequest: error querying tenantRepository for azp '{}'", azp, repoEx);
                }
            } else {
                logger.debug("getTenantFromRequest: 'azp' claim is missing");
            }

            // 2. Fallback: extract realm name from iss claim
            // Machine tokens (client_credentials) have azp = Keycloak client ID (e.g. "zolocuge-extension-client")
            // iss = "https://auth.example.com/realms/<tenant-name>"
            String iss = claims.getIssuer();
            if (iss != null && iss.contains("/realms/")) {
                String realmName = iss.substring(iss.lastIndexOf("/realms/") + 8);
                if (realmName.contains("/")) {
                    realmName = realmName.substring(0, realmName.indexOf("/"));
                }
                try {
                    Tenant tenant = tenantRepository.findByTenantName(realmName).orElse(null);
                    if (tenant != null) {
                        logger.debug("getTenantFromRequest: resolved tenant via iss realm='{}'", realmName);
                        return tenant;
                    }
                    logger.debug("getTenantFromRequest: no tenant found for iss realm='{}'", realmName);
                } catch (Exception repoEx) {
                    logger.error("getTenantFromRequest: error querying tenantRepository for realm '{}'", realmName, repoEx);
                }
            }

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
     * Get display name (name claim) from request's JWT.
     *
     * @param request incoming HTTP request
     * @return name claim or null if not present or on error
     */
    public String getDisplayName(HttpServletRequest request) {
        try {
            String token = extractToken(request);
            if (token == null) return null;

            JWTClaimsSet claims = decodeToken(token);
            if (claims == null) return null;

            String name = claims.getStringClaim("name");
            if (name == null) {
                logger.debug("getDisplayName: 'name' claim not found");
            }
            return name;
        } catch (Exception e) {
            logger.error("getDisplayName: unexpected error", e);
            return null;
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
            throw new TokenValidationException("Unexpected error during token validation", e);
        }
    }

    private String maskToken(String token) {
        if (token == null) return "null";
        int len = token.length();
        if (len <= 8) return "****";
        return token.substring(0, 4) + "..." + token.substring(len - 4);
    }

    /**
     * Extract user ID (sub) from JWT token
     *
     * @param token JWT token string
     * @return user ID from 'sub' claim or null if not present
     */
    public String extractUserIdFromToken(String token) {
        try {
            JWTClaimsSet claims = decodeToken(token);
            if (claims == null) return null;

            String sub = claims.getSubject();
            if (sub == null) {
                logger.debug("extractUserIdFromToken: subject (sub) claim not found");
            }
            return sub;
        } catch (Exception e) {
            logger.error("extractUserIdFromToken: unexpected error", e);
            return null;
        }
    }

    /**
     * Extract email from JWT token
     *
     * @param token JWT token string
     * @return email from 'email' claim or null if not present
     */
    public String extractEmailFromToken(String token) {
        try {
            JWTClaimsSet claims = decodeToken(token);
            if (claims == null) return null;

            String email = claims.getStringClaim("email");
            if (email == null) {
                logger.debug("extractEmailFromToken: 'email' claim not found");
            }
            return email;
        } catch (Exception e) {
            logger.error("extractEmailFromToken: unexpected error", e);
            return null;
        }
    }

    /**
     * Extract display name from JWT token
     *
     * @param token JWT token string
     * @return display name from 'name' claim or null if not present
     */
    public String extractDisplayNameFromToken(String token) {
        try {
            JWTClaimsSet claims = decodeToken(token);
            if (claims == null) return null;

            String name = claims.getStringClaim("name");
            if (name == null) {
                logger.debug("extractDisplayNameFromToken: 'name' claim not found");
            }
            return name;
        } catch (Exception e) {
            logger.error("extractDisplayNameFromToken: unexpected error", e);
            return null;
        }
    }

    /**
     * Extract Azure tenant ID from JWT token
     *
     * @param token JWT token string
     * @return Azure tenant ID from 'tid' claim or null if not present
     */
    public String extractAzureTenantIdFromToken(String token) {
        try {
            JWTClaimsSet claims = decodeToken(token);
            if (claims == null) return null;

            String tid = claims.getStringClaim("azure_tenant_id");
            if (tid == null) {
                logger.debug("extractAzureTenantIdFromToken: 'tid' claim not found");
            }
            return tid;
        } catch (Exception e) {
            logger.error("extractAzureTenantIdFromToken: unexpected error", e);
            return null;
        }
    }

    /**
     * Extract Azure groups from JWT token
     *
     * @param token JWT token string
     * @return List of Azure group IDs from 'groups' claim or empty list if not present
     */
    public List<String> extractAzureGroupsFromToken(String token) {
        try {
            JWTClaimsSet claims = decodeToken(token);
            if (claims == null) return Collections.emptyList();

            Object groupsClaim = claims.getClaim("groups");
            if (groupsClaim == null) {
                logger.debug("extractAzureGroupsFromToken: 'groups' claim not found");
                return Collections.emptyList();
            }

            // Groups claim can be either a List or an array
            if (groupsClaim instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> groups = (List<String>) groupsClaim;
                return groups;
            } else if (groupsClaim instanceof String[]) {
                return Arrays.asList((String[]) groupsClaim);
            } else {
                logger.warn("extractAzureGroupsFromToken: 'groups' claim is of unexpected type: {}",
                        groupsClaim.getClass().getName());
                return Collections.emptyList();
            }
        } catch (Exception e) {
            logger.error("extractAzureGroupsFromToken: unexpected error", e);
            return Collections.emptyList();
        }
    }

}