package com.secufusion.iam.util;

import com.secufusion.iam.dto.CreateIdentityProviderRequest;
import com.secufusion.iam.exception.KeycloakOperationException;
import jakarta.ws.rs.client.Entity;
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
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

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

            // -------------------------------
            // 4️⃣ Return redirect URL
            // -------------------------------
            return buildAzureRedirectUrl(realm, dto.getAlias());

        } catch (Exception e) {
            throw new KeycloakOperationException("IDP_CREATE_FAILED", 500,
                    "Failed to create identity provider in realm " + realm, e);
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
        log.info("Updating KC user '{}' in realm '{}'", userId, realm);
        try {
            if (username == null || username.isBlank()) {
                throw new KeycloakOperationException("INVALID_INPUT", 400, "Username must not be blank");
            }

            UsersResource users = keycloak.realm(realm).users();
            UserResource userResource = users.get(userId);
            UserRepresentation rep;
            try {
                rep = userResource.toRepresentation();
                if (rep == null) {
                    throw new KeycloakOperationException("USER_NOT_FOUND", 404, "User not found in realm");
                }
            } catch (KeycloakOperationException e) {
                throw e;
            } catch (Exception e) {
                throw wrap("USER_RETRIEVE_FAILED", 500, "Error retrieving KC user " + userId + " in realm " + realm, e);
            }

            // Unique validation: ensure no other user has same username/email
            List<UserRepresentation> conflicts = findUsersByUsernameOrEmail(realm, username, email).stream()
                    .filter(u -> u.getId() != null && !u.getId().equals(userId))
                    .toList();
            if (!conflicts.isEmpty()) {
                log.warn("Unique constraint violation while updating user {} in realm {}: conflicts={}", userId, realm,
                        conflicts.stream().map(UserRepresentation::getId).toList());
                throw new KeycloakOperationException("USER_ALREADY_EXISTS", 409, "Another user with same username/email already exists in realm");
            }

            rep.setUsername(username);
            rep.setEmail(email);
            rep.setFirstName(firstName);
            rep.setLastName(lastName);
            userResource.update(rep);
            log.info("Updated KC user {} in realm {}", userId, realm);
        } catch (KeycloakOperationException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            log.error("Validation error updating KC user {} in realm {}: {}", userId, realm, e.getMessage(), e);
            throw new KeycloakOperationException("INVALID_INPUT", 400, e.getMessage());
        } catch (Exception e) {
            throw wrap("UPDATE_FAILED", 500, "Failed to update KC user " + userId + " in realm " + realm, e);
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

    public void assignRealmAdminRoleIfMissing(String realm, String userId) {

        log.info("Ensuring realm-admin role for user {} in realm {}", userId, realm);

        try {
            RealmResource rr = keycloak.realm(realm);

            // -------------------------------------------------
            // 1️⃣ Find realm-management client
            // -------------------------------------------------
            ClientRepresentation realmMgmtClient = rr.clients()
                    .findByClientId("realm-management")
                    .stream()
                    .findFirst()
                    .orElseThrow(() ->
                            new KeycloakOperationException(
                                    "REALM_MGMT_CLIENT_NOT_FOUND",
                                    500,
                                    "realm-management client not found in realm " + realm
                            )
                    );

            String clientId = realmMgmtClient.getId();

            // -------------------------------------------------
            // 2️⃣ Get realm-admin role representation
            // -------------------------------------------------
            RoleRepresentation realmAdminRole = rr.clients()
                    .get(clientId)
                    .roles()
                    .get("realm-admin")
                    .toRepresentation();

            // -------------------------------------------------
            // 3️⃣ Fetch already assigned client roles
            // -------------------------------------------------
            List<RoleRepresentation> assignedRoles =
                    rr.users()
                            .get(userId)
                            .roles()
                            .clientLevel(clientId)
                            .listAll();

            boolean alreadyAssigned = assignedRoles.stream()
                    .anyMatch(r -> r.getName().equals("realm-admin"));

            if (alreadyAssigned) {
                log.debug("ℹ️ realm-admin role already assigned. userId={}", userId);
                return;
            }

            // -------------------------------------------------
            // 4️⃣ Assign role (ONLY IF MISSING)
            // -------------------------------------------------
            rr.users()
                    .get(userId)
                    .roles()
                    .clientLevel(clientId)
                    .add(List.of(realmAdminRole));

            log.info("✔ realm-admin role assigned to user {}", userId);

        } catch (KeycloakOperationException e) {
            throw e;
        } catch (Exception e) {
            throw wrap(
                    "ASSIGN_REALM_ADMIN_FAILED",
                    500,
                    "Failed to ensure realm-admin role for user " + userId,
                    e
            );
        }
    }

}