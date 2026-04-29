package com.secufusion.iam.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secufusion.iam.service.AuthConfigService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * Resolves an AuthenticationManager per incoming request based on the JWT issuer.
 * <p>
 * Behavior:
 * - Reads the Authorization header, expects "Bearer &lt;token&gt;".
 * - Extracts the JWT payload (base64url decode) and reads the "iss" claim.
 * - Looks up a JwtDecoder for that issuer from AuthConfigService.
 * - Returns a JwtAuthenticationProvider bound to the found decoder.
 * <p>
 * Notes:
 * - Returns null when no suitable AuthenticationManager can be resolved. The caller
 * should handle that case (e.g. allow other resolvers or reject the request).
 * - Does not attempt to validate the token here; validation is performed by the decoder/provider.
 */
@Component
public class DbJwtAuthenticationManagerResolver implements AuthenticationManagerResolver<HttpServletRequest> {

    private static final Logger logger = LoggerFactory.getLogger(DbJwtAuthenticationManagerResolver.class);

    private final AuthConfigService tenantService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DbJwtAuthenticationManagerResolver(AuthConfigService tenantService) {
        this.tenantService = tenantService;
    }

    /**
     * Resolve an AuthenticationManager for the given request. Uses the JWT issuer as the tenant key.
     */
    @Override
    public AuthenticationManager resolve(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        logger.debug("Resolving AuthenticationManager for request uri={} authorizationPresent={}",
                request.getRequestURI(), authHeader != null);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            logger.debug("Authorization header missing or does not start with 'Bearer '. header={}", authHeader);
            return null; // No token, skip
        }

        String token = authHeader.substring(7).trim(); // "Bearer ".length()
        logger.debug("Extracted Bearer token (length={}) from Authorization header", token.length());

        String issuer = extractIssuer(token);
        if (issuer == null) {
            logger.warn("Could not extract issuer from token for request uri={}", request.getRequestURI());
            return null;
        }
        logger.debug("Extracted issuer='{}' from token", issuer);

        Map<String, JwtDecoder> decoders = tenantService.getJwtDecoders(); // Note: JwtDecoder, not Reactive
        JwtDecoder decoder = decoders.get(issuer);
        if (decoder == null) {
            logger.warn("No JwtDecoder configured for issuer='{}'", issuer);
            return null;
        }

        logger.debug("Returning JwtAuthenticationProvider bound to decoder for issuer='{}'", issuer);
        // Return manager for this specific tenant's decoder
        return new JwtAuthenticationProvider(decoder)::authenticate;
    }

    /**
     * Extracts the "iss" claim from a JWT without validating it.
     * <p>
     * Steps:
     * - Split the token by '.' and take the payload (index 1).
     * - Base64url-decode the payload (adding padding if necessary).
     * - Parse JSON and return the "iss" value if present.
     * <p>
     * Returns null on parse errors or if the claim is missing.
     */
    private String extractIssuer(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                logger.debug("JWT does not have a payload part (expected 3 parts). partsCount={}", parts.length);
                return null;
            }

            String payload = parts[1];

            // Base64url decoding requires padding to be correct length. Add '=' padding if needed.
            while (payload.length() % 4 != 0) {
                payload += "=";
            }

            String decoded = new String(
                    Base64.getUrlDecoder().decode(payload),
                    StandardCharsets.UTF_8
            );

            // Avoid logging the entire decoded payload because it may contain sensitive data.
            logger.debug("Decoded JWT payload length={}", decoded.length());

            JsonNode node = objectMapper.readTree(decoded);
            return node.has("iss") ? node.get("iss").asText() : null;
        } catch (Exception e) {
            logger.debug("Failed to extract issuer from JWT payload", e);
            return null;
        }
    }
}