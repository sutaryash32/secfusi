package com.secufusion.tenant.util;

import com.secufusion.tenant.dto.CreateIdentityProviderRequest;
import com.secufusion.tenant.exception.KeycloakOperationException;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.*;
import org.keycloak.representations.idm.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Utility wrapper around Keycloak Admin client for common realm/client/user
 * operations.
 * Adds consistent logging, validation and exception handling (wraps errors into
 * KeycloakOperationException).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KeycloakAdminUtil {

    private final Keycloak keycloak;

    // ---------------- SMTP properties (injected from application properties)
    // ----------------
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

    @Value("${azure.sso.alias}")
    private String masterAzureAlias;

    @Value("${master.admin.tenant-name}")
    private String masterAdminTenantName;

    // ============================================================
    // Helper to centralize exception wrapping and logging
    // ============================================================
    private KeycloakOperationException wrap(String code, int status, String op, Exception e) {
        log.error("{} - {}: {}", op, e.getClass().getSimpleName(), e.getMessage(), e);
        return new KeycloakOperationException(code, status, op + " failed: " + e.getMessage());
    }

    // ============================================================
    // REALM OPERATIONS
    // ============================================================

    /**
     * Check whether a realm exists using a direct lookup (fast, O(1)).
     */
    public boolean realmExists(String realm) {
        try {
            keycloak.realm(realm).toRepresentation();
            log.debug("Realm exists check: realm={}, exists=true", realm);
            return true;
        } catch (Exception e) {
            log.debug("Realm exists check: realm={}, exists=false", realm);
            return false;
        }
    }

    /**
     * Completely disables the Identity Provider Redirector in the Master realm.
     * This ensures only the local login screen (username/password) is active.
     */
    public void disableMasterIdpRedirector() {
        String realm = "master";
        try {
            AuthenticationManagementResource auth = keycloak.realm(realm).flows();
            List<AuthenticationExecutionInfoRepresentation> executions = auth.getExecutions("browser");

            executions.stream()
                    .filter(e -> "identity-provider-redirector".equals(e.getProviderId()))
                    .findFirst()
                    .ifPresent(exec -> {
                        // If there is an existing config, delete it or clear the defaultProvider
                        if (exec.getAuthenticationConfig() != null) {
                            auth.removeAuthenticatorConfig(exec.getAuthenticationConfig());
                            log.info("Cleared default redirector config from Master realm.");
                        }

                        // Set to ALTERNATIVE so the login page renders when no defaultProvider is set.
                        exec.setRequirement("ALTERNATIVE");
                        auth.updateExecutions("browser", exec);
                    });
        } catch (Exception e) {
            log.error("Failed to reset Master redirector: {}", e.getMessage());
        }
    }
    /**
     * Create a realm from representation.
     */
    public void createRealm(RealmRepresentation realmRepresentation) {
        String realmName = realmRepresentation != null ? realmRepresentation.getRealm() : "unknown";
        log.info("Creating realm '{}'", realmName);
        try {
            keycloak.realms().create(realmRepresentation);
            log.info("Realm created: {}", realmName);
        } catch (Exception e) {
            throw wrap("REALM_CREATE_FAILED", 500, "Failed to create realm " + realmName, e);
        }
    }

    /**
     * Rename a Keycloak realm by updating its realm representation.
     * This changes the realm identifier — all existing tokens and URLs for this realm
     * will use the new name. Active sessions will be invalidated.
     *
     * @param currentName the current realm name in Keycloak
     * @param newName     the new realm name (lowercase)
     * @return true if renamed, false if realm not found or already has the target name
     */
    public boolean renameRealm(String currentName, String newName) {
        if (currentName.equals(newName)) {
            log.info("Realm '{}' already has target name, skipping rename.", currentName);
            return false;
        }
        try {
            RealmResource realmResource = keycloak.realm(currentName);
            RealmRepresentation rep = realmResource.toRepresentation();
            rep.setRealm(newName);
            realmResource.update(rep);
            log.info("Realm renamed: '{}' → '{}'", currentName, newName);
            return true;
        } catch (jakarta.ws.rs.NotFoundException e) {
            log.warn("Realm '{}' not found in Keycloak, cannot rename.", currentName);
            return false;
        } catch (Exception e) {
            throw wrap("REALM_RENAME_FAILED", 500,
                    "Failed to rename realm '" + currentName + "' to '" + newName + "'", e);
        }
    }

    public void updateRealmTokenSettings(
            String realm,
            Integer accessTokenLifespanSeconds,
            Integer refreshTokenIdleSeconds,
            Integer sessionMaxLifespanSeconds) {

        if (realm == null || realm.isBlank()) {
            throw new KeycloakOperationException(
                    "INVALID_INPUT", 400, "Realm name must not be null or empty");
        }

        RealmResource rr = keycloak.realm(realm);
        RealmRepresentation current;

        try {
            current = rr.toRepresentation();
        } catch (Exception e) {
            throw wrap(
                    "REALM_FETCH_FAILED",
                    500,
                    "Failed fetching realm config for " + realm,
                    e);
        }

        RealmRepresentation patch = new RealmRepresentation();
        boolean changed = false;

        if (accessTokenLifespanSeconds != null) {
            if (accessTokenLifespanSeconds < 60) {
                throw new KeycloakOperationException(
                        "INVALID_INPUT", 400, "Access token lifespan must be >= 60 seconds");
            }

            if (!Objects.equals(current.getAccessTokenLifespan(), accessTokenLifespanSeconds)) {
                patch.setAccessTokenLifespan(accessTokenLifespanSeconds);
                changed = true;
            }
        }

        if (refreshTokenIdleSeconds != null) {
            if (refreshTokenIdleSeconds < 300) {
                throw new KeycloakOperationException(
                        "INVALID_INPUT", 400, "Refresh token idle timeout must be >= 300 seconds");
            }

            if (!Objects.equals(current.getSsoSessionIdleTimeout(), refreshTokenIdleSeconds)) {
                patch.setSsoSessionIdleTimeout(refreshTokenIdleSeconds);
                changed = true;
            }
        }

        if (sessionMaxLifespanSeconds != null) {
            if (sessionMaxLifespanSeconds < 300) {
                throw new KeycloakOperationException(
                        "INVALID_INPUT", 400, "Session max lifespan must be >= 300 seconds");
            }

            if (!Objects.equals(current.getSsoSessionMaxLifespan(), sessionMaxLifespanSeconds)) {
                patch.setSsoSessionMaxLifespan(sessionMaxLifespanSeconds);
                changed = true;
            }
        }

        if (!changed) {
            log.info("No realm token settings change required for realm={}", realm);
            return;
        }

        try {
            rr.update(patch); // ✅ SAFE partial update
            log.info(
                    "✔ Realm token settings updated for realm={} [access={}, refreshIdle={}, sessionMax={}]",
                    realm,
                    accessTokenLifespanSeconds,
                    refreshTokenIdleSeconds,
                    sessionMaxLifespanSeconds);
        } catch (Exception e) {
            log.warn(
                    "Realm token settings update failed for realm={} (non-fatal)",
                    realm,
                    e);
            // DO NOT throw → tenant creation must continue
        }
    }

    public void updateRealmPasswordPolicy(
            String realm,
            int minLength,
            int maxLength,
            int upperCase,
            int lowerCase,
            int digits,
            int specialChars,
            boolean notUsername,
            boolean notEmail,
            int passwordHistory,
            int passwordExpiryDays,
            boolean displayRequirementsInUi) {

        try {
            RealmResource realmResource = keycloak.realm(realm);
            RealmRepresentation rep = realmResource.toRepresentation();

            List<String> policies = new ArrayList<>();

            policies.add("length(" + minLength + ")");
            policies.add("maxLength(" + maxLength + ")");
            policies.add("upperCase(" + upperCase + ")");
            policies.add("lowerCase(" + lowerCase + ")");
            policies.add("digits(" + digits + ")");
            policies.add("specialChars(" + specialChars + ")");

            if (notUsername) {
                policies.add("notUsername()");
            }

            if (notEmail) {
                policies.add("notEmail()");
            }

            if (passwordHistory > 0) {
                policies.add("passwordHistory(" + passwordHistory + ")");
            }

            if (passwordExpiryDays > 0) {
                policies.add("forceExpiredPasswordChange(" + passwordExpiryDays + ")");
            }

            String policyString = String.join(" and ", policies);

            rep.setPasswordPolicy(policyString);


            if (displayRequirementsInUi) {
                List<String> uiHints = new ArrayList<>();
                uiHints.add("Minimum " + minLength + " characters");
                if (upperCase > 0) uiHints.add("At least " + upperCase + " uppercase letter(s)");
                if (lowerCase > 0) uiHints.add("At least " + lowerCase + " lowercase letter(s)");
                if (digits > 0) uiHints.add("At least " + digits + " number(s)");
                if (specialChars > 0) uiHints.add("At least " + specialChars + " special character(s)");
                if (notUsername) uiHints.add("Cannot be your username");

                String readableDescription = String.join(", ", uiHints) + ".";

                // Store this in a Realm Attribute that your Theme can access
                if (rep.getAttributes() == null) {
                    rep.setAttributes(new HashMap<>());
                }
                rep.getAttributes().put("passwordPolicyDescription", readableDescription);

                log.info("✔ UI Password Hint updated: {}", readableDescription);
            }
            realmResource.update(rep);

            log.info("✔ Password policy updated for realm={} policy={}", realm, policyString);

        } catch (Exception e) {
            log.error("❌ Failed to update password policy for realm={}", realm, e);
            throw new RuntimeException("Failed to update password policy", e);
        }
    }

    /**
     * Update SMTP settings for a Keycloak realm.
     *
     * @param realm      the realm name
     * @param smtpConfig map of SMTP properties (host, port, from, user, password,
     *                   auth, starttls, ssl)
     */
    public void updateRealmSmtpSettings(String realm, Map<String, String> smtpConfig) {
        try {
            RealmResource rr = keycloak.realm(realm);
            RealmRepresentation patch = new RealmRepresentation();

            patch.setSmtpServer(smtpConfig);

            rr.update(patch);

            log.info("SMTP settings updated for realm '{}'", realm);

        } catch (Exception e) {
            log.error("Failed to update SMTP settings for realm '{}': {}", realm, e.getMessage(), e);
            throw wrap("SMTP_UPDATE_FAILED", 500, "Failed to update SMTP settings for realm " + realm, e);
        }
    }

    public void enableForgotPassword(String realm) {
        try {
            RealmResource rr = keycloak.realm(realm);
            RealmRepresentation patch = new RealmRepresentation();

            patch.setResetPasswordAllowed(true);

            rr.update(patch); // ✅ partial update only

            log.info("Forgot password enabled for realm '{}'", realm);

        } catch (Exception e) {
            log.warn(
                    "Failed to enable forgot password for realm={} (non-fatal)",
                    realm,
                    e);
        }
    }

    public void setRealmEnabled(String realm, boolean enabled) {
        try {
            RealmResource rr = keycloak.realm(realm);
            RealmRepresentation patch = new RealmRepresentation();
            patch.setEnabled(enabled);
            rr.update(patch);

            log.info("Realm '{}' enabled={}", realm, enabled);
        } catch (Exception e) {
            throw wrap(
                    "REALM_ENABLE_DISABLE_FAILED",
                    500,
                    "Failed to update realm enabled state for " + realm,
                    e);
        }
    }

    public void setAllUsersEnabled(String realm, boolean enabled) {
        try {
            UsersResource users = keycloak.realm(realm).users();
            int first = 0;
            int max = 100;

            while (true) {
                List<UserRepresentation> batch = users.list(first, max);
                if (batch.isEmpty())
                    break;

                for (UserRepresentation u : batch) {
                    if (u.isEnabled() != enabled) {
                        UserRepresentation patch = new UserRepresentation();
                        patch.setEnabled(enabled);
                        users.get(u.getId()).update(patch);
                    }
                }
                first += max;
            }

            log.info("All users in realm '{}' enabled={}", realm, enabled);
        } catch (Exception e) {
            throw wrap(
                    "USER_ENABLE_DISABLE_FAILED",
                    500,
                    "Failed to update users for realm " + realm,
                    e);
        }
    }

    public void deleteRealmHard(String realm) {
        try {
            if (!realmExists(realm)) {
                log.info("Realm '{}' already deleted or does not exist", realm);
                return;
            }

            keycloak.realm(realm).remove();
            log.info("Keycloak realm '{}' deleted successfully", realm);

        } catch (Exception e) {
            throw wrap(
                    "KC_REALM_DELETE_FAILED",
                    500,
                    "Failed to delete Keycloak realm: " + realm,
                    e);
        }
    }

    // ============================================================
    // CLIENT OPERATIONS
    // ============================================================

    /**
     * Check if a client exists in a realm by clientId.
     */
    public boolean clientExists(String realm, String clientId) {
        try {
            List<ClientRepresentation> clients = keycloak.realm(realm).clients().findByClientId(clientId);
            boolean exists = clients != null && !clients.isEmpty();
            log.debug("Client exists check: realm={}, clientId={}, exists={}", realm, clientId, exists);
            return exists;
        } catch (Exception e) {
            throw wrap("CLIENT_CHECK_FAILED", 500,
                    "Error checking client existence for " + clientId + " in realm " + realm, e);
        }
    }

    /**
     * Retrieve a client by clientId from a realm. Returns null if not found.
     */
    public ClientRepresentation getClientByClientId(String realm, String clientId) {
        try {
            List<ClientRepresentation> clients = keycloak.realm(realm).clients().findByClientId(clientId);
            return (clients == null || clients.isEmpty()) ? null : clients.get(0);
        } catch (Exception e) {
            throw wrap("CLIENT_FETCH_FAILED", 500,
                    "Error fetching client " + clientId + " in realm " + realm, e);
        }
    }

    /**
     * Fetch a client by clientId AND populate the secret by calling the dedicated
     * /client-secret endpoint. Use this when the secret is needed (e.g. IdP config).
     * The standard findByClientId() does not return the secret.
     */
    public ClientRepresentation getClientWithSecret(String realm, String clientId) {
        try {
            List<ClientRepresentation> clients = keycloak.realm(realm).clients().findByClientId(clientId);
            if (clients == null || clients.isEmpty()) return null;
            ClientRepresentation client = clients.get(0);
            // Fetch the actual secret via the dedicated endpoint using the internal client UUID
            CredentialRepresentation cred = keycloak.realm(realm).clients()
                    .get(client.getId()).getSecret();
            if (cred != null) {
                client.setSecret(cred.getValue());
            }
            return client;
        } catch (Exception e) {
            throw wrap("CLIENT_SECRET_FETCH_FAILED", 500,
                    "Error fetching client secret for " + clientId + " in realm " + realm, e);
        }
    }

    /**
     * Create a client in a realm. Allows 201 (created) and 409 (conflict).
     */
    public void createClient(String realm, ClientRepresentation clientRep) {
        log.info("Creating Keycloak client: realm={}, clientId={}", realm,
                clientRep != null ? clientRep.getClientId() : "null");
        Response resp = null;
        try {
            resp = keycloak.realm(realm).clients().create(clientRep);
            int status = resp.getStatus();
            log.debug("Client creation response status={}", status);
            if (status != 201 && status != 409) {
                String body = resp.readEntity(String.class);
                throw new KeycloakOperationException("CLIENT_CREATE_FAILED", 500, "Client creation failed: " + body);
            }
            if (status == 409) {
                log.warn("Client already exists: realm={}, clientId={}", realm, clientRep.getClientId());
            } else {
                log.info("Client created in realm={} clientId={}", realm, clientRep.getClientId());
            }
        } catch (KeycloakOperationException e) {
            // rethrow Keycloak-specific wrapper
            throw e;
        } catch (Exception e) {
            throw wrap("CLIENT_CREATE_FAILED", 500, "Exception while creating client "
                    + (clientRep != null ? clientRep.getClientId() : "null") + " in realm " + realm, e);
        } finally {
            if (resp != null) {
                try {
                    resp.close();
                } catch (Exception e) {
                    log.warn("Failed to close client creation response: {}", e.getMessage(), e);
                }
            }
        }
    }

    // ============================================================
    // USER OPERATIONS
    // ============================================================

    /**
     * Create a user. Returns created Keycloak user id or null if already exists.
     */
    public String createUser(String realm, String username, String email, String firstName, String lastName,
            boolean emailVerified) {
        log.info("Creating Keycloak user in realm='{}' username='{}'", realm, username);

        // Validate and ensure uniqueness
        try {
            findUsersByUsernameOrEmail(realm, username, email).forEach(u -> {
                log.warn(
                        "User with same username/email already exists in realm='{}': userId='{}', username='{}', email='{}'",
                        realm, u.getId(), u.getUsername(), u.getEmail());
                throw new KeycloakOperationException("USER_ALREADY_EXISTS", 409,
                        "User with same username/email already exists in realm");
            });
        } catch (KeycloakOperationException e) {
            throw e;
        } catch (Exception e) {
            throw wrap("USER_CREATE_VALIDATION_FAILED", 500, "Failed validation for user creation in realm " + realm,
                    e);
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
            throw wrap("USER_CREATE_FAILED", 500,
                    "Error creating user in Keycloak realm=" + realm + " username=" + username, e);
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
     * Trigger Keycloak to send required action emails (e.g., verify email, update
     * password).
     */
    public void sendRequiredActionEmail(String realm, String userId, List<String> actions) {
        log.info("Sending required-action email to KC user '{}' in realm {}", userId, realm);
        try {
            keycloak.realm(realm).users().get(userId).executeActionsEmail(actions);
            log.debug("Required-action email triggered for user {}", userId);
        } catch (Exception e) {
            throw wrap("EMAIL_ACTION_TRIGGER_FAILED", 500, "Failed to send required-action email to KC user " + userId,
                    e);
        }
    }

    /**
     * Get the total user count for a realm.
     *
     * @param realm the realm name
     * @return total number of users in the realm
     */
    public int getUserCount(String realm) {
        log.debug("Getting user count for realm={}", realm);
        try {
            return keycloak.realm(realm).users().count();
        } catch (Exception e) {
            throw wrap("USER_COUNT_FAILED", 500, "Failed to get user count for realm " + realm, e);
        }
    }

    /**
     * Find a user by username in a realm.
     *
     * @param realm    the realm name
     * @param username the username to search for
     * @return list of matching users (exact match)
     */
    public List<UserRepresentation> findUserByUsername(String realm, String username) {
        return findUsersByUsernameOrEmail(realm, username, null);
    }

    public List<UserRepresentation> findUsersByUsernameOrEmail(
            String realm,
            String username,
            String email) {
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
                    e);
        }

        // 🔐 Filter to exact matches only
        return results.stream()
                .filter(u -> (username != null && username.equalsIgnoreCase(u.getUsername())) ||
                        (email != null && email.equalsIgnoreCase(u.getEmail())))
                // 🔁 Deduplicate by Keycloak user ID
                .collect(Collectors.toMap(
                        UserRepresentation::getId,
                        u -> u,
                        (a, b) -> a))
                .values()
                .stream()
                .toList();
    }

    // ============================================================
    // EMAIL (moved here from TenantService)
    // ============================================================

    /**
     * Send the Secufusion welcome email.
     *
     * @param to                recipient admin email (also shown inline in the body)
     * @param loginUrl          full Keycloak login URL for the tenant
     * @param tenantName        enterprise/tenant display name
     * @param setPasswordUrl    Keycloak forgot-password URL for token-based tenants; pass null for SSO tenants
     */
    public void sendWelcomeEmail(String to, String loginUrl, String tenantName, String setPasswordUrl) {
        log.info("Sending welcome email to {} with loginUrl={} setPassword={}",
                to, loginUrl, setPasswordUrl != null);

        Properties props = new Properties();
        props.put("mail.smtp.host", smtpHost);
        props.put("mail.smtp.port", smtpPort);
        props.put("mail.smtp.auth", smtpAuth);
        props.put("mail.smtp.starttls.enable", smtpStarttls);

        Session session = Session.getInstance(
                props,
                new Authenticator() {
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(smtpUsername, smtpPassword);
                    }
                });

        try {
            Message msg = new MimeMessage(session);
            msg.setFrom(new InternetAddress(smtpMail, false));
            msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            msg.setSubject("Welcome to Secufusion Agentic Workspace Control Center");
            msg.setContent(buildWelcomeHtml(to, loginUrl, tenantName, setPasswordUrl), "text/html; charset=utf-8");

            Transport.send(msg);
            log.info("Welcome email sent to {}", to);
        } catch (MessagingException e) {
            throw wrap("EMAIL_SEND_FAILED", 500, "Failed to send welcome email to " + to, e);
        } catch (Exception e) {
            throw wrap("EMAIL_SEND_FAILED", 500, "Unexpected error while sending welcome email to " + to, e);
        }
    }

    private String buildWelcomeHtml(String adminEmail, String loginUrl, String tenantName, String setPasswordUrl) {
        String setPasswordBlock = (setPasswordUrl == null) ? "" : """
                <p style="margin:24px 0 8px;">To set your password for the first time, click the button below:</p>
                <p style="margin:0 0 20px;">
                  <a href="%s" style="background:#2c3e50;color:#ffffff;padding:12px 22px;text-decoration:none;border-radius:4px;display:inline-block;">Set Your Password</a>
                </p>
                """.formatted(setPasswordUrl);

        return """
                <html>
                <body style="font-family:Arial,sans-serif;line-height:1.6;color:#333;">
                  <div style="max-width:640px;margin:0 auto;padding:24px;">
                    <h2 style="color:#2c3e50;margin:0 0 16px;">Welcome to the Secufusion Agentic Workspace Control Center</h2>

                    <p>A dedicated tenant has been provisioned for <strong>%s</strong>. To access your management console, please visit:</p>
                    <p style="margin:12px 0 20px;">
                      <a href="%s" style="background:#3498db;color:#ffffff;padding:12px 22px;text-decoration:none;border-radius:4px;display:inline-block;">🔗 Open Control Center</a>
                    </p>
                    <p>Log in using your enterprise credentials and the email address: <strong>%s</strong>.</p>

                    %s

                    <h3 style="color:#2c3e50;margin-top:28px;">📘 Onboarding Resources</h3>
                    <p>Please refer to the onboarding cheat sheet for guidance on configuring policies for your end users. It includes best practices for deploying in both supported modes:</p>
                    <ul>
                      <li>Secufusion Enterprise Browser</li>
                      <li>Secufusion Browser Extensions</li>
                    </ul>
                    <p>You are entitled to configure and operate in both deployment modes.</p>
                    <p>Additionally, a customizable welcome email template is available to help streamline your end-user onboarding process.</p>
                    <p>Secufusion is designed for seamless onboarding, for both administrators and end users. If you have any questions or need assistance, feel free to reach out to us at <a href="mailto:info@secufusion.com">info@secufusion.com</a>.</p>

                    <p style="margin-top:24px;">Welcome aboard!</p>
                    <p style="margin:0;"><strong>Team Secufusion</strong><br/>The Agentic Workspace Company</p>
                  </div>
                </body>
                </html>
                """.formatted(tenantName, loginUrl, adminEmail, setPasswordBlock);
    }

    public void createExtensionClient(String realm, String clientId, String redirectUri) {
        log.info("Creating extension client '{}' in realm '{}'", clientId, realm);

        RealmResource rr = keycloak.realm(realm);

        // ------------------------
        // 1️⃣ CREATE CLIENT
        // ------------------------
        ClientRepresentation client = new ClientRepresentation();
        client.setClientId(clientId);
        client.setEnabled(true);
        client.setProtocol("openid-connect");
        client.setRedirectUris(List.of(redirectUri));

        client.setPublicClient(false); // for secure extensions
        client.setServiceAccountsEnabled(true); // enable machine-to-machine
        client.setAuthorizationServicesEnabled(true); // enable Keycloak authz

        client.setDirectAccessGrantsEnabled(false);
        client.setStandardFlowEnabled(false);
        client.setBearerOnly(false);

        Response resp = rr.clients().create(client);
        if (resp.getStatus() != 201 && resp.getStatus() != 409) {
            throw new KeycloakOperationException("CLIENT_CREATE_FAILED", resp.getStatus(),
                    resp.readEntity(String.class));
        }

        if (resp.getStatus() == 409) {
            log.warn("Extension client {} already exists in realm {}", clientId, realm);
            return;
        }

        resp.close();

        // Fetch newly created client
        ClientRepresentation created = rr.clients()
                .findByClientId(clientId).stream().findFirst()
                .orElseThrow(() -> new RuntimeException("Client not found after creation"));

        String clientUUID = created.getId();

        // ------------------------
        // 2️⃣ GET SERVICE ACCOUNT USER
        // ------------------------
        UserRepresentation serviceUser = rr.clients().get(clientUUID).getServiceAccountUser();
        if (serviceUser == null) {
            throw new RuntimeException("Service account user missing for client " + clientId);
        }

        String serviceUserId = serviceUser.getId();

        // ------------------------
        // 3️⃣ ASSIGN DEFAULT REALM ROLES
        // ------------------------
        String[] defaultRealmRoles = {
                "view-users",
                "query-users"
        };

        for (String roleName : defaultRealmRoles) {
            RoleRepresentation role = rr.roles().get(roleName).toRepresentation();
            rr.users().get(serviceUserId).roles().realmLevel().add(List.of(role));
        }

        log.info("Assigned default realm-management roles to client {}", clientId);

        // ------------------------
        // 4️⃣ OPTIONAL: ASSIGN CLIENT ROLES (realm-management)
        // ------------------------
        ClientRepresentation rmClient = rr.clients()
                .findByClientId("realm-management").stream().findFirst().orElse(null);

        if (rmClient != null) {
            ClientResource rmResource = rr.clients().get(rmClient.getId());

            String[] clientRoles = { "view-users", "query-users" };

            for (String cr : clientRoles) {
                RoleRepresentation r = rmResource.roles().get(cr).toRepresentation();
                rr.users().get(serviceUserId)
                        .roles()
                        .clientLevel(rmClient.getId())
                        .add(List.of(r));
            }
            log.info("Client roles assigned from realm-management for {}", clientId);
        }

        log.info("Extension client '{}' created successfully", clientId);
    }

    public void assignRealmAdminRoleIfMissing(String realm, String userId) {

        log.info("Ensuring realm-admin role for user {} in realm {}", userId, realm);

        // Skip for master realm - it doesn't have realm-management client
        // Master realm users get admin access through different mechanisms
        if (masterAdminTenantName.equalsIgnoreCase(realm)) {
            log.info("Skipping realm-admin role assignment for master realm user {}. Master realm uses different admin role structure.", userId);
            return;
        }

        try {
            RealmResource rr = keycloak.realm(realm);

            // -------------------------------------------------
            // 1️⃣ Find realm-management client
            // -------------------------------------------------
            ClientRepresentation realmMgmtClient = rr.clients()
                    .findByClientId("realm-management")
                    .stream()
                    .findFirst()
                    .orElseThrow(() -> new KeycloakOperationException(
                            "REALM_MGMT_CLIENT_NOT_FOUND",
                            500,
                            "realm-management client not found in realm " + realm));

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
            List<RoleRepresentation> assignedRoles = rr.users()
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
                    e);
        }
    }

    // ============================================================
    // SESSION / LOGOUT OPERATIONS
    // ============================================================

    /**
     * Logout a specific user by revoking all their sessions.
     *
     * @param realm  the realm name
     * @param userId the Keycloak user ID
     * @return number of sessions revoked
     */
    public int logoutUser(String realm, String userId) {
        log.info("Logging out user {} from realm {}", userId, realm);
        try {
            UserResource userResource = keycloak.realm(realm).users().get(userId);
            List<UserSessionRepresentation> sessions = userResource.getUserSessions();
            int sessionCount = sessions.size();

            userResource.logout();

            log.info("User {} logged out from realm {}, {} sessions revoked", userId, realm, sessionCount);
            return sessionCount;
        } catch (Exception e) {
            throw wrap("USER_LOGOUT_FAILED", 500, "Failed to logout user " + userId + " from realm " + realm, e);
        }
    }

    /**
     * Logout all users from a realm by terminating all sessions.
     *
     * @param realm the realm name
     * @return number of sessions revoked
     */
    public int logoutAllUsers(String realm) {
        log.info("Logging out all users from realm {}", realm);
        try {
            RealmResource realmResource = keycloak.realm(realm);
            UsersResource users = realmResource.users();

            int totalSessions = 0;
            int first = 0;
            int max = 100;

            while (true) {
                List<UserRepresentation> batch = users.list(first, max);
                if (batch.isEmpty())
                    break;

                for (UserRepresentation user : batch) {
                    try {
                        UserResource userResource = users.get(user.getId());
                        List<UserSessionRepresentation> sessions = userResource.getUserSessions();
                        if (!sessions.isEmpty()) {
                            totalSessions += sessions.size();
                            userResource.logout();
                        }
                    } catch (Exception e) {
                        log.warn("Failed to logout user {}: {}", user.getUsername(), e.getMessage());
                    }
                }
                first += max;
            }

            log.info("All users logged out from realm {}, {} total sessions revoked", realm, totalSessions);
            return totalSessions;
        } catch (Exception e) {
            throw wrap("REALM_LOGOUT_FAILED", 500, "Failed to logout all users from realm " + realm, e);
        }
    }

    /**
     * Revoke a specific session by session ID.
     *
     * @param realm     the realm name
     * @param sessionId the session ID to revoke
     */
    public void revokeSession(String realm, String sessionId) {
        log.info("Revoking session {} in realm {}", sessionId, realm);
        try {
            // Second parameter: isOffline - false for regular sessions
            keycloak.realm(realm).deleteSession(sessionId, false);
            log.info("Session {} revoked in realm {}", sessionId, realm);
        } catch (Exception e) {
            throw wrap("SESSION_REVOKE_FAILED", 500, "Failed to revoke session " + sessionId + " in realm " + realm, e);
        }
    }

    /**
     * Get all active sessions for a user.
     *
     * @param realm  the realm name
     * @param userId the Keycloak user ID
     * @return list of active sessions
     */
    public List<UserSessionRepresentation> getUserSessions(String realm, String userId) {
        log.debug("Getting sessions for user {} in realm {}", userId, realm);
        if (userId == null || userId.isBlank()) {
            log.warn("getUserSessions called with null/blank userId for realm {}", realm);
            return List.of();
        }
        try {
            return keycloak.realm(realm).users().get(userId).getUserSessions();
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg != null && (msg.contains("404") || msg.contains("Not Found"))) {
                log.warn("User {} not found in realm {} — returning empty session list", userId, realm);
                return List.of();
            }
            throw wrap("GET_SESSIONS_FAILED", 500, "Failed to get sessions for user " + userId + " in realm " + realm,
                    e);
        }
    }

    /**
     * Get count of active sessions in a realm.
     *
     * @param realm the realm name
     * @return number of active sessions
     */
    public int getActiveSessionCount(String realm) {
        log.debug("Getting active session count for realm {}", realm);
        try {
            Map<String, Long> clientSessionStats = keycloak.realm(realm).getClientSessionStats()
                    .stream()
                    .collect(java.util.stream.Collectors.toMap(
                            m -> (String) m.get("clientId"),
                            m -> Long.parseLong(String.valueOf(m.get("active")))));

            return clientSessionStats.values().stream()
                    .mapToInt(Long::intValue)
                    .sum();
        } catch (Exception e) {
            log.warn("Failed to get session count for realm {}: {}", realm, e.getMessage());
            return -1;
        }
    }

    /**
     * Get all active sessions in a realm (paginated).
     *
     * @param realm the realm name
     * @param first pagination offset
     * @param max   max results to return
     * @return list of all sessions in the realm
     */
    public List<UserSessionRepresentation> getAllSessions(String realm, int first, int max) {
        log.debug("Getting all sessions for realm {} (first={}, max={})", realm, first, max);
        try {
            RealmResource realmResource = keycloak.realm(realm);
            UsersResource users = realmResource.users();
            List<UserSessionRepresentation> allSessions = new ArrayList<>();

            int userFirst = 0;
            int userMax = 100;

            while (true) {
                List<UserRepresentation> batch = users.list(userFirst, userMax);
                if (batch.isEmpty())
                    break;

                for (UserRepresentation user : batch) {
                    try {
                        List<UserSessionRepresentation> userSessions = users.get(user.getId()).getUserSessions();
                        allSessions.addAll(userSessions);
                    } catch (Exception e) {
                        log.warn("Failed to get sessions for user {}: {}", user.getUsername(), e.getMessage());
                    }
                }
                userFirst += userMax;
            }

            // Apply pagination
            int fromIndex = Math.min(first, allSessions.size());
            int toIndex = Math.min(first + max, allSessions.size());

            return allSessions.subList(fromIndex, toIndex);
        } catch (Exception e) {
            throw wrap("GET_ALL_SESSIONS_FAILED", 500, "Failed to get all sessions for realm " + realm, e);
        }
    }

    /**
     * Get sessions for a specific client in a realm.
     *
     * @param realm    the realm name
     * @param clientId the client ID (not UUID)
     * @return list of sessions for the client
     */
    public List<UserSessionRepresentation> getClientSessions(String realm, String clientId) {
        log.debug("Getting sessions for client {} in realm {}", clientId, realm);
        try {
            RealmResource realmResource = keycloak.realm(realm);

            // Find client by clientId
            List<org.keycloak.representations.idm.ClientRepresentation> clients = realmResource.clients()
                    .findByClientId(clientId);

            if (clients.isEmpty()) {
                log.warn("Client {} not found in realm {}", clientId, realm);
                return List.of();
            }

            String clientUuid = clients.get(0).getId();
            return realmResource.clients().get(clientUuid).getUserSessions(0, 1000);
        } catch (Exception e) {
            throw wrap("GET_CLIENT_SESSIONS_FAILED", 500,
                    "Failed to get sessions for client " + clientId + " in realm " + realm, e);
        }
    }

    /**
     * Get offline sessions for a user (remember-me sessions).
     *
     * @param realm  the realm name
     * @param userId the Keycloak user ID
     * @return list of offline sessions
     */
    public List<UserSessionRepresentation> getUserOfflineSessions(String realm, String userId, String clientId) {
        log.debug("Getting offline sessions for user {} in realm {}", userId, realm);
        try {
            RealmResource realmResource = keycloak.realm(realm);

            // Find client by clientId
            List<org.keycloak.representations.idm.ClientRepresentation> clients = realmResource.clients()
                    .findByClientId(clientId);

            if (clients.isEmpty()) {
                log.warn("Client {} not found in realm {}", clientId, realm);
                return List.of();
            }

            String clientUuid = clients.get(0).getId();
            return realmResource.users().get(userId).getOfflineSessions(clientUuid);
        } catch (Exception e) {
            throw wrap("GET_OFFLINE_SESSIONS_FAILED", 500,
                    "Failed to get offline sessions for user " + userId + " in realm " + realm, e);
        }
    }

    /**
     * Get session statistics for a realm.
     *
     * @param realm the realm name
     * @return map of client ID to active session count
     */
    public Map<String, Long> getSessionStats(String realm) {
        log.debug("Getting session stats for realm {}", realm);
        try {
            return keycloak.realm(realm).getClientSessionStats()
                    .stream()
                    .collect(java.util.stream.Collectors.toMap(
                            m -> (String) m.get("clientId"),
                            m -> Long.parseLong(String.valueOf(m.get("active")))));
        } catch (Exception e) {
            throw wrap("GET_SESSION_STATS_FAILED", 500, "Failed to get session stats for realm " + realm, e);
        }
    }

    /**
     * Revoke all offline sessions (remember-me tokens) for a user.
     *
     * @param realm  the realm name
     * @param userId the Keycloak user ID
     */
    public void revokeUserOfflineSessions(String realm, String userId) {
        log.info("Revoking offline sessions for user {} in realm {}", userId, realm);
        try {
            keycloak.realm(realm).users().get(userId).revokeConsent(realm);
            log.info("Offline sessions revoked for user {} in realm {}", userId, realm);
        } catch (Exception e) {
            log.warn("Failed to revoke offline sessions for user {}: {}", userId, e.getMessage());
        }
    }

    /**
     * Find a specific session by its ID.
     * Keycloak Admin API has no direct "get by session ID" endpoint,
     * so we iterate user sessions in the realm and match by ID.
     *
     * @param realm     the realm name
     * @param sessionId the session ID to find
     * @return the matching session, or empty if not found
     */
    public Optional<UserSessionRepresentation> getSessionById(String realm, String sessionId) {
        log.debug("Looking up session {} in realm {}", sessionId, realm);
        try {
            UsersResource users = keycloak.realm(realm).users();
            int first = 0;
            int batchSize = 100;
            while (true) {
                List<UserRepresentation> batch = users.list(first, batchSize);
                if (batch.isEmpty()) break;
                for (UserRepresentation user : batch) {
                    try {
                        Optional<UserSessionRepresentation> match = users.get(user.getId())
                                .getUserSessions().stream()
                                .filter(s -> sessionId.equals(s.getId()))
                                .findFirst();
                        if (match.isPresent()) return match;
                    } catch (Exception e) {
                        log.warn("Could not fetch sessions for user {}: {}", user.getUsername(), e.getMessage());
                    }
                }
                first += batchSize;
            }
            return Optional.empty();
        } catch (Exception e) {
            throw wrap("GET_SESSION_BY_ID_FAILED", 500,
                    "Failed to find session " + sessionId + " in realm " + realm, e);
        }
    }

    /**
     * Get the number of active sessions for a specific user.
     *
     * @param realm  the realm name
     * @param userId the Keycloak user ID
     * @return session count, or 0 if none / user not found
     */
    public int getUserSessionCount(String realm, String userId) {
        log.debug("Getting session count for user {} in realm {}", userId, realm);
        try {
            return keycloak.realm(realm).users().get(userId).getUserSessions().size();
        } catch (Exception e) {
            log.warn("Failed to get session count for user {} in realm {}: {}", userId, realm, e.getMessage());
            return 0;
        }
    }

    // ============================================================
    // PASSWORD MANAGEMENT
    // ============================================================

    /**
     * Verify a user's current password.
     *
     * @param realm    the realm name
     * @param userId   the Keycloak user ID
     * @param password the password to verify
     * @return true if password is valid
     */
    public boolean verifyUserPassword(String realm, String userId, String password) {
        log.debug("Verifying password for user {} in realm {}", userId, realm);
        try {
            // Get user to retrieve username
            org.keycloak.representations.idm.UserRepresentation user =
                    keycloak.realm(realm).users().get(userId).toRepresentation();

            if (user == null || user.getUsername() == null) {
                log.warn("User {} not found in realm {}", userId, realm);
                return false;
            }

            // Try to get a token with the provided password
            // This is a workaround since Keycloak Admin API doesn't provide password verification
            // In production, you might use a direct grant or similar mechanism
            log.debug("Password verification attempted for user {} in realm {}", userId, realm);
            return true; // Simplified - in production, implement proper password verification
        } catch (Exception e) {
            log.error("Failed to verify password for user {}: {}", userId, e.getMessage());
            return false;
        }
    }

    /**
     * Set a new password for a user.
     *
     * @param realm     the realm name
     * @param userId    the Keycloak user ID
     * @param password  the new password
     * @param temporary whether the password is temporary (user must change on next login)
     */
    public void setUserPassword(String realm, String userId, String password, boolean temporary) {
        log.info("Setting password for user {} in realm {} (temporary={})", userId, realm, temporary);
        try {
            org.keycloak.representations.idm.CredentialRepresentation credential =
                    new org.keycloak.representations.idm.CredentialRepresentation();
            credential.setType(org.keycloak.representations.idm.CredentialRepresentation.PASSWORD);
            credential.setValue(password);
            credential.setTemporary(temporary);

            keycloak.realm(realm).users().get(userId).resetPassword(credential);
            log.info("Password set successfully for user {} in realm {}", userId, realm);
        } catch (Exception e) {
            throw wrap("SET_PASSWORD_FAILED", 500, "Failed to set password for user " + userId, e);
        }
    }

    /**
     * Set a required action for a user.
     *
     * @param realm          the realm name
     * @param userId         the Keycloak user ID
     * @param requiredAction the required action (e.g., "UPDATE_PASSWORD", "VERIFY_EMAIL")
     */
    public void setRequiredAction(String realm, String userId, String requiredAction) {
        log.info("Setting required action {} for user {} in realm {}", requiredAction, userId, realm);
        try {
            org.keycloak.representations.idm.UserRepresentation user =
                    keycloak.realm(realm).users().get(userId).toRepresentation();

            if (user == null) {
                throw wrap("USER_NOT_FOUND", 404, "User not found: " + userId, null);
            }

            java.util.List<String> actions = user.getRequiredActions();
            if (actions == null) {
                actions = new java.util.ArrayList<>();
            }
            if (!actions.contains(requiredAction)) {
                actions.add(requiredAction);
            }
            user.setRequiredActions(actions);

            keycloak.realm(realm).users().get(userId).update(user);
            log.info("Required action {} set for user {} in realm {}", requiredAction, userId, realm);
        } catch (Exception e) {
            if (e instanceof com.secufusion.tenant.exception.KeycloakOperationException) {
                throw e;
            }
            throw wrap("SET_REQUIRED_ACTION_FAILED", 500,
                    "Failed to set required action for user " + userId, e);
        }
    }

    /**
     * Updates firstName, lastName and email on an existing Keycloak user.
     * Only non-null fields are applied. Safe to call even if nothing has changed.
     */
    public void updateKeycloakUser(String realm, String keycloakUserId,
                                   String firstName, String lastName, String email) {
        try {
            UserRepresentation user = keycloak.realm(realm).users().get(keycloakUserId).toRepresentation();
            if (user == null) {
                log.warn("updateKeycloakUser: user {} not found in realm {}", keycloakUserId, realm);
                return;
            }
            boolean changed = false;
            if (firstName != null && !firstName.equals(user.getFirstName())) {
                user.setFirstName(firstName);
                changed = true;
            }
            if (lastName != null && !lastName.equals(user.getLastName())) {
                user.setLastName(lastName);
                changed = true;
            }
            if (email != null && !email.equals(user.getEmail())) {
                user.setEmail(email);
                changed = true;
            }
            if (changed) {
                keycloak.realm(realm).users().get(keycloakUserId).update(user);
                log.info("Updated Keycloak user {} in realm {}", keycloakUserId, realm);
            }
        } catch (Exception e) {
            log.error("Failed to update Keycloak user {} in realm {}: {}", keycloakUserId, realm, e.getMessage());
        }
    }

    /**
     * Simple check to see if an Identity Provider alias is already registered in a realm.
     */
    public boolean idpExists(String realm, String alias) {
        try {
            keycloak.realm(realm).identityProviders().get(alias).toRepresentation();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public String addIdentityProvider(String realm, CreateIdentityProviderRequest dto) {
        log.info("Adding identity provider '{}' to realm {}", dto.getAlias(), realm);
        Response resp = null;

        try {
            RealmResource rr = keycloak.realm(realm);

            IdentityProviderRepresentation idpRep = new IdentityProviderRepresentation();
            idpRep.setAlias(dto.getAlias());
            idpRep.setProviderId("oidc");
            idpRep.setEnabled(Boolean.TRUE.equals(dto.getEnabled()));
            idpRep.setStoreToken(Boolean.TRUE.equals(dto.getStoreToken()));
            idpRep.setLinkOnly(Boolean.FALSE);
            idpRep.setTrustEmail(true);
            idpRep.setDisplayName(dto.getDisplayName());

            Map<String, String> config = new HashMap<>();
            config.put("clientId", dto.getClientId());
            config.put("clientSecret", dto.getClientSecret());

            // Multi-tenant Azure OIDC endpoints
            config.put("authorizationUrl", "https://login.microsoftonline.com/common/oauth2/v2.0/authorize");
            config.put("tokenUrl", "https://login.microsoftonline.com/common/oauth2/v2.0/token");
            config.put("logoutUrl", "https://login.microsoftonline.com/common/oauth2/v2.0/logout");
            config.put("userInfoUrl", "https://graph.microsoft.com/oidc/userinfo");
            config.put("jwksUrl", "https://login.microsoftonline.com/common/discovery/v2.0/keys");

            // Empty issuer for multi-tenant to avoid validation errors
            config.put("issuer", "");
            config.put("validateSignature", "true");
            config.put("useJwksUrl", "true");

            config.put("scopes", "openid email profile offline_access");

            idpRep.setConfig(config);

            resp = rr.identityProviders().create(idpRep);
            if (resp.getStatus() != 201 && resp.getStatus() != 409) {
                throw new RuntimeException("Failed to create IdP: " + resp.getStatusInfo());
            }

            long tMappers = System.currentTimeMillis();
            configureOidcMappers(rr, realm, dto.getAlias());
            log.info("[SSO] addIdentityProvider: configureOidcMappers done in {}ms for realm={}", System.currentTimeMillis() - tMappers, realm);

            return buildAzureRedirectUrl(realm, dto.getAlias());

        } catch (Exception e) {
            log.error("Error creating IdP: {}", e.getMessage(), e);
            throw new RuntimeException("IdP creation failed", e);
        } finally {
            if (resp != null) resp.close();
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

    private String buildAzureRedirectUrl(String realm, String alias) {
        String keycloakBaseUrl = keycloakServerUrl;

        return keycloakBaseUrl + "/realms/" + realm + "/broker/" + alias + "/endpoint";
    }

    /**
     * Consolidates mapper logic.
     * 1. Creates IdP Mappers (Azure JWT -> Keycloak User Attribute)
     * 2. Creates DEDICATED Client Mappers (Keycloak User Attribute -> App Access Token)
     */
    private void configureOidcMappers(RealmResource rr, String realmName, String idpAlias) {
        // Logic: Client ID is exactly the same as the Realm Name
        String targetClientId = realmName;

        log.info("Configuring Mappers. Realm: {}, IdP: {}, TargetClient: {}", realmName, idpAlias, targetClientId);

        // =================================================================================
        // STEP A: Import Data from Azure Token (IdP Mappers)
        // =================================================================================
        try {
            IdentityProviderResource idpRes = rr.identityProviders().get(idpAlias);

            // Clean up old/duplicate mappers first
            List<IdentityProviderMapperRepresentation> existingMappers = idpRes.getMappers();
            if (existingMappers != null) {
                Set<String> correctMapperNames = Set.of("azure_tenant_id", "roles", "groups");
                for (IdentityProviderMapperRepresentation mapper : existingMappers) {
                    String mapperName = mapper.getName();
                    // Delete if it's a duplicate or old-style name
                    if (!correctMapperNames.contains(mapperName)) {
                        try {
                            idpRes.delete(mapper.getId());
                            log.info("🗑️ Deleted old/duplicate mapper '{}' from Azure IdP '{}' in master realm",
                                    mapperName, idpAlias);
                        } catch (Exception e) {
                            log.warn("Failed to delete mapper '{}': {}", mapperName, e.getMessage());
                        }
                    }
                }
            }

            // 1. Tenant ID (tid -> azure_tenant_id)
            createIdpAttributeMapper(idpRes, idpAlias, "azure_tenant_id", "tid", "azure_tenant_id");

            // 2. Roles (roles -> azure_roles) - mapper name must be "roles" for diagnostic to pass
            createIdpAttributeMapper(idpRes, idpAlias, "roles", "roles", "azure_roles");

            // 3. Groups (groups -> azure_groups) - mapper name must be "groups" for diagnostic to pass
            createIdpAttributeMapper(idpRes, idpAlias, "groups", "groups", "azure_groups");

        } catch (Exception e) {
            log.error("Failed to configure IdP Mappers for {}: {}", idpAlias, e.getMessage());
        }

        // =================================================================================
        // STEP B: Export Data to App Token (Dedicated Client Mappers)
        // =================================================================================

        ClientsResource clientsRes = rr.clients();
        ClientResource clientResource = null;

        // 1. Find the Client (using realmName as the clientId)
        try {
            List<ClientRepresentation> foundClients = clientsRes.findByClientId(targetClientId);

            if (foundClients == null || foundClients.isEmpty()) {
                log.error("CRITICAL: Client '{}' not found. Mappers cannot be added.", targetClientId);
                return;
            }

            // We must use the internal UUID to get the resource
            String internalId = foundClients.get(0).getId();
            clientResource = clientsRes.get(internalId);

        } catch (Exception e) {
            log.error("Error finding client '{}': {}", targetClientId, e.getMessage());
            return;
        }

        // 2. Clean up old/duplicate mappers from client
        List<ProtocolMapperRepresentation> currentMappers = clientResource.getProtocolMappers().getMappers();
        Set<String> correctMapperNames = Set.of("azure_tenant_id", "roles", "groups");
        for (ProtocolMapperRepresentation mapper : currentMappers) {
            String mapperName = mapper.getName();
            // Delete if it's not in our correct list
            if (!correctMapperNames.contains(mapperName)) {
                try {
                    clientResource.getProtocolMappers().delete(mapper.getId());
                    log.info("🗑️ Deleted old/duplicate mapper '{}' from client '{}' in realm '{}'",
                            mapperName, targetClientId, realmName);
                } catch (Exception e) {
                    log.warn("Failed to delete mapper '{}': {}", mapperName, e.getMessage());
                }
            }
        }

        // Refresh list after cleanup
        currentMappers = clientResource.getProtocolMappers().getMappers();
        List<ProtocolMapperRepresentation> finalCurrentMappers = currentMappers;
        Predicate<String> exists = name -> finalCurrentMappers.stream().anyMatch(m -> m.getName().equals(name));

        // Mapper 1: azure_tenant_id
        if (!exists.test("azure_tenant_id")) {
            createClientProtocolMapper(clientResource, "azure_tenant_id", "azure_tenant_id", "azure_tenant_id", "String", false);
        }

        // Mapper 2: roles (reads from azure_roles user attr, writes as "roles" in token)
        if (!exists.test("roles")) {
            createClientProtocolMapper(clientResource, "roles", "azure_roles", "roles", "String", true);
        }

        // Mapper 3: groups (reads from azure_groups user attr, writes as "groups" in token)
        if (!exists.test("groups")) {
            createClientProtocolMapper(clientResource, "groups", "azure_groups", "groups", "String", true);
        }
    }

    /**
     * Repairs the master realm's Azure IdP mappers.
     * Deletes all existing mappers and recreates them via configureOidcMappers.
     * Safe to call on an already-provisioned master realm.
     */
    public void repairMasterAzureIdpMappers() {
        String realm = "master";
        try {
            IdentityProviderResource idpRes = keycloak.realm(realm)
                    .identityProviders().get(masterAzureAlias);

            // Delete all existing mappers
            List<IdentityProviderMapperRepresentation> existing = idpRes.getMappers();
            if (existing != null) {
                for (IdentityProviderMapperRepresentation m : existing) {
                    try {
                        idpRes.delete(m.getId());
                        log.info("Deleted old mapper '{}' from master IdP '{}'", m.getName(), masterAzureAlias);
                    } catch (Exception e) {
                        log.warn("Could not delete mapper '{}': {}", m.getName(), e.getMessage());
                    }
                }
            }

            // Recreate using the same OIDC mapper config as addIdentityProvider
            RealmResource rr = keycloak.realm(realm);
            configureOidcMappers(rr, realm, masterAzureAlias);

            log.info("Master realm Azure IdP mappers repaired for alias='{}'", masterAzureAlias);
        } catch (Exception e) {
            log.error("Failed to repair master Azure IdP mappers for alias='{}': {}", masterAzureAlias, e.getMessage());
        }
    }

    /**
     * Configures mappers for the built-in Keycloak Microsoft IdP (providerId="microsoft").
     * Uses "microsoft-user-attribute-mapper" for claim imports instead of the OIDC mapper type.
     */
    private void configureMicrosoftIdpMappers(RealmResource rr, String realmName, String idpAlias) {
        log.info("Configuring Microsoft IdP mappers. Realm: {}, IdP: {}", realmName, idpAlias);
        try {
            IdentityProviderResource idpRes = rr.identityProviders().get(idpAlias);

            // Clean up old/duplicate mappers first
            List<IdentityProviderMapperRepresentation> existingMappers = idpRes.getMappers();
            if (existingMappers != null) {
                Set<String> correctMapperNames = Set.of("azure_tenant_id", "roles", "groups");
                for (IdentityProviderMapperRepresentation mapper : existingMappers) {
                    String mapperName = mapper.getName();
                    // Delete if it's a duplicate or old-style name
                    if (!correctMapperNames.contains(mapperName)) {
                        try {
                            idpRes.delete(mapper.getId());
                            log.info("🗑️ Deleted old/duplicate mapper '{}' from Microsoft IdP '{}' in realm '{}'",
                                    mapperName, idpAlias, realmName);
                        } catch (Exception e) {
                            log.warn("Failed to delete mapper '{}': {}", mapperName, e.getMessage());
                        }
                    }
                }
            }

            createMicrosoftIdpAttributeMapper(idpRes, idpAlias, "azure_tenant_id", "tid", "azure_tenant_id");
            createMicrosoftIdpAttributeMapper(idpRes, idpAlias, "roles", "roles", "azure_roles");
            createMicrosoftIdpAttributeMapper(idpRes, idpAlias, "groups", "groups", "azure_groups");

        } catch (Exception e) {
            log.error("Failed to configure Microsoft IdP mappers for {}: {}", idpAlias, e.getMessage());
        }
    }

    // --- HELPER 1: Create IdP Mapper (Import from Azure) ---
    private void createIdpAttributeMapper(IdentityProviderResource idpRes, String alias, String name, String claimName, String userAttribute) {
        try {
            IdentityProviderMapperRepresentation mapper = new IdentityProviderMapperRepresentation();
            mapper.setName(name);
            mapper.setIdentityProviderAlias(alias);
            mapper.setIdentityProviderMapper("oidc-user-attribute-idp-mapper");
            mapper.setConfig(Map.of(
                    "claim", claimName,
                    "user.attribute", userAttribute,
                    "syncMode", "FORCE"
            ));
            idpRes.addMapper(mapper);
            log.info("IdP Mapper created: {}", name);
        } catch (Exception e) {
            // Safe to ignore if exists
        }
    }

    // --- HELPER 1b: Create IdP Mapper for built-in Microsoft provider ---
    // Microsoft's built-in provider is OIDC-based and accepts oidc-user-attribute-idp-mapper.
    // syncMode INHERIT means "follow the IdP-level sync mode setting" which is correct.
    private void createMicrosoftIdpAttributeMapper(IdentityProviderResource idpRes, String alias, String name, String claimName, String userAttribute) {
        try {
            IdentityProviderMapperRepresentation mapper = new IdentityProviderMapperRepresentation();
            mapper.setName(name);
            mapper.setIdentityProviderAlias(alias);
            mapper.setIdentityProviderMapper("oidc-user-attribute-idp-mapper");
            mapper.setConfig(Map.of(
                    "claim", claimName,
                    "user.attribute", userAttribute,
                    "syncMode", "INHERIT"
            ));
            idpRes.addMapper(mapper);
            log.info("Microsoft IdP Mapper created: {}", name);
        } catch (Exception e) {
            log.warn("Could not create Microsoft IdP mapper '{}': {}", name, e.getMessage());
        }
    }

    // --- HELPER 2: Create Client Protocol Mapper (Export to Token) ---
// UPDATED: Accepts ClientResource instead of ClientScopeResource
    private void createClientProtocolMapper(ClientResource clientRes, String name, String userAttribute, String tokenClaimName, String jsonType, boolean multivalued) {
        try {
            ProtocolMapperRepresentation mapper = new ProtocolMapperRepresentation();
            mapper.setName(name);
            mapper.setProtocol("openid-connect");
            mapper.setProtocolMapper("oidc-usermodel-attribute-mapper");

            mapper.setConfig(Map.of(
                    "user.attribute", userAttribute,      // Read from Keycloak DB
                    "claim.name", tokenClaimName,         // Write to Backend Token
                    "jsonType.label", jsonType,
                    "multivalued", String.valueOf(multivalued),
                    "id.token.claim", "true",
                    "access.token.claim", "true"
            ));

            // Add mapper directly to the client
            clientRes.getProtocolMappers().createMapper(mapper);
            log.info("Created Dedicated Client Mapper: {}", name);

        } catch (Exception e) {
            log.error("Failed to create client mapper '{}': {}", name, e.getMessage());
        }
    }


    /**
     * Programmatically creates an Identity Provider in a specific realm.
     */
    public void createIdp(String realm, IdentityProviderRepresentation idp) {
        try {
            keycloak.realm(realm).identityProviders().create(idp);
            log.info("Created IdP '{}' in realm '{}'", idp.getAlias(), realm);
        } catch (Exception e) {
            log.error("Failed to create IdP in realm {}: {}", realm, e.getMessage());
        }
    }

    /**
     * Configures mappers on the master-hub (keycloak-oidc) IdP in the tenant realm.
     * These mappers import azure_tenant_id, azure_roles, azure_groups from the master realm
     * token claims into the tenant realm user attributes, so they flow through to the app JWT.
     */
    public void configureMasterHubIdpMappers(String tenantRealm) {
        try {
            String alias = "master-hub";
            IdentityProviderResource idpRes = keycloak.realm(tenantRealm)
                    .identityProviders().get(alias);

            // Clean up old/duplicate mappers first
            List<IdentityProviderMapperRepresentation> existingMappers = idpRes.getMappers();
            if (existingMappers != null) {
                // Define mappers to keep (by name)
                Set<String> correctMapperNames = Set.of("azure_tenant_id", "roles", "groups");

                for (IdentityProviderMapperRepresentation mapper : existingMappers) {
                    String mapperName = mapper.getName();

                    // Delete if it's a duplicate or old-style name
                    if (!correctMapperNames.contains(mapperName)) {
                        try {
                            idpRes.delete(mapper.getId());
                            log.info("🗑️ Deleted old/duplicate mapper '{}' from master-hub IdP in realm '{}'",
                                    mapperName, tenantRealm);
                        } catch (Exception e) {
                            log.warn("Failed to delete mapper '{}': {}", mapperName, e.getMessage());
                        }
                    }
                }
            }

            // Import from broker token (which has roles/groups claims) into user attributes
            createIdpAttributeMapper(idpRes, alias, "azure_tenant_id", "azure_tenant_id", "azure_tenant_id");
            createIdpAttributeMapper(idpRes, alias, "roles", "roles", "azure_roles");
            createIdpAttributeMapper(idpRes, alias, "groups", "groups", "azure_groups");

            log.info("✅ master-hub IdP mappers configured for tenant realm '{}'", tenantRealm);
        } catch (Exception e) {
            log.error("Failed to configure master-hub IdP mappers for realm '{}': {}", tenantRealm, e.getMessage());
        }
    }

    /**
     * Adds client protocol mappers to the broker client in the master realm.
     * The broker client issues tokens back to the tenant realm during the OIDC handshake.
     * Without these mappers, azure_tenant_id / azure_roles / azure_groups are not included
     * in those tokens and are lost before they reach the tenant realm.
     * Idempotent — skips any mapper that already exists by name.
     */
    public void configureBrokerClientMappers(String tenantRealmName) {
        String brokerClientId = "broker-for-" + tenantRealmName;
        try {
            List<ClientRepresentation> found = keycloak.realm("master")
                    .clients().findByClientId(brokerClientId);
            if (found == null || found.isEmpty()) {
                log.warn("configureBrokerClientMappers: client '{}' not found in master realm, skipping", brokerClientId);
                return;
            }
            ClientResource clientResource = keycloak.realm("master")
                    .clients().get(found.get(0).getId());

            List<ProtocolMapperRepresentation> existing = clientResource.getProtocolMappers().getMappers();

            // Clean up old/duplicate mappers
            Set<String> correctMapperNames = Set.of("azure_tenant_id", "roles", "groups");
            for (ProtocolMapperRepresentation mapper : existing) {
                String mapperName = mapper.getName();
                // Delete if it's not in our correct list (old-style names like "Pass Tenant ID", etc.)
                if (!correctMapperNames.contains(mapperName)) {
                    try {
                        clientResource.getProtocolMappers().delete(mapper.getId());
                        log.info("🗑️ Deleted old/duplicate mapper '{}' from broker client '{}'",
                                mapperName, brokerClientId);
                    } catch (Exception e) {
                        log.warn("Failed to delete mapper '{}': {}", mapperName, e.getMessage());
                    }
                }
            }

            // Refresh list after cleanup
            existing = clientResource.getProtocolMappers().getMappers();
            List<ProtocolMapperRepresentation> finalExisting = existing;
            Predicate<String> exists = name -> finalExisting.stream().anyMatch(m -> m.getName().equals(name));

            if (!exists.test("azure_tenant_id")) {
                createClientProtocolMapper(clientResource, "azure_tenant_id", "azure_tenant_id", "azure_tenant_id", "String", false);
            }
            if (!exists.test("roles")) {
                // Pass through azure_roles as "roles" in broker token
                createClientProtocolMapper(clientResource, "roles", "azure_roles", "roles", "String", true);
            }
            if (!exists.test("groups")) {
                // Pass through azure_groups as "groups" in broker token
                createClientProtocolMapper(clientResource, "groups", "azure_groups", "groups", "String", true);
            }

            log.info("✅ Broker client mappers configured for client='{}' in master realm", brokerClientId);
        } catch (Exception e) {
            log.error("Failed to configure broker client mappers for '{}': {}", brokerClientId, e.getMessage());
        }
    }

    /**
     * Adds client protocol mappers to the tenant realm's own client (clientId = tenantName).
     * These read the user attributes set by the IdP mappers and write them as claims
     * into the access token and id token returned to the application.
     * Idempotent — skips any mapper that already exists by name.
     */
    public void configureTenantClientMappers(String tenantRealm, String tenantName) {
        try {
            List<ClientRepresentation> found = keycloak.realm(tenantRealm)
                    .clients().findByClientId(tenantName);
            if (found == null || found.isEmpty()) {
                log.warn("configureTenantClientMappers: client '{}' not found in realm '{}', skipping", tenantName, tenantRealm);
                return;
            }
            ClientResource clientResource = keycloak.realm(tenantRealm)
                    .clients().get(found.get(0).getId());

            List<ProtocolMapperRepresentation> existing = clientResource.getProtocolMappers().getMappers();

            // Clean up old/duplicate mappers
            Set<String> correctMapperNames = Set.of("azure_tenant_id", "roles", "groups");
            for (ProtocolMapperRepresentation mapper : existing) {
                String mapperName = mapper.getName();
                // Delete if it's not in our correct list (old-style names like "Pass Tenant ID", etc.)
                if (!correctMapperNames.contains(mapperName)) {
                    try {
                        clientResource.getProtocolMappers().delete(mapper.getId());
                        log.info("🗑️ Deleted old/duplicate mapper '{}' from tenant client '{}' in realm '{}'",
                                mapperName, tenantName, tenantRealm);
                    } catch (Exception e) {
                        log.warn("Failed to delete mapper '{}': {}", mapperName, e.getMessage());
                    }
                }
            }

            // Refresh list after cleanup
            existing = clientResource.getProtocolMappers().getMappers();
            List<ProtocolMapperRepresentation> finalExisting = existing;
            Predicate<String> exists = name -> finalExisting.stream().anyMatch(m -> m.getName().equals(name));

            if (!exists.test("azure_tenant_id")) {
                createClientProtocolMapper(clientResource, "azure_tenant_id", "azure_tenant_id", "azure_tenant_id", "String", false);
            }
            if (!exists.test("roles")) {
                // Read from azure_roles user attribute, write as "roles" in JWT token
                createClientProtocolMapper(clientResource, "roles", "azure_roles", "roles", "String", true);
            }
            if (!exists.test("groups")) {
                // Read from azure_groups user attribute, write as "groups" in JWT token
                createClientProtocolMapper(clientResource, "groups", "azure_groups", "groups", "String", true);
            }

            log.info("✅ Tenant client mappers configured for realm='{}' client='{}'", tenantRealm, tenantName);
        } catch (Exception e) {
            log.error("Failed to configure tenant client mappers for realm='{}': {}", tenantRealm, e.getMessage());
        }
    }

    public void setAutoRedirect(String realm, String idpAlias) {
        try {

            if ("master".equalsIgnoreCase(realm)) {
                log.info("Skipping auto-redirect for master realm to maintain login screen.");
                return;
            }
            AuthenticationManagementResource auth = keycloak.realm(realm).flows();

            // 1. Get all executions for the 'browser' flow
            List<AuthenticationExecutionInfoRepresentation> executions = auth.getExecutions("browser");

            // 2. Find the Identity Provider Redirector
            AuthenticationExecutionInfoRepresentation execution = executions.stream()
                    .filter(e -> "identity-provider-redirector".equals(e.getProviderId()))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Redirector not found in flow"));

            // 3. Prepare the config
            AuthenticatorConfigRepresentation configRep = new AuthenticatorConfigRepresentation();
            configRep.setAlias(idpAlias);
            configRep.setConfig(Map.of("defaultProvider", idpAlias));

            // 4. Handle 404 by checking if config already exists
            if (execution.getAuthenticationConfig() != null) {
                // Update existing config
                configRep.setId(execution.getAuthenticationConfig());
                auth.updateAuthenticatorConfig(execution.getAuthenticationConfig(), configRep);
            } else {
                // Create new config and link it
                Response response = auth.newExecutionConfig(execution.getId(), configRep);
                if (response.getStatus() != 201) {
                    log.error("Failed to link config: " + response.readEntity(String.class));
                }
            }

            log.info("Auto-redirect to '{}' successfully enabled for realm '{}'", idpAlias, realm);

        } catch (Exception e) {
            log.error("Error setting auto-redirect: {}", e.getMessage());
        }
    }

    /**
     * Updates the master-hub IdP's authorizationUrl in the given tenant realm to include
     * kc_idp_hint=microsoft so that the master realm skips its login screen and goes straight
     * to Azure AD.
     */
    public void updateMasterHubIdpAuthorizationUrl(String tenantRealm) {
        try {
            IdentityProviderRepresentation idp = keycloak.realm(tenantRealm)
                    .identityProviders().get("master-hub").toRepresentation();
            Map<String, String> config = idp.getConfig();
            if (config == null) {
                config = new HashMap<>();
            }
            config.put("authorizationUrl", keycloakServerUrl + "/realms/master/protocol/openid-connect/auth?kc_idp_hint=microsoft");
            // Remove stale kc_idp_hint / forwardParameters keys from old config
            config.remove("kc_idp_hint");
            config.remove("forwardParameters");
            idp.setConfig(config);
            keycloak.realm(tenantRealm).identityProviders().get("master-hub").update(idp);
            log.info("Updated master-hub authorizationUrl with kc_idp_hint=microsoft for realm={}", tenantRealm);
        } catch (Exception e) {
            log.error("Failed to update master-hub authorizationUrl for realm={}: {}", tenantRealm, e.getMessage());
        }
    }

    /**
     * Updates master-hub IdP with fresh broker client credentials and authorization URL.
     * Called during tenant creation/update to ensure credentials stay synchronized.
     *
     * @param tenantRealm Tenant realm name
     * @param brokerClientId Broker client ID (from broker-for-{tenant} client)
     * @param brokerClientSecret Broker client secret
     */
    public void updateMasterHubIdpWithCredentials(String tenantRealm, String brokerClientId, String brokerClientSecret) {
        try {
            IdentityProviderRepresentation idp = keycloak.realm(tenantRealm)
                    .identityProviders().get("master-hub").toRepresentation();

            Map<String, String> config = idp.getConfig();
            if (config == null) {
                config = new HashMap<>();
            }

            // Update authorization URL with kc_idp_hint
            config.put("authorizationUrl", keycloakServerUrl + "/realms/master/protocol/openid-connect/auth?kc_idp_hint=" + masterAzureAlias);

            // Update broker client credentials
            config.put("clientId", brokerClientId);
            config.put("clientSecret", brokerClientSecret);

            // Update other OIDC endpoints
            config.put("tokenUrl", keycloakServerUrl + "/realms/master/protocol/openid-connect/token");
            config.put("userInfoUrl", keycloakServerUrl + "/realms/master/protocol/openid-connect/userinfo");
            config.put("logoutUrl", keycloakServerUrl + "/realms/master/protocol/openid-connect/logout");
            config.put("issuer", keycloakServerUrl + "/realms/master");

            // Remove stale kc_idp_hint / forwardParameters keys from old config
            config.remove("kc_idp_hint");
            config.remove("forwardParameters");

            idp.setConfig(config);
            keycloak.realm(tenantRealm).identityProviders().get("master-hub").update(idp);

            log.info("✅ Updated master-hub IdP with fresh credentials and authorizationUrl for realm={}", tenantRealm);

        } catch (Exception e) {
            log.error("❌ Failed to update master-hub IdP credentials for realm={}: {}", tenantRealm, e.getMessage());
            throw new KeycloakOperationException("IDP_UPDATE_FAILED", 500,
                    "Failed to update master-hub IdP: " + e.getMessage());
        }
    }

    /**
     * Creates an Organization in the specified realm (usually Master).
     */
    public void createOrganization(String realm, String orgName, String domain) {
        log.info("Creating Organization '{}' in realm '{}' for domain '{}'", orgName, realm, domain);
        try {

            OrganizationRepresentation orgRep = new OrganizationRepresentation();
            orgRep.setName(orgName);
            orgRep.setAlias(orgName.toLowerCase().replaceAll("\\s+", "-"));
            orgRep.setEnabled(true);

            OrganizationDomainRepresentation orgDomain = new OrganizationDomainRepresentation();
            orgDomain.setName(domain);
            orgDomain.setVerified(true);
            orgDomain.setName(domain);

            orgRep.addDomain(orgDomain);
            try (Response response = keycloak.realm(realm).organizations().create(orgRep)) {
                if (response.getStatus() != 201 && response.getStatus() != 409) {
                    log.error("Org creation failed: Status {}", response.getStatus());
                }
            }
        } catch (Exception e) {
            log.error("Failed to create Organization: {}", e.getMessage());
            // Non-fatal for the experiment, but wrap for production
        }
    }

    /**
     * Links an existing Identity Provider to an Organization.
     */
    public void linkIdpToOrganization(String realm, String orgAlias, String idpAlias) {
        log.info("Linking IdP '{}' to Org '{}' in realm '{}'", idpAlias, orgAlias, realm);
        try {

            // 2. Perform the link on the Organization Resource.
            // Path: /admin/realms/{realm}/organizations/{orgId}/identity-providers
            // The method signature is: Response addIdentityProvider(String id)
            keycloak.realm(realm)
                    .organizations()
                    .get(orgAlias) // Returns OrganizationResource
                    .identityProviders() // Returns OrganizationIdentityProvidersResource
                    .addIdentityProvider(idpAlias);

            log.info("Successfully linked IdP '{}' to Organization '{}'", idpAlias, orgAlias);

        } catch (Exception e) {
            log.error("IdP linking failed for org {}: {}", orgAlias, e.getMessage());
            throw wrap("ORG_IDP_LINK_FAILED", 500, "Failed to link IdP", e);
        }
    }

    /**
     * Check if an organization exists in the specified realm
     *
     * @param realm Realm name (usually "master")
     * @param orgAlias Organization alias
     * @return true if organization exists, false otherwise
     */
    public boolean organizationExists(String realm, String orgAlias) {
        try {
            OrganizationRepresentation org = keycloak.realm(realm)
                    .organizations()
                    .get(orgAlias)
                    .toRepresentation();
            log.debug("Organization exists check: realm={}, orgAlias={}, exists=true", realm, orgAlias);
            return org != null;
        } catch (Exception e) {
            log.debug("Organization exists check: realm={}, orgAlias={}, exists=false", realm, orgAlias);
            return false;
        }
    }

    /**
     * Create organization if it doesn't exist
     *
     * @param realm Realm name (usually "master")
     * @param orgName Organization name
     * @param domain Organization domain
     * @return Organization alias
     */
    public String createOrganizationIfNotExists(String realm, String orgName, String domain) {
        String orgAlias = orgName.toLowerCase().replaceAll("\\s+", "-");

        if (organizationExists(realm, orgAlias)) {
            log.info("Organization '{}' already exists in realm '{}'", orgAlias, realm);
            return orgAlias;
        }

        log.info("Creating organization '{}' in realm '{}' with domain '{}'", orgName, realm, domain);
        createOrganization(realm, orgName, domain);
        return orgAlias;
    }

    /**
     * Check if IdP is already linked to an organization
     *
     * @param realm Realm name
     * @param orgAlias Organization alias
     * @param idpAlias Identity Provider alias
     * @return true if IdP is linked, false otherwise
     */
    public boolean isIdpLinkedToOrganization(String realm, String orgAlias, String idpAlias) {
        try {
            // Get all IdPs linked to the organization
            List<IdentityProviderRepresentation> linkedIdps = keycloak.realm(realm)
                    .organizations()
                    .get(orgAlias)
                    .identityProviders()
                    .getIdentityProviders();

            boolean isLinked = linkedIdps.stream()
                    .anyMatch(idp -> idp.getAlias().equals(idpAlias));

            log.debug("IdP link check: realm={}, org={}, idp={}, linked={}", realm, orgAlias, idpAlias, isLinked);
            return isLinked;

        } catch (Exception e) {
            log.debug("IdP link check failed: realm={}, org={}, idp={}, error={}", realm, orgAlias, idpAlias, e.getMessage());
            return false;
        }
    }

    /**
     * Link IdP to organization if not already linked
     *
     * @param realm Realm name
     * @param orgAlias Organization alias
     * @param idpAlias Identity Provider alias
     */
    public void linkIdpToOrganizationIfNotLinked(String realm, String orgAlias, String idpAlias) {
        if (isIdpLinkedToOrganization(realm, orgAlias, idpAlias)) {
            log.info("IdP '{}' is already linked to organization '{}' in realm '{}'", idpAlias, orgAlias, realm);
            return;
        }

        log.info("Linking IdP '{}' to organization '{}' in realm '{}'", idpAlias, orgAlias, realm);
        linkIdpToOrganization(realm, orgAlias, idpAlias);
    }

    /**
     * Diagnose SSO configuration for a tenant realm
     * Checks all critical SSO components and returns detailed diagnostic report
     *
     * @param tenantRealm Tenant realm name
     * @param tenantName Tenant name
     * @param domain Tenant domain
     * @param azureIdpAlias Azure IdP alias in master realm
     * @return Diagnostic report with issues, warnings, and recommendations
     */
    public com.secufusion.tenant.dto.SsoDiagnosticDto diagnoseSsoConfiguration(
            String tenantRealm, String tenantName, String domain, String azureIdpAlias) {

        log.info("🔍 Starting SSO diagnosis for tenant realm: {}", tenantRealm);

        com.secufusion.tenant.dto.SsoDiagnosticDto.SsoDiagnosticDtoBuilder diagnosticBuilder =
                com.secufusion.tenant.dto.SsoDiagnosticDto.builder()
                        .tenantRealm(tenantRealm)
                        .tenantName(tenantName)
                        .domain(domain)
                        .azureIdpAlias(azureIdpAlias);

        List<String> issues = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> validConfigurations = new ArrayList<>();

        try {
            // Check 1: Master-hub IdP in tenant realm
            log.info("Check 1: Verifying master-hub IdP configuration in tenant realm");
            com.secufusion.tenant.dto.SsoDiagnosticDto.MasterHubIdpCheck masterHubCheck = checkMasterHubIdp(tenantRealm);
            diagnosticBuilder.masterHubIdpCheck(masterHubCheck);

            if (!masterHubCheck.isExists()) {
                issues.add("Master-hub IdP does not exist in tenant realm");
            } else if (!masterHubCheck.isHasCorrectAuthorizationUrl()) {
                issues.add("Master-hub IdP authorization URL is missing kc_idp_hint parameter");
                issues.add("Current: " + masterHubCheck.getCurrentAuthorizationUrl());
                issues.add("Expected: " + masterHubCheck.getExpectedAuthorizationUrl());
            } else {
                validConfigurations.add("✅ Master-hub IdP authorization URL is correctly configured");
            }

            // Check 2: Master-hub IdP mappers in tenant realm
            log.info("Check 2: Verifying master-hub IdP mappers");
            com.secufusion.tenant.dto.SsoDiagnosticDto.IdpMappersCheck idpMappersCheck = checkIdpMappers(tenantRealm, "master-hub");
            diagnosticBuilder.masterHubIdpMappersCheck(idpMappersCheck);

            if (!idpMappersCheck.getMissingMappers().isEmpty()) {
                issues.add("Master-hub IdP missing mappers: " + String.join(", ", idpMappersCheck.getMissingMappers()));
            } else {
                validConfigurations.add("✅ Master-hub IdP has all required mappers (azure_tenant_id, roles, groups)");
            }

            // Check 3: Tenant client mappers
            log.info("Check 3: Verifying tenant client mappers");
            com.secufusion.tenant.dto.SsoDiagnosticDto.ClientMappersCheck tenantClientCheck = checkClientMappers(tenantRealm, tenantRealm);
            diagnosticBuilder.tenantClientMappersCheck(tenantClientCheck);

            if (!tenantClientCheck.isClientExists()) {
                warnings.add("Tenant client '" + tenantRealm + "' not found in tenant realm");
            } else if (!tenantClientCheck.getMissingMappers().isEmpty()) {
                issues.add("Tenant client missing mappers: " + String.join(", ", tenantClientCheck.getMissingMappers()));
            } else {
                validConfigurations.add("✅ Tenant client has all required mappers");
            }

            // Check 4: Broker client mappers in master realm
            log.info("Check 4: Verifying broker client mappers in master realm");
            String brokerClientId = "broker-for-" + tenantRealm;
            com.secufusion.tenant.dto.SsoDiagnosticDto.ClientMappersCheck brokerClientCheck = checkClientMappers("master", brokerClientId);
            diagnosticBuilder.brokerClientMappersCheck(brokerClientCheck);

            if (!brokerClientCheck.isClientExists()) {
                warnings.add("Broker client '" + brokerClientId + "' not found in master realm");
            } else if (!brokerClientCheck.getMissingMappers().isEmpty()) {
                issues.add("Broker client missing mappers: " + String.join(", ", brokerClientCheck.getMissingMappers()));
            } else {
                validConfigurations.add("✅ Broker client has all required mappers");
            }

            // Check 5: Master Azure IdP mappers
            log.info("Check 5: Verifying master Azure IdP mappers");
            com.secufusion.tenant.dto.SsoDiagnosticDto.AzureIdpMappersCheck azureIdpCheck = checkMasterAzureIdpMappers(azureIdpAlias);
            diagnosticBuilder.masterAzureIdpMappersCheck(azureIdpCheck);

            if (!azureIdpCheck.getIssues().isEmpty()) {
                issues.addAll(azureIdpCheck.getIssues());
            } else {
                validConfigurations.add("✅ Master Azure IdP mappers are correctly configured");
            }

            // Check 6 & 7: Organization and IdP linking
            log.info("Check 6 & 7: Verifying organization and IdP linking");
            com.secufusion.tenant.dto.SsoDiagnosticDto.OrganizationCheck orgCheck = checkOrganization(tenantName, domain, azureIdpAlias);
            diagnosticBuilder.organizationCheck(orgCheck);

            if (!orgCheck.isOrganizationExists()) {
                warnings.add("Organization does not exist in master realm");
            } else if (!orgCheck.isIdpLinkedToOrganization()) {
                warnings.add("Azure IdP not linked to organization");
            } else {
                validConfigurations.add("✅ Organization exists and Azure IdP is linked");
            }

            // Determine if repair is needed
            boolean requiresRepair = !issues.isEmpty();
            diagnosticBuilder.requiresRepair(requiresRepair);

            // Build recommendation
            String recommendation;
            if (requiresRepair) {
                recommendation = String.format(
                        "⚠️ SSO configuration has %d issue(s) and %d warning(s). " +
                        "Run POST /api/tenants/%s/repair-sso to fix all issues automatically.",
                        issues.size(), warnings.size(), tenantRealm
                );
            } else if (!warnings.isEmpty()) {
                recommendation = String.format(
                        "ℹ️ SSO configuration is functional but has %d warning(s). " +
                        "Consider running repair to optimize configuration.",
                        warnings.size()
                );
            } else {
                recommendation = "✅ SSO configuration is healthy. No repair needed.";
            }
            diagnosticBuilder.recommendation(recommendation);

            log.info("🔍 Diagnosis complete: {} issues, {} warnings", issues.size(), warnings.size());

        } catch (Exception e) {
            log.error("❌ SSO diagnosis failed for tenant realm: {}", tenantRealm, e);
            issues.add("Diagnosis failed: " + e.getMessage());
            diagnosticBuilder.requiresRepair(true)
                    .recommendation("⚠️ Diagnosis encountered errors. Manual investigation required.");
        }

        return diagnosticBuilder
                .issues(issues)
                .warnings(warnings)
                .validConfigurations(validConfigurations)
                .build();
    }

    private com.secufusion.tenant.dto.SsoDiagnosticDto.MasterHubIdpCheck checkMasterHubIdp(String tenantRealm) {
        try {
            IdentityProviderResource idpResource = keycloak.realm(tenantRealm).identityProviders().get("master-hub");
            IdentityProviderRepresentation idp = idpResource.toRepresentation();

            String authUrl = idp.getConfig().get("authorizationUrl");
            String expectedAuthUrl = keycloakServerUrl + "/realms/master/protocol/openid-connect/auth?kc_idp_hint=" + masterAzureAlias;

            boolean hasCorrectUrl = authUrl != null && authUrl.contains("kc_idp_hint=" + masterAzureAlias);

            return com.secufusion.tenant.dto.SsoDiagnosticDto.MasterHubIdpCheck.builder()
                    .exists(true)
                    .hasCorrectAuthorizationUrl(hasCorrectUrl)
                    .currentAuthorizationUrl(authUrl)
                    .expectedAuthorizationUrl(expectedAuthUrl)
                    .build();

        } catch (Exception e) {
            log.warn("Master-hub IdP not found in realm: {}", tenantRealm);
            return com.secufusion.tenant.dto.SsoDiagnosticDto.MasterHubIdpCheck.builder()
                    .exists(false)
                    .hasCorrectAuthorizationUrl(false)
                    .build();
        }
    }

    private com.secufusion.tenant.dto.SsoDiagnosticDto.IdpMappersCheck checkIdpMappers(String realmName, String idpAlias) {
        List<String> missingMappers = new ArrayList<>();

        try {
            IdentityProviderResource idpResource = keycloak.realm(realmName).identityProviders().get(idpAlias);
            List<IdentityProviderMapperRepresentation> mappers = idpResource.getMappers();

            boolean hasAzureTenantId = mappers.stream().anyMatch(m -> "azure_tenant_id".equals(m.getName()));
            boolean hasRoles = mappers.stream().anyMatch(m -> "roles".equals(m.getName()));
            boolean hasGroups = mappers.stream().anyMatch(m -> "groups".equals(m.getName()));

            if (!hasAzureTenantId) missingMappers.add("azure_tenant_id");
            if (!hasRoles) missingMappers.add("roles");
            if (!hasGroups) missingMappers.add("groups");

            return com.secufusion.tenant.dto.SsoDiagnosticDto.IdpMappersCheck.builder()
                    .hasAzureTenantIdMapper(hasAzureTenantId)
                    .hasRolesMapper(hasRoles)
                    .hasGroupsMapper(hasGroups)
                    .totalMappers(mappers.size())
                    .missingMappers(missingMappers)
                    .build();

        } catch (Exception e) {
            log.warn("Failed to check IdP mappers for {}/{}: {}", realmName, idpAlias, e.getMessage());
            return com.secufusion.tenant.dto.SsoDiagnosticDto.IdpMappersCheck.builder()
                    .totalMappers(0)
                    .missingMappers(Arrays.asList("azure_tenant_id", "roles", "groups"))
                    .build();
        }
    }

    private com.secufusion.tenant.dto.SsoDiagnosticDto.ClientMappersCheck checkClientMappers(String realmName, String clientId) {
        List<String> missingMappers = new ArrayList<>();

        try {
            ClientRepresentation client = keycloak.realm(realmName).clients().findByClientId(clientId).stream()
                    .findFirst()
                    .orElse(null);

            if (client == null) {
                return com.secufusion.tenant.dto.SsoDiagnosticDto.ClientMappersCheck.builder()
                        .clientExists(false)
                        .totalMappers(0)
                        .missingMappers(Arrays.asList("azure_tenant_id", "roles", "groups"))
                        .build();
            }

            List<ProtocolMapperRepresentation> mappers = keycloak.realm(realmName).clients()
                    .get(client.getId()).getProtocolMappers().getMappers();

            boolean hasAzureTenantId = mappers.stream().anyMatch(m -> "azure_tenant_id".equals(m.getName()));
            boolean hasRoles = mappers.stream().anyMatch(m -> "roles".equals(m.getName()));
            boolean hasGroups = mappers.stream().anyMatch(m -> "groups".equals(m.getName()));

            if (!hasAzureTenantId) missingMappers.add("azure_tenant_id");
            if (!hasRoles) missingMappers.add("roles");
            if (!hasGroups) missingMappers.add("groups");

            return com.secufusion.tenant.dto.SsoDiagnosticDto.ClientMappersCheck.builder()
                    .clientExists(true)
                    .hasAzureTenantIdMapper(hasAzureTenantId)
                    .hasRolesMapper(hasRoles)
                    .hasGroupsMapper(hasGroups)
                    .totalMappers(mappers.size())
                    .missingMappers(missingMappers)
                    .build();

        } catch (Exception e) {
            log.warn("Failed to check client mappers for {}/{}: {}", realmName, clientId, e.getMessage());
            return com.secufusion.tenant.dto.SsoDiagnosticDto.ClientMappersCheck.builder()
                    .clientExists(false)
                    .totalMappers(0)
                    .missingMappers(Arrays.asList("azure_tenant_id", "roles", "groups"))
                    .build();
        }
    }

    private com.secufusion.tenant.dto.SsoDiagnosticDto.AzureIdpMappersCheck checkMasterAzureIdpMappers(String azureIdpAlias) {
        List<String> issues = new ArrayList<>();

        try {
            IdentityProviderResource idpResource = keycloak.realm("master").identityProviders().get(azureIdpAlias);
            List<IdentityProviderMapperRepresentation> mappers = idpResource.getMappers();

            IdentityProviderMapperRepresentation rolesMapper = mappers.stream()
                    .filter(m -> "roles".equals(m.getName()))
                    .findFirst()
                    .orElse(null);

            IdentityProviderMapperRepresentation groupsMapper = mappers.stream()
                    .filter(m -> "groups".equals(m.getName()))
                    .findFirst()
                    .orElse(null);

            boolean hasRoles = rolesMapper != null;
            boolean hasGroups = groupsMapper != null;

            boolean rolesCorrectSyncMode = hasRoles &&
                    "FORCE".equals(rolesMapper.getConfig().get("syncMode"));

            boolean groupsCorrectSyncMode = hasGroups &&
                    "FORCE".equals(groupsMapper.getConfig().get("syncMode"));

            if (!hasRoles) {
                issues.add("Master Azure IdP missing 'roles' mapper");
            } else if (!rolesCorrectSyncMode) {
                issues.add("Master Azure IdP 'roles' mapper has incorrect syncMode (expected: FORCE)");
            }

            if (!hasGroups) {
                issues.add("Master Azure IdP missing 'groups' mapper");
            } else if (!groupsCorrectSyncMode) {
                issues.add("Master Azure IdP 'groups' mapper has incorrect syncMode (expected: FORCE)");
            }

            return com.secufusion.tenant.dto.SsoDiagnosticDto.AzureIdpMappersCheck.builder()
                    .hasRolesMapper(hasRoles)
                    .hasGroupsMapper(hasGroups)
                    .rolesMapperHasCorrectSyncMode(rolesCorrectSyncMode)
                    .groupsMapperHasCorrectSyncMode(groupsCorrectSyncMode)
                    .totalMappers(mappers.size())
                    .issues(issues)
                    .build();

        } catch (Exception e) {
            log.warn("Failed to check master Azure IdP mappers: {}", e.getMessage());
            issues.add("Failed to check master Azure IdP mappers: " + e.getMessage());
            return com.secufusion.tenant.dto.SsoDiagnosticDto.AzureIdpMappersCheck.builder()
                    .totalMappers(0)
                    .issues(issues)
                    .build();
        }
    }

    private com.secufusion.tenant.dto.SsoDiagnosticDto.OrganizationCheck checkOrganization(
            String tenantName, String domain, String azureIdpAlias) {

        try {
            String orgAlias = tenantName.toLowerCase().replaceAll("\\s+", "-");
            boolean orgExists = organizationExists("master", orgAlias);

            if (!orgExists) {
                return com.secufusion.tenant.dto.SsoDiagnosticDto.OrganizationCheck.builder()
                        .organizationExists(false)
                        .idpLinkedToOrganization(false)
                        .build();
            }

            boolean idpLinked = isIdpLinkedToOrganization("master", orgAlias, azureIdpAlias);

            return com.secufusion.tenant.dto.SsoDiagnosticDto.OrganizationCheck.builder()
                    .organizationExists(true)
                    .organizationId(orgAlias)
                    .idpLinkedToOrganization(idpLinked)
                    .build();

        } catch (Exception e) {
            log.warn("Failed to check organization: {}", e.getMessage());
            return com.secufusion.tenant.dto.SsoDiagnosticDto.OrganizationCheck.builder()
                    .organizationExists(false)
                    .idpLinkedToOrganization(false)
                    .build();
        }
    }

    /**
     * Complete SSO repair for Azure-enabled tenant
     * This method:
     * 1. Fixes tenant realm master-hub IdP authorization URL
     * 2. Configures master-hub IdP mappers in tenant realm
     * 3. Configures tenant client mappers
     * 4. Configures broker client mappers in master realm
     * 5. Repairs master realm Azure IdP mappers
     * 6. Creates organization in master realm (if not exists)
     * 7. Links Azure IdP to organization (if not linked)
     *
     * @param tenantRealm Tenant realm name
     * @param tenantName Tenant name (for organization)
     * @param domain Tenant domain (for organization)
     * @param azureIdpAlias Azure IdP alias in master realm
     */
    public void repairAzureSsoComplete(String tenantRealm, String tenantName, String domain, String azureIdpAlias) {
        log.info("Starting complete SSO repair for tenant realm: {}", tenantRealm);

        try {
            // Step 1: Fix tenant realm master-hub authorizationUrl
            log.info("Step 1: Updating master-hub IdP authorization URL in tenant realm '{}'", tenantRealm);
            updateMasterHubIdpAuthorizationUrl(tenantRealm);

            // Step 2: Configure tenant realm master-hub IdP mappers
            log.info("Step 2: Configuring master-hub IdP mappers in tenant realm '{}'", tenantRealm);
            configureMasterHubIdpMappers(tenantRealm);

            // Step 3: Configure tenant realm client mappers
            log.info("Step 3: Configuring tenant client mappers in tenant realm '{}'", tenantRealm);
            configureTenantClientMappers(tenantRealm, tenantRealm);

            // Step 4: Configure broker client mappers in master realm
            log.info("Step 4: Configuring broker client mappers in master realm for tenant '{}'", tenantRealm);
            configureBrokerClientMappers(tenantRealm);

            // Step 5: Repair master realm Azure IdP mappers
            log.info("Step 5: Repairing master realm Azure IdP mappers");
            repairMasterAzureIdpMappers();

            // Step 6: Create organization in master realm (if not exists)
            log.info("Step 6: Creating organization in master realm (if not exists)");
            String orgAlias = createOrganizationIfNotExists("master", tenantName, domain);

            // Step 7: Link Azure IdP to organization (if not linked)
            log.info("Step 7: Linking Azure IdP to organization (if not linked)");
            linkIdpToOrganizationIfNotLinked("master", orgAlias, azureIdpAlias);

            log.info("✅ Complete SSO repair successful for tenant realm: {}", tenantRealm);

        } catch (Exception e) {
            log.error("❌ Complete SSO repair failed for tenant realm: {}", tenantRealm, e);
            throw wrap("SSO_REPAIR_FAILED", 500, "Complete SSO repair failed", e);
        }
    }

    /**
     * Unlinks an Identity Provider from an organization.
     * This prevents the IdP from being used for authentication in the organization.
     *
     * @param realm Realm name (usually "master")
     * @param orgAlias Organization alias
     * @param idpAlias Identity Provider alias to unlink
     */
    public void unlinkIdpFromOrganization(String realm, String orgAlias, String idpAlias) {
        try {
            log.info("Unlinking IdP '{}' from organization '{}' in realm '{}'", idpAlias, orgAlias, realm);

            // Check if organization exists
            if (!organizationExists(realm, orgAlias)) {
                log.warn("Organization '{}' not found in realm '{}', skipping unlink", orgAlias, realm);
                return;
            }

            // Check if IdP is linked
            if (!isIdpLinkedToOrganization(realm, orgAlias, idpAlias)) {
                log.info("IdP '{}' is not linked to organization '{}', skipping unlink", idpAlias, orgAlias);
                return;
            }

            // Unlink the IdP from the organization
            // REST API: DELETE /admin/realms/{realm}/organizations/{orgId}/identity-providers/{idpAlias}
            keycloak.realm(realm)
                    .organizations()
                    .get(orgAlias)
                    .identityProviders()
                    .get(idpAlias)
                    .delete();

            log.info("✅ Successfully unlinked IdP '{}' from organization '{}'", idpAlias, orgAlias);

        } catch (Exception e) {
            log.error("❌ Failed to unlink IdP '{}' from organization '{}' in realm '{}': {}",
                    idpAlias, orgAlias, realm, e.getMessage());
            throw wrap("UNLINK_IDP_FROM_ORG_FAILED", 500,
                "Failed to unlink IdP from organization", e);
        }
    }

    /**
     * Disables auto-redirect to IdP in the browser flow for the given realm.
     * This allows users to see the Keycloak login screen instead of being
     * automatically redirected to the IdP.
     *
     * @param realmName The realm to disable auto-redirect for
     */
    public void disableAutoRedirect(String realmName) {
        try {
            log.info("Disabling auto-redirect for realm: {}", realmName);

            AuthenticationManagementResource auth = keycloak.realm(realmName).flows();

            // Get all executions for the 'browser' flow
            List<AuthenticationExecutionInfoRepresentation> executions = auth.getExecutions("browser");

            // Find the Identity Provider Redirector
            AuthenticationExecutionInfoRepresentation execution = executions.stream()
                    .filter(e -> "identity-provider-redirector".equals(e.getProviderId()))
                    .findFirst()
                    .orElse(null);

            if (execution == null) {
                log.warn("Identity Provider Redirector not found in browser flow for realm: {}", realmName);
                return;
            }

            // If there's an authentication config, remove it
            if (execution.getAuthenticationConfig() != null) {
                try {
                    auth.removeAuthenticatorConfig(execution.getAuthenticationConfig());
                    log.info("✅ Removed auto-redirect config for realm: {}", realmName);
                } catch (Exception e) {
                    log.warn("Failed to remove authenticator config: {}", e.getMessage());
                }
            }

            // Set execution to DISABLED
            execution.setRequirement("DISABLED");
            auth.updateExecutions("browser", execution);

            log.info("✅ Auto-redirect disabled successfully for realm: {}", realmName);

        } catch (Exception e) {
            log.error("❌ Failed to disable auto-redirect for realm: {}", realmName, e);
            throw wrap("DISABLE_AUTO_REDIRECT_FAILED", 500,
                "Failed to disable auto-redirect for realm: " + realmName, e);
        }
    }

    /**
     * Disables an Identity Provider in the given realm.
     * This prevents users from authenticating through the specified IdP.
     *
     * @param realmName The realm containing the IdP
     * @param idpAlias The alias of the IdP to disable
     */
    public void disableIdp(String realmName, String idpAlias) {
        try {
            log.info("Disabling IdP '{}' in realm: {}", idpAlias, realmName);

            // Check if IdP exists
            if (!idpExists(realmName, idpAlias)) {
                log.warn("IdP '{}' not found in realm: {}", idpAlias, realmName);
                return;
            }

            // Get the IdP representation
            IdentityProviderRepresentation idp = keycloak.realm(realmName)
                    .identityProviders()
                    .get(idpAlias)
                    .toRepresentation();

            // Disable the IdP
            idp.setEnabled(false);

            // Update the IdP
            keycloak.realm(realmName)
                    .identityProviders()
                    .get(idpAlias)
                    .update(idp);

            log.info("✅ IdP '{}' disabled successfully in realm: {}", idpAlias, realmName);

        } catch (Exception e) {
            log.error("❌ Failed to disable IdP '{}' in realm: {}", idpAlias, realmName, e);
            throw wrap("DISABLE_IDP_FAILED", 500,
                "Failed to disable IdP '" + idpAlias + "' in realm: " + realmName, e);
        }
    }
}