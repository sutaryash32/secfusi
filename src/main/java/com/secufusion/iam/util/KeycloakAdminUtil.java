package com.secufusion.iam.util;

import com.secufusion.iam.dto.CreateIdentityProviderRequest;
import com.secufusion.iam.exception.KeycloakOperationException;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.*;
import org.keycloak.representations.idm.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.mail.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Utility wrapper around Keycloak Admin client for common realm/client/user operations.
 * Adds consistent logging, validation and exception handling (wraps errors into KeycloakOperationException).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KeycloakAdminUtil {

    private final Keycloak keycloak;

    // ---------------- SMTP properties (injected from application properties) ----------------
    @Value("${mail.smtp.host}")
    private String smtpHost;

    @Value("${mail.smtp.port}")
    private String smtpPort;

    @Value("${mail.smtp.auth}")
    private String smtpAuth;

    @Value("${mail.smtp.starttls}")
    private String smtpStarttls;

    @Value("${mail.smtp.username}")
    private String smtpUsername;

    @Value("${mail.smtp.password}")
    private String smtpPassword;

    @Value("${mail.smtp.mail}")
    private String smtpMail;

    @Value("${keycloak.admin.server-url}")
    private String keycloakServerUrl;

    // ============================================================
    // Helper to centralize exception wrapping and logging
    // ============================================================
    private KeycloakOperationException wrap(String code, int status, String op, Exception e) {
        log.error("{} - {}: {}", op, e.getClass().getSimpleName(), e.getMessage(), e);
        return new KeycloakOperationException(code, status, op + " failed: " + e.getMessage());
    }

    public String addIdentityProvider(String realm, CreateIdentityProviderRequest dto) {
        log.info("Adding identity provider '{}' to realm {}", dto.getAlias(), realm);

        Response resp = null;

        try {
            if (dto == null) {
                throw new KeycloakOperationException("INVALID_INPUT", 400,
                        "CreateIdentityProviderRequest must not be null");
            }

            RealmResource rr = keycloak.realm(realm);

            // -------------------------------
            // 1️⃣ Build IDP object
            // -------------------------------
            IdentityProviderRepresentation idpRep = new IdentityProviderRepresentation();
            idpRep.setAlias(dto.getAlias());
            idpRep.setProviderId(dto.getProviderId());
            idpRep.setEnabled(Boolean.TRUE.equals(dto.getEnabled()));
            idpRep.setStoreToken(Boolean.TRUE.equals(dto.getStoreToken()));
            idpRep.setLinkOnly(Boolean.TRUE.equals(dto.getLinkOnly()));
            idpRep.setTrustEmail(Boolean.TRUE.equals(dto.getTrustEmail()));
            idpRep.setDisplayName(dto.getDisplayName());

            // IDP Configuration
            Map<String, String> config = new HashMap<>();
            put(config, "clientId", dto.getClientId());
            put(config, "clientSecret", dto.getClientSecret());
            put(config, "authorizationUrl", dto.getAuthorizationUrl());
            put(config, "tokenUrl", dto.getTokenUrl());
            put(config, "userInfoUrl", dto.getUserInfoUrl());
            put(config, "issuer", dto.getIssuer());
            put(config, "redirectUri", dto.getRedirectUri());
            put(config, "tenantId", dto.getTenantId());
            config.put("scopes", "openid email profile");
            config.put("disableUserInfo", "true");

            idpRep.setConfig(config);

            // -------------------------------
            // 2️⃣ Create IDP in Keycloak
            // -------------------------------
            resp = rr.identityProviders().create(idpRep);

            int status = resp.getStatus();
            log.debug("IDP create response = {}", status);

            if (status != 201 && status != 409) {
                String body = resp.readEntity(String.class);
                throw new KeycloakOperationException("IDP_CREATE_FAILED", 500,
                        "Identity provider creation failed: " + body);
            }

            if (status == 409) {
                log.warn("Identity provider '{}' already exists in realm {}", dto.getAlias(), realm);
            } else {
                log.info("Identity provider '{}' created successfully in realm {}", dto.getAlias(), realm);
            }

            // -------------------------------
            // 3️⃣ Update IDP config (optional patches)
            // -------------------------------
            IdentityProviderResource idpRes = rr.identityProviders().get(dto.getAlias());
            IdentityProviderRepresentation rep = idpRes.toRepresentation();

            rep.setTrustEmail(true);
            rep.getConfig().put("disableUserInfo", "true");
            rep.getConfig().put("scopes", "openid email profile");

            idpRes.update(rep);

            try {
                configureAttributePassthrough(rr, dto.getAlias());
                configureRolePassthrough(rr, dto.getAlias());
            } catch (Exception e) {
                // We log error but DO NOT throw, so we still return the redirect URL
                log.error("Failed to configure auto-mappers for IdP '{}'. Users may not have groups in token. Error: {}",
                        dto.getAlias(), e.getMessage());
            }            // -------------------------------
            // 4️⃣ Return redirect URL
            // -------------------------------
            return buildAzureRedirectUrl(realm, dto.getAlias());

        } catch (KeycloakOperationException e) {
            throw new KeycloakOperationException("IDP_CREATE_FAILED", 500,
                    "Failed to create identity provider in realm " + realm);
        } finally {
            if (resp != null) resp.close();
        }
    }

    private String buildAzureRedirectUrl(String realm, String alias) {
        String keycloakBaseUrl = keycloakServerUrl;

        return keycloakBaseUrl + "/realms/" + realm + "/broker/" + alias + "/endpoint";
    }


    private void put(Map<String, String> map, String key, String value) {
        if (value != null && !value.isEmpty()) {
            map.put(key, value);
        }
    }


    public void setAsDefaultIdentityProvider(String realm, String alias) {
        RealmResource rr = keycloak.realm(realm);

        // Get all flows
        List<AuthenticationFlowRepresentation> flows = rr.flows().getFlows();

        // Find browser flow
        AuthenticationFlowRepresentation browserFlow = flows.stream()
                .filter(f -> "browser".equalsIgnoreCase(f.getAlias()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Browser flow not found"));

        // Get executions in browser flow
        List<AuthenticationExecutionInfoRepresentation> executions =
                rr.flows().getExecutions(browserFlow.getAlias());

        // Find Identity Provider Redirector execution
        AuthenticationExecutionInfoRepresentation idpRedirectExec = executions.stream()
                .filter(e -> "identity-provider-redirector".equals(e.getProviderId()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Identity Provider Redirector not found"));

        String configId = idpRedirectExec.getAuthenticationConfig();

        // -------------------------
        // 🔥 FIX: Create AuthenticatorConfigRepresentation
        // -------------------------
        AuthenticatorConfigRepresentation cfg = new AuthenticatorConfigRepresentation();
        cfg.setAlias("idp-redirector-config-" + alias);

        Map<String, String> configMap = new HashMap<>();
        configMap.put("defaultProvider", alias);
        cfg.setConfig(configMap);

        // If config doesn't exist, create it
        if (configId == null) {
            try (Response res = rr.flows().newExecutionConfig(idpRedirectExec.getId(), cfg)) {
                if (res.getStatus() != 201) {
                    throw new RuntimeException("Failed to create authenticator config for IDP redirector");
                }
            }
            log.info("Created new authenticator config for IDP redirector");
        } else {
            // Update existing config
            rr.flows().updateAuthenticatorConfig(configId, cfg);
            log.info("Updated authenticator config for IDP redirector");
        }

        log.info("Default Identity Provider for realm={} set to {}", realm, alias);
    }

    private void configureRolePassthrough(RealmResource rr, String idpAlias) {
        log.info("Configuring Azure App Role Mappers for IdP: {}", idpAlias);

        try {
            IdentityProviderResource idpResource = rr.identityProviders().get(idpAlias);

            // ---------------------------------------------------------
            // A. Add Identity Provider Mapper (Azure -> Keycloak DB)
            //    This reads the "roles" claim which contains names like "Manager"
            // ---------------------------------------------------------
            try {
                IdentityProviderMapperRepresentation roleImporter = new IdentityProviderMapperRepresentation();
                roleImporter.setName("Import Azure App Roles");
                roleImporter.setIdentityProviderAlias(idpAlias);
                roleImporter.setIdentityProviderMapper("oidc-user-attribute-idp-mapper");

                // CONFIGURATION:
                // "claim": "roles"        <-- This is what Azure sends (The readable names)
                // "user.attribute": "azure_roles" <-- We store it here in Keycloak
                roleImporter.setConfig(Map.of(
                        "claim", "roles",
                        "user.attribute", "azure_roles",
                        "syncMode", "FORCE"
                ));

                idpResource.addMapper(roleImporter);
                log.info("Added 'Import Azure App Roles' mapper for IdP: {}", idpAlias);
            } catch (Exception e) {
                log.warn("Could not add Role Mapper (might already exist): {}", e.getMessage());
            }

            // ---------------------------------------------------------
            // B. Add Client Scope (Keycloak DB -> JWT Token)
            //    This ensures the 'azure_roles' attribute gets written to the token
            // ---------------------------------------------------------
            String scopeName = "azure-role-data";
            ClientScopesResource scopesResource = rr.clientScopes();
            String scopeId = null;

            // 1. Create/Find Scope
            try {
                List<ClientScopeRepresentation> existingScopes = scopesResource.findAll();
                scopeId = existingScopes.stream()
                        .filter(s -> s.getName().equals(scopeName))
                        .map(ClientScopeRepresentation::getId)
                        .findFirst()
                        .orElse(null);

                if (scopeId == null) {
                    ClientScopeRepresentation scopeRep = new ClientScopeRepresentation();
                    scopeRep.setName(scopeName);
                    scopeRep.setProtocol("openid-connect");
                    scopeRep.setAttributes(Map.of(
                            "include.in.token.scope", "true",
                            "display.on.consent.screen", "false"
                    ));

                    try (Response r = scopesResource.create(scopeRep)) {
                        if (r.getStatus() == 201) {
                            String path = r.getLocation().getPath();
                            scopeId = path.substring(path.lastIndexOf("/") + 1);
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Error creating client scope: {}", e.getMessage());
            }

            // 2. Add Protocol Mapper to Scope
            if (scopeId != null) {
                ClientScopeResource scopeRes = scopesResource.get(scopeId);

                try {
                    ProtocolMapperRepresentation pmRoles = new ProtocolMapperRepresentation();
                    pmRoles.setName("Pass Roles to Token");
                    pmRoles.setProtocol("openid-connect");
                    pmRoles.setProtocolMapper("oidc-usermodel-attribute-mapper");

                    // CONFIGURATION:
                    // "user.attribute": "azure_roles" <-- Read from here
                    // "claim.name": "roles"           <-- Write to Token as "roles"
                    pmRoles.setConfig(Map.of(
                            "user.attribute", "azure_roles",
                            "claim.name", "roles",
                            "jsonType.label", "String",
                            "multivalued", "true",
                            "id.token.claim", "true",
                            "access.token.claim", "true"
                    ));

                    scopeRes.getProtocolMappers().createMapper(pmRoles);
                    log.info("Added 'Pass Roles to Token' mapper to scope '{}'", scopeName);
                } catch (Exception e) {
                    log.warn("Mapper might already exist in scope: {}", e.getMessage());
                }

                // 3. Make it a Default Scope (So all apps get it automatically)
                try {
                    rr.addDefaultDefaultClientScope(scopeId);
                } catch (Exception e) {
                    // Ignore if already default
                }
            }

        } catch (Exception e) {
            log.error("Fatal error configuring role passthrough: {}", e.getMessage(), e);
        }
    }
    /**
     * Automates the creation of Mappers so Azure Groups/Roles appear in the Spring Boot Token.
     * Contains granular try-catch blocks to prevent one failure from stopping the whole process.
     */
    private void configureAttributePassthrough(RealmResource rr, String idpAlias) {
        log.info("Configuring Auto-Mappers for IdP: {}", idpAlias);

        try {
            IdentityProviderResource idpResource = rr.identityProviders().get(idpAlias);

            // ---------------------------------------------------------
            // A. Add Identity Provider Mappers (Azure -> Keycloak DB)
            // ---------------------------------------------------------

            // 1. Map "groups" claim (Azure UUIDs) -> user attribute "azure_groups"
            try {
                IdentityProviderMapperRepresentation groupImporter = new IdentityProviderMapperRepresentation();
                groupImporter.setName("Import Azure Groups");
                groupImporter.setIdentityProviderAlias(idpAlias);
                groupImporter.setIdentityProviderMapper("oidc-user-attribute-idp-mapper");
                groupImporter.setConfig(Map.of(
                        "claim", "groups",
                        "user.attribute", "azure_groups",
                        "syncMode", "FORCE"
                ));
                idpResource.addMapper(groupImporter);
                log.info("Added 'Import Azure Groups' mapper for IdP: {}", idpAlias);
            } catch (Exception e) {
                // Usually 409 Conflict if it already exists
                log.warn("Could not add 'Import Azure Groups' mapper (might already exist) for IdP {}: {}", idpAlias, e.getMessage());
            }

            // 2. Map "roles" claim (Azure App Role Names) -> user attribute "azure_roles"
            try {
                IdentityProviderMapperRepresentation roleImporter = new IdentityProviderMapperRepresentation();
                roleImporter.setName("Import Azure Roles");
                roleImporter.setIdentityProviderAlias(idpAlias);
                roleImporter.setIdentityProviderMapper("oidc-user-attribute-idp-mapper");
                roleImporter.setConfig(Map.of(
                        "claim", "roles",
                        "user.attribute", "azure_roles",
                        "syncMode", "FORCE"
                ));
                idpResource.addMapper(roleImporter);
                log.info("Added 'Import Azure Roles' mapper for IdP: {}", idpAlias);
            } catch (Exception e) {
                log.warn("Could not add 'Import Azure Roles' mapper (might already exist) for IdP {}: {}", idpAlias, e.getMessage());
            }

            // ---------------------------------------------------------
            // B. Add Client Scope Mappers (Keycloak DB -> JWT Token)
            // ---------------------------------------------------------
            String scopeName = "azure-data";
            ClientScopesResource scopesResource = rr.clientScopes();
            String scopeId = null;

            // Try to create or find the scope
            try {
                List<ClientScopeRepresentation> existingScopes = scopesResource.findAll();

                // Check if exists
                scopeId = existingScopes.stream()
                        .filter(s -> s.getName().equals(scopeName))
                        .map(ClientScopeRepresentation::getId)
                        .findFirst()
                        .orElse(null);

                if (scopeId == null) {
                    ClientScopeRepresentation scopeRep = new ClientScopeRepresentation();
                    scopeRep.setName(scopeName);
                    scopeRep.setProtocol("openid-connect");
                    scopeRep.setAttributes(Map.of(
                            "include.in.token.scope", "true",
                            "display.on.consent.screen", "false"
                    ));

                    try (Response r = scopesResource.create(scopeRep)) {
                        if (r.getStatus() == 201) {
                            String path = r.getLocation().getPath();
                            scopeId = path.substring(path.lastIndexOf("/") + 1);
                            log.info("Created new global client scope '{}' with ID: {}", scopeName, scopeId);
                        } else {
                            log.error("Failed to create client scope '{}'. Status: {}", scopeName, r.getStatus());
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Error checking/creating client scope '{}': {}", scopeName, e.getMessage());
            }

            // If we have a valid Scope ID (either found or created), add the mappers
            if (scopeId != null) {
                ClientScopeResource scopeRes = scopesResource.get(scopeId);

                // Mapper 1: azure_groups -> Token "groups"
                try {
                    ProtocolMapperRepresentation pmGroups = new ProtocolMapperRepresentation();
                    pmGroups.setName("Pass Azure Groups");
                    pmGroups.setProtocol("openid-connect");
                    pmGroups.setProtocolMapper("oidc-usermodel-attribute-mapper");
                    pmGroups.setConfig(Map.of(
                            "user.attribute", "azure_groups",
                            "claim.name", "groups",
                            "jsonType.label", "String",
                            "multivalued", "true",
                            "id.token.claim", "true",
                            "access.token.claim", "true"
                    ));
                    scopeRes.getProtocolMappers().createMapper(pmGroups);
                    log.info("Added 'Pass Azure Groups' protocol mapper to scope '{}'", scopeName);
                } catch (Exception e) {
                    log.warn("Could not add 'Pass Azure Groups' mapper to scope '{}' (might exist): {}", scopeName, e.getMessage());
                }

                // Mapper 2: azure_roles -> Token "roles"
                try {
                    ProtocolMapperRepresentation pmRoles = new ProtocolMapperRepresentation();
                    pmRoles.setName("Pass Azure Roles");
                    pmRoles.setProtocol("openid-connect");
                    pmRoles.setProtocolMapper("oidc-usermodel-attribute-mapper");
                    pmRoles.setConfig(Map.of(
                            "user.attribute", "azure_roles",
                            "claim.name", "roles",
                            "jsonType.label", "String",
                            "multivalued", "true",
                            "id.token.claim", "true",
                            "access.token.claim", "true"
                    ));
                    scopeRes.getProtocolMappers().createMapper(pmRoles);
                    log.info("Added 'Pass Azure Roles' protocol mapper to scope '{}'", scopeName);
                } catch (Exception e) {
                    log.warn("Could not add 'Pass Azure Roles' mapper to scope '{}' (might exist): {}", scopeName, e.getMessage());
                }

                // Add this scope to Realm Default Client Scopes
                try {
                    rr.addDefaultDefaultClientScope(scopeId);
                    log.info("Ensured scope '{}' is a default realm scope", scopeName);
                } catch (Exception e) {
                    // Keycloak throws an error if it's already a default scope, so we just log debug
                    log.debug("Scope '{}' is already default or could not be added: {}", scopeName, e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("Fatal error in configureAttributePassthrough for IdP {}: {}", idpAlias, e.getMessage(), e);
        }
    }

    public void disableIdentityProvider(String realm, String alias) {
            log.info("Disabling Identity Provider '{}' in realm '{}'", alias, realm);
            try {
                RealmResource rr = keycloak.realm(realm);
                IdentityProviderResource idpRes = rr.identityProviders().get(alias);
                IdentityProviderRepresentation rep = idpRes.toRepresentation();
                if (rep == null) {
                    throw new KeycloakOperationException("IDP_NOT_FOUND", 404,
                            "Identity provider not found: " + alias);
                }
                rep.setEnabled(false);
                idpRes.update(rep);
                log.info("Disabled Identity Provider '{}' in realm '{}'", alias, realm);
            } catch (KeycloakOperationException e) {
                throw e;
            } catch (Exception e) {
                throw wrap("IDP_DISABLE_FAILED", 500,
                        "Failed to disable identity provider " + alias + " in realm " + realm, e);
            }
        }
    /**
     * Create a user. Returns created Keycloak user id or null if already exists.
     */
    public String createUser(String realm, String username, String email, String firstName, String lastName, boolean emailVerified) {
        log.info("Creating Keycloak user in realm='{}' username='{}'", realm, username);

        // Validate and ensure uniqueness
        try {
            findUsersByUsernameOrEmail(realm, username, email).forEach(u -> {
                log.warn("User with same username/email already exists in realm='{}': userId='{}', username='{}', email='{}'",
                        realm, u.getId(), u.getUsername(), u.getEmail());
                throw new KeycloakOperationException("USER_ALREADY_EXISTS", 409, "User with same username/email already exists in realm");
            });
        } catch (KeycloakOperationException e) {
            throw e;
        } catch (Exception e) {
            throw wrap("USER_CREATE_VALIDATION_FAILED", 500, "Failed validation for user creation in realm " + realm, e);
        }

        UserRepresentation user = new UserRepresentation();
        user.setUsername(username);
        user.setEmail(email);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEnabled(true);
        user.setEmailVerified(emailVerified);

        Response response = null;
        try {
            response = keycloak.realm(realm).users().create(user);
            int status = response.getStatus();

            if (status == 409) {
                log.warn("User already exists in realm={} username={}", realm, username);
                return null;
            }

            if (status != 201) {
                String body = response.readEntity(String.class);
                throw new KeycloakOperationException("USER_CREATE_FAILED", 500, "Failed to create user: " + body);
            }

            String userId = CreatedResponseUtil.getCreatedId(response);
            log.info("Created Keycloak user {} in realm {} with id {}", username, realm, userId);
            return userId;
        } catch (KeycloakOperationException e) {
            throw e;
        } catch (Exception e) {
            throw wrap("USER_CREATE_FAILED", 500, "Error creating user in Keycloak realm=" + realm + " username=" + username, e);
        } finally {
            if (response != null) {
                try {
                    response.close();
                } catch (Exception e) {
                    log.warn("Failed to close user creation response: {}", e.getMessage(), e);
                }
            }
        }
    }

    /**
     * Update user basic attributes. Validates uniqueness of username/email across other users.
     */
    public void updateUser(String realm, String userId, String username, String email, String firstName, String lastName) {
        log.info("Syncing KC user '{}' in realm '{}'", userId, realm);

        try {
            UsersResource users = keycloak.realm(realm).users();
            UserResource userResource = users.get(userId);

            // Fetch current KC state
            UserRepresentation rep = userResource.toRepresentation();
            if (rep == null) {
                throw new KeycloakOperationException("USER_NOT_FOUND", 404, "User not found in Keycloak");
            }

            // --- FIX FOR HTTP 400: DIRTY CHECKING ---
            // Only call setters if the value is ACTUALLY different.
            // This prevents triggering validation errors on fields we aren't changing.

            boolean isDirty = false;

            // 1. Check Username (Careful: If Realm forbids username changes, this avoids the error)
            if (username != null && !Objects.equals(rep.getUsername(), username)) {
                // Optional: Add check here if your realm allows username edits
                rep.setUsername(username);
                isDirty = true;
            }

            // 2. Check Email
            if (email != null && !Objects.equals(rep.getEmail(), email)) {
                rep.setEmail(email);
                // Verify email isn't verified automatically if you change it (optional policy)
                rep.setEmailVerified(false);
                isDirty = true;
            }

            // 3. Check Names
            if (!Objects.equals(rep.getFirstName(), firstName)) {
                rep.setFirstName(firstName);
                isDirty = true;
            }
            if (!Objects.equals(rep.getLastName(), lastName)) {
                rep.setLastName(lastName);
                isDirty = true;
            }

            // --- PERFORM UPDATE ONLY IF DIRTY ---
            if (isDirty) {
                // Optional: Re-validate Uniqueness here if you removed it from Service
                // (But typically Service layer handles the heavy lifting)

                userResource.update(rep);
                log.info("✔ KC User Updated: {}", userId);
            } else {
                log.info("⚠ KC Update Skipped: No fields differed from current Keycloak state.");
            }

        } catch (BadRequestException e) {
            // Capture the response body to see the REAL error message from Keycloak
            String responseBody = e.getResponse().readEntity(String.class);
            log.error("KC 400 Bad Request Details: {}", responseBody);
            throw new KeycloakOperationException("INVALID_INPUT", 400, "Keycloak rejected update: " + responseBody);
        } catch (Exception e) {
            log.error("KC Update Failure", e);
            throw new KeycloakOperationException("UPDATE_FAILED", 500, e.getMessage());
        }
    }

    /**
     * Remove a user by id.
     */
    public void removeUser(String realm, String userId) {
        log.info("Removing KC user '{}' from realm '{}'", userId, realm);
        try {
            keycloak.realm(realm).users().get(userId).remove();
            log.info("Removed KC user {}", userId);
        } catch (Exception e) {
            throw wrap("USER_DELETE_FAILED", 500, "Failed to remove KC user " + userId + " in realm " + realm, e);
        }
    }

    /**
     * Trigger Keycloak to send required action emails (e.g., verify email, update password).
     */
    public void sendRequiredActionEmail(String realm, String userId, List<String> actions) {
        log.info("Sending required-action email to KC user '{}' in realm {}", userId, realm);
        try {
            keycloak.realm(realm).users().get(userId).executeActionsEmail(actions);
            log.debug("Required-action email triggered for user {}", userId);
        } catch (Exception e) {
            throw wrap("EMAIL_ACTION_TRIGGER_FAILED", 500, "Failed to send required-action email to KC user " + userId, e);
        }
    }

    /**
     * Search users by username or email and return deduped list (by id).
     */
    public List<UserRepresentation> findUsersByUsernameOrEmail(
            String realm,
            String username,
            String email
    ) {
        UsersResource users = keycloak.realm(realm).users();
        List<UserRepresentation> results = new ArrayList<>();

        try {
            if (username != null && !username.isBlank()) {
                results.addAll(users.search(username, true));
            }
            if (email != null && !email.isBlank()) {
                results.addAll(users.search(email, true));
            }
        } catch (Exception e) {
            throw wrap(
                    "USER_SEARCH_FAILED",
                    500,
                    "Error searching KC for realm=" + realm +
                            " username=" + username +
                            " email=" + email,
                    e
            );
        }

        // 🔐 Filter to exact matches only
        return results.stream()
                .filter(u ->
                        (username != null && username.equalsIgnoreCase(u.getUsername())) ||
                                (email != null && email.equalsIgnoreCase(u.getEmail()))
                )
                // 🔁 Deduplicate by Keycloak user ID
                .collect(Collectors.toMap(
                        UserRepresentation::getId,
                        u -> u,
                        (a, b) -> a
                ))
                .values()
                .stream()
                .toList();
    }

    public void deleteIdentityProvider(String realm, String alias) {
        try {
            keycloak.realm(realm)
                    .identityProviders()
                    .get(alias)
                    .remove();

            log.info("Deleted Identity Provider '{}' from realm '{}'", alias, realm);
        } catch (Exception e) {
            log.error("Failed to delete Identity Provider '{}' from realm '{}'",
                    alias, realm, e);
            throw e; // let service decide whether to continue
        }
    }

}