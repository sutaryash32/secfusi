package com.secufusion.events.service;

import com.secufusion.events.util.CryptoUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.ClientResource;
import org.keycloak.admin.client.resource.ClientsResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import jakarta.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("KeycloakClientService Tests")
class KeycloakClientServiceTest {

    @Mock
    private Keycloak keycloak;

    @InjectMocks
    private KeycloakClientService service;

    private static final String REALM = "test-realm";
    private static final String CLIENT_ID = "test-client";
    private static final String SECRET = "plain-secret";
    private static final String ENCRYPTED_SECRET = "encrypted-secret";

    private MockedStatic<CryptoUtil> cryptoUtilMock;

    @BeforeEach
    void setUp() {
        cryptoUtilMock = mockStatic(CryptoUtil.class);
        // Stub only the exact arguments we need – no lenient generic fallbacks
        cryptoUtilMock.when(() -> CryptoUtil.encrypt(SECRET)).thenReturn(ENCRYPTED_SECRET);
        cryptoUtilMock.when(() -> CryptoUtil.decrypt(ENCRYPTED_SECRET)).thenReturn(SECRET);
        // No generic stubs – they would shadow the specific ones
    }

    @AfterEach
    void tearDown() {
        cryptoUtilMock.close();
    }

    // ==================== clientExists ====================
    @Nested
    @DisplayName("clientExists")
    class ClientExistsTests {

        @Test
        @DisplayName("Happy Path — client found")
        void happyPath_clientFound() {
            // ARRANGE
            RealmResource realmResource = mock(RealmResource.class);
            ClientsResource clientsResource = mock(ClientsResource.class);
            ClientRepresentation clientRep = new ClientRepresentation();
            clientRep.setClientId(CLIENT_ID);
            when(keycloak.realm(REALM)).thenReturn(realmResource);
            when(realmResource.clients()).thenReturn(clientsResource);
            when(clientsResource.findByClientId(CLIENT_ID)).thenReturn(List.of(clientRep));

            // ACT
            boolean exists = service.clientExists(REALM, CLIENT_ID);

            // ASSERT
            assertTrue(exists);
            verify(clientsResource).findByClientId(CLIENT_ID);
        }

        @Test
        @DisplayName("Happy Path — client not found (empty list)")
        void happyPath_clientNotFound_emptyList() {
            // ARRANGE
            RealmResource realmResource = mock(RealmResource.class);
            ClientsResource clientsResource = mock(ClientsResource.class);
            when(keycloak.realm(REALM)).thenReturn(realmResource);
            when(realmResource.clients()).thenReturn(clientsResource);
            when(clientsResource.findByClientId(CLIENT_ID)).thenReturn(Collections.emptyList());

            // ACT
            boolean exists = service.clientExists(REALM, CLIENT_ID);

            // ASSERT
            assertFalse(exists);
        }

        @Test
        @DisplayName("Happy Path — client not found (null list)")
        void happyPath_clientNotFound_nullList() {
            // ARRANGE
            RealmResource realmResource = mock(RealmResource.class);
            ClientsResource clientsResource = mock(ClientsResource.class);
            when(keycloak.realm(REALM)).thenReturn(realmResource);
            when(realmResource.clients()).thenReturn(clientsResource);
            when(clientsResource.findByClientId(CLIENT_ID)).thenReturn(null);

            // ACT
            boolean exists = service.clientExists(REALM, CLIENT_ID);

            // ASSERT
            assertFalse(exists);
        }

        @Test
        @DisplayName("Sad Path — exception thrown, returns false")
        void sadPath_exceptionReturnsFalse() {
            // ARRANGE
            when(keycloak.realm(REALM)).thenThrow(new RuntimeException("Keycloak down"));

            // ACT
            boolean exists = service.clientExists(REALM, CLIENT_ID);

            // ASSERT
            assertFalse(exists);
        }
    }

    // ==================== getClientWithSecret ====================
    @Nested
    @DisplayName("getClientWithSecret")
    class GetClientWithSecretTests {

        @Test
        @DisplayName("Happy Path — secret retrieved and set on client")
        void happyPath() {
            // ARRANGE
            String clientUuid = UUID.randomUUID().toString();
            ClientRepresentation clientRep = new ClientRepresentation();
            clientRep.setId(clientUuid);
            clientRep.setClientId(CLIENT_ID);

            RealmResource realmResource = mock(RealmResource.class);
            ClientsResource clientsResource = mock(ClientsResource.class);
            ClientResource clientResource = mock(ClientResource.class);
            CredentialRepresentation credential = new CredentialRepresentation();
            credential.setValue(SECRET);

            when(keycloak.realm(REALM)).thenReturn(realmResource);
            when(realmResource.clients()).thenReturn(clientsResource);
            when(clientsResource.findByClientId(CLIENT_ID)).thenReturn(List.of(clientRep));
            when(clientsResource.get(clientUuid)).thenReturn(clientResource);
            when(clientResource.getSecret()).thenReturn(credential);

            // ACT
            ClientRepresentation result = service.getClientWithSecret(REALM, CLIENT_ID);

            // ASSERT
            assertNotNull(result);
            assertEquals(CLIENT_ID, result.getClientId());
            assertEquals(SECRET, result.getSecret());
            verify(clientResource).getSecret();
        }

        @Test
        @DisplayName("Sad Path — client not found throws RuntimeException")
        void sadPath_clientNotFound() {
            // ARRANGE
            RealmResource realmResource = mock(RealmResource.class);
            ClientsResource clientsResource = mock(ClientsResource.class);
            when(keycloak.realm(REALM)).thenReturn(realmResource);
            when(realmResource.clients()).thenReturn(clientsResource);
            when(clientsResource.findByClientId(CLIENT_ID)).thenReturn(Collections.emptyList());

            // ACT + ASSERT
            assertThrows(RuntimeException.class, () -> service.getClientWithSecret(REALM, CLIENT_ID));
        }

        @Test
        @DisplayName("Sad Path — Keycloak exception throws RuntimeException")
        void sadPath_keycloakException() {
            // ARRANGE
            when(keycloak.realm(REALM)).thenThrow(new RuntimeException("Keycloak unavailable"));

            // ACT + ASSERT
            assertThrows(RuntimeException.class, () -> service.getClientWithSecret(REALM, CLIENT_ID));
        }
    }

    // ==================== createClient ====================
    @Nested
    @DisplayName("createClient")
    class CreateClientTests {

        @Test
        @DisplayName("Happy Path — 201 Created")
        void happyPath_201() {
            // ARRANGE
            ClientRepresentation clientRep = new ClientRepresentation();
            clientRep.setClientId(CLIENT_ID);

            RealmResource realmResource = mock(RealmResource.class);
            ClientsResource clientsResource = mock(ClientsResource.class);
            Response response = mock(Response.class);
            when(keycloak.realm(REALM)).thenReturn(realmResource);
            when(realmResource.clients()).thenReturn(clientsResource);
            when(clientsResource.create(clientRep)).thenReturn(response);
            when(response.getStatus()).thenReturn(201);

            // ACT & ASSERT (no exception expected)
            assertDoesNotThrow(() -> service.createClient(REALM, clientRep));
            verify(response).close();
        }

        @Test
        @DisplayName("Happy Path — 409 Conflict (already exists) logs warning")
        void happyPath_409() {
            ClientRepresentation clientRep = new ClientRepresentation();
            clientRep.setClientId(CLIENT_ID);

            RealmResource realmResource = mock(RealmResource.class);
            ClientsResource clientsResource = mock(ClientsResource.class);
            Response response = mock(Response.class);
            when(keycloak.realm(REALM)).thenReturn(realmResource);
            when(realmResource.clients()).thenReturn(clientsResource);
            when(clientsResource.create(clientRep)).thenReturn(response);
            when(response.getStatus()).thenReturn(409);

            assertDoesNotThrow(() -> service.createClient(REALM, clientRep));
            verify(response).close();
        }

        @Test
        @DisplayName("Sad Path — non-2xx status throws RuntimeException")
        void sadPath_invalidStatus() {
            ClientRepresentation clientRep = new ClientRepresentation();
            clientRep.setClientId(CLIENT_ID);

            RealmResource realmResource = mock(RealmResource.class);
            ClientsResource clientsResource = mock(ClientsResource.class);
            Response response = mock(Response.class);
            when(keycloak.realm(REALM)).thenReturn(realmResource);
            when(realmResource.clients()).thenReturn(clientsResource);
            when(clientsResource.create(clientRep)).thenReturn(response);
            when(response.getStatus()).thenReturn(400);
            when(response.readEntity(String.class)).thenReturn("Bad request");

            assertThrows(RuntimeException.class, () -> service.createClient(REALM, clientRep));
            verify(response).close();
        }

        @Test
        @DisplayName("Sad Path — Keycloak exception throws RuntimeException")
        void sadPath_keycloakException() {
            ClientRepresentation clientRep = new ClientRepresentation();
            when(keycloak.realm(REALM)).thenThrow(new RuntimeException("Keycloak unavailable"));

            assertThrows(RuntimeException.class, () -> service.createClient(REALM, clientRep));
            // no response to close
        }
    }

    // ==================== encryptSecret / decryptSecret ====================
    @Nested
    @DisplayName("encryptSecret / decryptSecret")
    class CryptoOperationsTests {

        @Test
        @DisplayName("encryptSecret returns encrypted value")
        void encryptSecret() {
            String result = service.encryptSecret(SECRET);
            assertEquals(ENCRYPTED_SECRET, result);
        }

        @Test
        @DisplayName("decryptSecret returns plain text")
        void decryptSecret() {
            String result = service.decryptSecret(ENCRYPTED_SECRET);
            assertEquals(SECRET, result);
        }
    }

    // ==================== regenerateClientSecret ====================
    @Nested
    @DisplayName("regenerateClientSecret")
    class RegenerateClientSecretTests {

        @Test
        @DisplayName("Happy Path — new secret generated and returned")
        void happyPath() {
            String clientUuid = UUID.randomUUID().toString();
            ClientRepresentation clientRep = new ClientRepresentation();
            clientRep.setId(clientUuid);
            clientRep.setClientId(CLIENT_ID);

            RealmResource realmResource = mock(RealmResource.class);
            ClientsResource clientsResource = mock(ClientsResource.class);
            ClientResource clientResource = mock(ClientResource.class);
            CredentialRepresentation newCred = new CredentialRepresentation();
            newCred.setValue("new-secret");

            when(keycloak.realm(REALM)).thenReturn(realmResource);
            when(realmResource.clients()).thenReturn(clientsResource);
            when(clientsResource.findByClientId(CLIENT_ID)).thenReturn(List.of(clientRep));
            when(clientsResource.get(clientUuid)).thenReturn(clientResource);
            when(clientResource.generateNewSecret()).thenReturn(newCred);

            String result = service.regenerateClientSecret(REALM, CLIENT_ID);
            assertEquals("new-secret", result);
            verify(clientResource).generateNewSecret();
        }

        @Test
        @DisplayName("Sad Path — client not found throws RuntimeException")
        void sadPath_clientNotFound() {
            RealmResource realmResource = mock(RealmResource.class);
            ClientsResource clientsResource = mock(ClientsResource.class);
            when(keycloak.realm(REALM)).thenReturn(realmResource);
            when(realmResource.clients()).thenReturn(clientsResource);
            when(clientsResource.findByClientId(CLIENT_ID)).thenReturn(Collections.emptyList());

            assertThrows(RuntimeException.class, () -> service.regenerateClientSecret(REALM, CLIENT_ID));
        }

        @Test
        @DisplayName("Sad Path — Keycloak exception throws RuntimeException")
        void sadPath_keycloakException() {
            when(keycloak.realm(REALM)).thenThrow(new RuntimeException("Keycloak unavailable"));
            assertThrows(RuntimeException.class, () -> service.regenerateClientSecret(REALM, CLIENT_ID));
        }
    }
}