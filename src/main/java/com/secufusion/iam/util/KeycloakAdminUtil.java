package com.secufusion.iam.util;

import com.secufusion.iam.exception.KeycloakOperationException;
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
     * Check whether a realm exists.
     */
    public boolean realmExists(String realm) {
        try {
            boolean exists = keycloak.realms().findAll().stream()
                    .anyMatch(r -> r.getRealm().equalsIgnoreCase(realm));
            log.debug("Realm exists check: realm={}, exists={}", realm, exists);
            return exists;
        } catch (Exception e) {
            throw wrap("REALM_CHECK_FAILED", 500, "Error checking realm existence for " + realm, e);
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
     * Delete a realm by name.
     */
    public void deleteRealm(String realmName) {
        log.info("Deleting realm: {}", realmName);
        try {
            keycloak.realm(realmName).remove();
            log.info("Realm deleted: {}", realmName);
        } catch (Exception e) {
            throw wrap("REALM_DELETE_FAILED", 500, "Failed to delete realm " + realmName, e);
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
            boolean exists = keycloak.realm(realm).clients().findAll()
                    .stream()
                    .anyMatch(c -> c.getClientId().equalsIgnoreCase(clientId));
            log.debug("Client exists check: realm={}, clientId={}, exists={}", realm, clientId, exists);
            return exists;
        } catch (Exception e) {
            throw wrap("CLIENT_CHECK_FAILED", 500, "Error checking client existence for " + clientId + " in realm " + realm, e);
        }
    }

    /**
     * Create a client in a realm. Allows 201 (created) and 409 (conflict).
     */
    public void createClient(String realm, ClientRepresentation clientRep) {
        log.info("Creating Keycloak client: realm={}, clientId={}", realm, clientRep != null ? clientRep.getClientId() : "null");
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
            throw wrap("CLIENT_CREATE_FAILED", 500, "Exception while creating client " + (clientRep != null ? clientRep.getClientId() : "null") + " in realm " + realm, e);
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
     * Set or reset a user's password.
     */
    public void setPassword(String realm, String userId, String password, boolean temporary) {
        log.info("Setting password for KC user '{}' in realm '{}'", userId, realm);
        try {
            CredentialRepresentation cred = new CredentialRepresentation();
            cred.setType(CredentialRepresentation.PASSWORD);
            cred.setValue(password);
            cred.setTemporary(temporary);
            keycloak.realm(realm).users().get(userId).resetPassword(cred);
            log.debug("Password set for user {}", userId);
        } catch (Exception e) {
            throw wrap("PASSWORD_SET_FAILED", 500, "Failed to set password for KC user " + userId, e);
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
     * Assign all client roles from every client to the given user.
     */
    public void assignAllClientRoles(String realm, String userId) {
        log.info("Assigning all client roles to user {} in realm {}", userId, realm);
        try {
            RealmResource rr = keycloak.realm(realm);
            List<ClientRepresentation> clients = rr.clients().findAll();
            for (ClientRepresentation client : clients) {
                try {
                    List<RoleRepresentation> roles = rr.clients().get(client.getId()).roles().list();
                    if (!roles.isEmpty()) {
                        rr.users().get(userId).roles().clientLevel(client.getId()).add(roles);
                        log.debug("Assigned {} roles from client {} to user {}", roles.size(), client.getClientId(), userId);
                    }
                } catch (Exception e) {
                    // Continue with other clients but log the failure for each client individually
                    log.warn("Failed to assign roles from client {} to user {}: {}", client.getClientId(), userId, e.getMessage(), e);
                }
            }
            log.info("Assigned client roles to user {}", userId);
        } catch (Exception e) {
            throw wrap("ASSIGN_CLIENT_ROLES_FAILED", 500, "Failed assigning client roles to KC user " + userId, e);
        }
    }

    /**
     * Assign realm-admin client role to the user.
     */
    public void assignRealmAdminRole(String realm, String userId) {
        log.info("Assigning realm-admin role to user {} in realm {}", userId, realm);
        try {
            RealmResource rr = keycloak.realm(realm);
            List<ClientRepresentation> found = rr.clients().findByClientId("realm-management");
            if (found == null || found.isEmpty()) {
                throw new KeycloakOperationException("ROLE_LOOKUP_FAILED", 500, "realm-management client not found in realm " + realm);
            }
            ClientRepresentation realmMgmt = found.get(0);
            RoleRepresentation role = rr.clients().get(realmMgmt.getId()).roles().get("realm-admin").toRepresentation();
            rr.users().get(userId).roles().clientLevel(realmMgmt.getId()).add(List.of(role));
            log.info("Assigned realm-admin to user {}", userId);
        } catch (KeycloakOperationException e) {
            throw e;
        } catch (Exception e) {
            throw wrap("ASSIGN_REALM_ADMIN_FAILED", 500, "Failed to assign realm-admin role to user " + userId, e);
        }
    }

    // ============================================================
    // FIND / SEARCH HELPERS
    // ============================================================

    /**
     * Search users by username (case-insensitive).
     */
    public List<UserRepresentation> findUserByUsername(String realm, String username) {
        try {
            return keycloak.realm(realm).users().search(username, true);
        } catch (Exception e) {
            throw wrap("USER_SEARCH_FAILED", 500, "Failed searching user in KC realm=" + realm + " username=" + username, e);
        }
    }

    /**
     * Search users by username or email and return deduped list (by id).
     */
    public List<UserRepresentation> findUsersByUsernameOrEmail(String realm, String username, String email) {
        UsersResource users = keycloak.realm(realm).users();
        List<UserRepresentation> list = new ArrayList<>();
        try {
            if (username != null && !username.isBlank()) list.addAll(users.search(username, true));
            if (email != null && !email.isBlank()) list.addAll(users.search(email, true));
        } catch (Exception e) {
            // Log and wrap - returning empty list may hide issues, so throw to surface the error
            throw wrap("USER_SEARCH_FAILED", 500, "Error searching KC for username/email realm=" + realm + " username=" + username + " email=" + email, e);
        }
        // dedupe by id
        return list.stream().collect(Collectors.toMap(UserRepresentation::getId, u -> u, (a, b) -> a)).values().stream().toList();
    }

    /**
     * Compare DB user id with Keycloak user id.
     */
    public boolean isSameKeycloakUser(String realm, String dbUserId, String kcUserId) {
        boolean same = kcUserId != null && dbUserId != null && kcUserId.equals(dbUserId);
        log.debug("isSameKeycloakUser realm={}, dbUserId={}, kcUserId={}, same={}", realm, dbUserId, kcUserId, same);
        return same;
    }

    // ============================================================
    // EMAIL (moved here from TenantService)
    // ============================================================

    /**
     * Send a simple welcome email. All exceptions are handled and wrapped.
     */
    public void sendWelcomeEmail(String to, String loginUrl, String username) {
        log.info("Sending welcome email to {} with loginUrl={}", to, loginUrl);

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
            msg.setSubject("Welcome to Secufusion");
            msg.setContent(
                    "<h3>Welcome to Secufusion!</h3>" +
                            "<p>Your admin account is ready.</p>" +
                            "<p><b>Login:</b> <a href='" + loginUrl + "'>" + loginUrl + "</a></p>" +
                            "<p><b>Username:</b> " + username + "</p><hr/>",
                    "text/html"
            );

            Transport.send(msg);
            log.info("Welcome email sent to {}", to);
        } catch (MessagingException e) {
            throw wrap("EMAIL_SEND_FAILED", 500, "Failed to send welcome email to " + to, e);
        } catch (Exception e) {
            throw wrap("EMAIL_SEND_FAILED", 500, "Unexpected error while sending welcome email to " + to, e);
        }
    }
}