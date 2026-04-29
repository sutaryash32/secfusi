package com.secufusion.events.service;

import com.secufusion.events.util.CryptoUtil;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.ClientResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * KeycloakClientService
 *
 * Wrapper service for Keycloak client operations.
 * Provides methods for client management and secret encryption/decryption.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KeycloakClientService {

    private final Keycloak keycloak;

    /**
     * Check if Keycloak client exists
     */
    public boolean clientExists(String realm, String clientId) {
        try {
            List<ClientRepresentation> clients = keycloak.realm(realm)
                .clients()
                .findByClientId(clientId);
            return clients != null && !clients.isEmpty();
        } catch (Exception e) {
            log.error("Failed to check client existence: realm={}, clientId={}", realm, clientId, e);
            return false;
        }
    }

    /**
     * Get client with secret from Keycloak
     */
    public ClientRepresentation getClientWithSecret(String realm, String clientId) {
        try {
            List<ClientRepresentation> clients = keycloak.realm(realm)
                .clients()
                .findByClientId(clientId);

            if (clients == null || clients.isEmpty()) {
                throw new RuntimeException("Client not found: " + clientId);
            }

            ClientRepresentation client = clients.get(0);

            // Fetch the actual secret via the dedicated endpoint using the internal client UUID
            CredentialRepresentation cred = keycloak.realm(realm)
                .clients()
                .get(client.getId())
                .getSecret();

            if (cred != null) {
                client.setSecret(cred.getValue());
            }

            return client;

        } catch (Exception e) {
            log.error("Failed to fetch client secret: realm={}, clientId={}", realm, clientId, e);
            throw new RuntimeException("CLIENT_SECRET_FETCH_FAILED: " + e.getMessage(), e);
        }
    }

    /**
     * Create Keycloak client
     */
    public void createClient(String realm, ClientRepresentation clientRep) {
        log.info("Creating Keycloak client: realm={}, clientId={}",
            realm, clientRep != null ? clientRep.getClientId() : "null");

        Response resp = null;
        try {
            resp = keycloak.realm(realm).clients().create(clientRep);
            int status = resp.getStatus();

            log.debug("Client creation response status={}", status);

            if (status != 201 && status != 409) {
                String body = resp.readEntity(String.class);
                throw new RuntimeException("Client creation failed: " + body);
            }

            if (status == 409) {
                log.warn("Client already exists: realm={}, clientId={}", realm, clientRep.getClientId());
            } else {
                log.info("Client created in realm={} clientId={}", realm, clientRep.getClientId());
            }

        } catch (Exception e) {
            log.error("Failed to create client: realm={}, clientId={}",
                realm, clientRep != null ? clientRep.getClientId() : "null", e);
            throw new RuntimeException("CLIENT_CREATE_FAILED: " + e.getMessage(), e);

        } finally {
            if (resp != null) {
                try {
                    resp.close();
                } catch (Exception e) {
                    log.warn("Failed to close client creation response", e);
                }
            }
        }
    }

    /**
     * Encrypt client secret for storage using AES-256
     *
     * @param secret Plain text secret
     * @return Base64-encoded encrypted secret
     */
    public String encryptSecret(String secret) {
        return CryptoUtil.encrypt(secret);
    }

    /**
     * Decrypt client secret for use
     *
     * @param encryptedSecret Base64-encoded encrypted secret
     * @return Plain text secret
     */
    public String decryptSecret(String encryptedSecret) {
        return CryptoUtil.decrypt(encryptedSecret);
    }

    /**
     * Regenerate client secret
     */
    public String regenerateClientSecret(String realm, String clientId) {
        try {
            List<ClientRepresentation> clients = keycloak.realm(realm)
                .clients()
                .findByClientId(clientId);

            if (clients == null || clients.isEmpty()) {
                throw new RuntimeException("Client not found: " + clientId);
            }

            ClientRepresentation client = clients.get(0);
            ClientResource clientResource = keycloak.realm(realm)
                .clients()
                .get(client.getId());

            // Regenerate secret
            CredentialRepresentation newCred = clientResource.generateNewSecret();

            log.info("Regenerated client secret: realm={}, clientId={}", realm, clientId);

            return newCred.getValue();

        } catch (Exception e) {
            log.error("Failed to regenerate client secret: realm={}, clientId={}", realm, clientId, e);
            throw new RuntimeException("CLIENT_SECRET_REGENERATE_FAILED: " + e.getMessage(), e);
        }
    }
}
