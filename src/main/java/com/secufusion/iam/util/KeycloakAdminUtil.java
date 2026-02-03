package com.secufusion.iam.util;

import com.secufusion.iam.dto.CreateIdentityProviderRequest;
import com.secufusion.iam.entity.Tenant;
import com.secufusion.iam.exception.KeycloakOperationException;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
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
import java.util.function.Predicate;
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

    private String buildAzureRedirectUrl(String realm, String alias) {
        String keycloakBaseUrl = keycloakServerUrl;

        return keycloakBaseUrl + "/realms/" + realm + "/broker/" + alias + "/endpoint";
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
        if (configId == null) {
            cfg.setAlias("idp-redirector-config");
            rr.flows().newExecutionConfig(idpRedirectExec.getId(), cfg);
        } else {
            rr.flows().updateAuthenticatorConfig(configId, cfg);
        }

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

    public void configureBrowserFlowForAutoRedirect(String realmName, String idpAlias) {

        RealmResource rr = keycloak.realm(realmName);

        AuthenticationFlowRepresentation browserFlow = rr.flows()
                .getFlows()
                .stream()
                .filter(f -> "browser".equalsIgnoreCase(f.getAlias()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Browser flow not found"));

        List<AuthenticationExecutionInfoRepresentation> executions =
                rr.flows().getExecutions(browserFlow.getAlias());

        for (AuthenticationExecutionInfoRepresentation exec : executions) {
            if (exec.getRequirement() == null) {
                continue;
            }

            // 1️⃣ Force Identity Provider Redirector
            if ("identity-provider-redirector".equals(exec.getProviderId())
                    && !"REQUIRED".equals(exec.getRequirement())) {

                exec.setRequirement("REQUIRED");
                rr.flows().updateExecutions(browserFlow.getAlias(), exec);

                log.info("[FLOW] IDP Redirector set to REQUIRED | realm={}", realmName);
            }

            // 2️⃣ Disable local username/password
            if ("auth-username-password-form".equals(exec.getProviderId())
                    && !"DISABLED".equals(exec.getRequirement())) {

                exec.setRequirement("DISABLED");
                rr.flows().updateExecutions(browserFlow.getAlias(), exec);

                log.info("[FLOW] Username/password DISABLED | realm={}", realmName);
            }
        }
    }

    public void restoreBrowserFlowToLocalLogin(String realmName) {

        RealmResource rr = keycloak.realm(realmName);

        AuthenticationFlowRepresentation browserFlow = rr.flows()
                .getFlows()
                .stream()
                .filter(f -> "browser".equalsIgnoreCase(f.getAlias()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Browser flow not found"));

        List<AuthenticationExecutionInfoRepresentation> executions =
                rr.flows().getExecutions(browserFlow.getAlias());

        for (AuthenticationExecutionInfoRepresentation exec : executions) {

            if (exec.getRequirement() == null) {
                continue;
            }

            if ("identity-provider-redirector".equals(exec.getProviderId())
                    && !"ALTERNATIVE".equals(exec.getRequirement())) {

                exec.setRequirement("ALTERNATIVE");
                rr.flows().updateExecutions(browserFlow.getAlias(), exec);

                log.info("[FLOW] IDP Redirector reverted to ALTERNATIVE | realm={}", realmName);
            }

            if ("auth-username-password-form".equals(exec.getProviderId())
                    && !"REQUIRED".equals(exec.getRequirement())) {

                exec.setRequirement("REQUIRED");
                rr.flows().updateExecutions(browserFlow.getAlias(), exec);

                log.info("[FLOW] Username/password RESTORED | realm={}", realmName);
            }
        }
    }



// -------------------------------------------------------------------------
// KEYCLOAK UTIL METHODS (Refined)
// -------------------------------------------------------------------------

    public String addIdentityProvider(String realm, CreateIdentityProviderRequest dto, boolean isGatewayRealm) {
        log.info("Adding Azure IdP to realm {} (isGateway={})", realm, isGatewayRealm);

        RealmResource realmResource = keycloak.realm(realm);

        if (isGatewayRealm && "microsoft".equalsIgnoreCase(dto.getProviderId())) {
            // GATEWAY REALM: Create dual IdPs for user isolation
            log.info("Creating dual Azure IdPs for gateway realm: {}", realm);

            // 1. Create custom broker flow (no user creation)
            createBrokerNoUserCreationFlow(realmResource);

            // 2. Create "azure" - Direct login (creates users)
            createAzureIdp(realmResource, dto, "azure", "Azure AD", true);

            // 3. Create "azure-broker" - Broker login (no user creation)
            createAzureIdp(realmResource, dto, "azure-broker", "Azure AD (Broker)", false);

            return buildAzureRedirectUrl(realm, "azure");

        } else {
            // NON-GATEWAY REALM: Single IdP
            log.info("Creating single Azure IdP for non-gateway realm: {}", realm);
            createAzureIdp(realmResource, dto, dto.getAlias(), dto.getDisplayName(), true);

            return buildAzureRedirectUrl(realm, dto.getAlias());
        }
    }

    /**
     * Helper method to create an Azure IdP with configurable user creation behavior
     */
    private void createAzureIdp(
            RealmResource realmResource,
            CreateIdentityProviderRequest dto,
            String alias,
            String displayName,
            boolean createUsers
    ) {
        log.info("Creating Azure IdP '{}' (createUsers={})", alias, createUsers);

        IdentityProviderRepresentation idpRep = new IdentityProviderRepresentation();
        idpRep.setAlias(alias);
        idpRep.setDisplayName(displayName);
        idpRep.setProviderId("microsoft"); // TODO: Use dto.getProviderId() for flexibility
        idpRep.setEnabled(true);
        idpRep.setStoreToken(true);
        idpRep.setTrustEmail(true);

        if (!createUsers) {
            // Use custom flow without user creation
            idpRep.setFirstBrokerLoginFlowAlias("broker-no-user-creation");
            // Note: azure-broker is not set as default, so it won't appear in auto-redirect
            // Users accessing MSSP realm directly will use the "azure" IdP instead
        }

        Map<String, String> config = new HashMap<>();
        config.put("clientId", dto.getClientId());
        config.put("clientSecret", dto.getClientSecret());
        config.put("tenant", "common"); // TODO: Use dto.getTenantId() instead of hardcoded "common"
        config.put("defaultScope", "openid email profile");
        config.put("syncMode", "FORCE");
        config.put("clientAuthMethod", "client_secret_post");

        idpRep.setConfig(config);

        try (Response response = realmResource.identityProviders().create(idpRep)) {
            if (response.getStatus() != 201 && response.getStatus() != 409) {
                throw new RuntimeException("Failed to create Azure IdP '" + alias + "': " + response.getStatusInfo());
            }
        }

        // Configure attribute mappers
        configureOidcMappers(realmResource, realmResource.toRepresentation().getRealm(), alias);

        log.info("Successfully created Azure IdP: {}", alias);
    }

    /**
     * Creates a custom First Broker Login flow without user creation
     * Used for brokered authentication where users should not be created in the gateway realm
     */
    private void createBrokerNoUserCreationFlow(RealmResource realmResource) {
        String flowAlias = "broker-no-user-creation";

        // Check if flow already exists
        boolean flowExists = realmResource.flows().getFlows().stream()
            .anyMatch(f -> flowAlias.equals(f.getAlias()));

        if (flowExists) {
            log.info("Flow '{}' already exists, skipping creation", flowAlias);
            return;
        }

        log.info("Creating custom First Broker Login flow: {}", flowAlias);

        // Create empty authentication flow (no executions = no user creation)
        AuthenticationFlowRepresentation flow = new AuthenticationFlowRepresentation();
        flow.setAlias(flowAlias);
        flow.setDescription("First broker login without user creation (for gateway brokering)");
        flow.setProviderId("basic-flow");
        flow.setTopLevel(true);
        flow.setBuiltIn(false);

        try (Response response = realmResource.flows().createFlow(flow)) {
            if (response.getStatus() != 201) {
                throw new RuntimeException("Failed to create broker flow: " + response.getStatusInfo());
            }
        }

        log.info("Successfully created flow: {}", flowAlias);
    }

    /**
     * Maps external IdP attributes to Keycloak attributes, and then to the Client Token.
     */
    private void configureOidcMappers(RealmResource rr, String realmName, String idpAlias) {
        // Assumption: The frontend client ID matches the realm name (e.g., 'magellanic')
        // If your frontend client ID is different (e.g., 'secufusion-web'), change this variable.
        String targetClientId = realmName;

        log.info("Configuring Mappers. Realm: {}, IdP: {}, TargetClient: {}", realmName, idpAlias, targetClientId);

        try {
            IdentityProviderResource idpRes = rr.identityProviders().get(idpAlias);

            // A. Import from Azure Token (IdP Mappers)
            createIdpAttributeMapper(idpRes, idpAlias, "Import Azure Tenant ID", "tid", "azure_tenant_id");
            createIdpAttributeMapper(idpRes, idpAlias, "Import Azure Roles", "roles", "azure_roles");
            createIdpAttributeMapper(idpRes, idpAlias, "Import Azure Groups", "groups", "azure_groups");

            // B. Export to App Token (Client Mappers)
            ClientsResource clientsRes = rr.clients();
            List<ClientRepresentation> foundClients = clientsRes.findByClientId(targetClientId);

            if (foundClients == null || foundClients.isEmpty()) {
                log.warn("Client '{}' not found. Skipping client mappers.", targetClientId);
                return;
            }

            String internalId = foundClients.get(0).getId();
            ClientResource clientResource = clientsRes.get(internalId);

            // Check for existence before adding
            List<ProtocolMapperRepresentation> currentMappers = clientResource.getProtocolMappers().getMappers();
            Predicate<String> exists = name -> currentMappers.stream().anyMatch(m -> m.getName().equals(name));

            if (!exists.test("Pass Tenant ID")) {
                createClientProtocolMapper(clientResource, "Pass Tenant ID", "azure_tenant_id", "azure_tenant_id", "String", false);
            }
            if (!exists.test("Pass Roles")) {
                createClientProtocolMapper(clientResource, "Pass Roles", "azure_roles", "roles", "String", true);
            }
            if (!exists.test("Pass Groups")) {
                createClientProtocolMapper(clientResource, "Pass Groups", "azure_groups", "groups", "String", true);
            }

        } catch (Exception e) {
            log.error("Failed to configure mappers for {}: {}", idpAlias, e.getMessage());
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

    public void linkTenantToGatewayRealm(Tenant tenant, String gatewayRealmName) {
        log.info("Initiating SSO Link. Child: {}, Gateway: {}", tenant.getRealmName(), gatewayRealmName);

        RealmResource gatewayRealmRes = keycloak.realm(gatewayRealmName);
        RealmResource tenantRealmRes = keycloak.realm(tenant.getRealmName());

        String idpAlias = "parent-gateway";
        String idpDisplayName = "Login via " + gatewayRealmName;
        String brokerClientId = "broker-for-" + tenant.getRealmName();

        // Child's callback URL
        String tenantRedirectUri = keycloakServerUrl + "/realms/" + tenant.getRealmName() + "/broker/" + idpAlias + "/endpoint";

        // 1. Create Client in PARENT Realm (The "Broker" client)
        List<ClientRepresentation> existingClients = gatewayRealmRes.clients().findByClientId(brokerClientId);
        if (existingClients.isEmpty()) {
            ClientRepresentation gatewayClient = new ClientRepresentation();
            gatewayClient.setClientId(brokerClientId);
            gatewayClient.setName("Broker for " + tenant.getTenantName());
            gatewayClient.setProtocol("openid-connect");
            gatewayClient.setPublicClient(false);
            gatewayClient.setBearerOnly(false);
            gatewayClient.setServiceAccountsEnabled(false);
            gatewayClient.setStandardFlowEnabled(true);
            gatewayClient.setDirectAccessGrantsEnabled(true);
            gatewayClient.setRedirectUris(List.of(tenantRedirectUri));
            gatewayClient.setEnabled(true);

            try (Response response = gatewayRealmRes.clients().create(gatewayClient)) {
                if (response.getStatus() != 201) {
                    throw new RuntimeException("Failed to create Gateway Client: " + response.getStatusInfo());
                }
            }
            // Add mapper to pass the 'azure_tenant_id' from Parent -> Child
            addGatewayClientMapper(gatewayRealmRes, brokerClientId);
        }

        // 2. Fetch Secret
        String clientSecret = getClientSecret(gatewayRealmRes, brokerClientId);

        // 3. Create IdP in CHILD Realm
        try {
            tenantRealmRes.identityProviders().get(idpAlias).toRepresentation();
            log.info("SSO Link already exists.");
        } catch (NotFoundException e) {
            IdentityProviderRepresentation idp = new IdentityProviderRepresentation();
            idp.setAlias(idpAlias);
            idp.setDisplayName(idpDisplayName);
            idp.setProviderId("keycloak-oidc"); // Keycloak specific OIDC
            idp.setEnabled(true);
            idp.setStoreToken(true);
            idp.setTrustEmail(true);

            Map<String, String> config = new HashMap<>();
            config.put("clientId", brokerClientId);
            config.put("clientSecret", clientSecret);

            String gatewayBase = keycloakServerUrl + "/realms/" + gatewayRealmName;

            // Check if gateway realm has azure-broker IdP
            boolean hasAzureBroker = checkIfIdpExists(gatewayRealmRes, "azure-broker");

            if (hasAzureBroker) {
                // Use azure-broker to prevent user creation in gateway realm
                config.put("authorizationUrl", gatewayBase + "/protocol/openid-connect/auth?kc_idp_hint=azure-broker");
                log.info("Gateway realm '{}' has azure-broker, using kc_idp_hint", gatewayRealmName);
            } else {
                // Fallback to default (backward compatibility for pre-existing setups)
                config.put("authorizationUrl", gatewayBase + "/protocol/openid-connect/auth");
                log.warn("Gateway realm '{}' does NOT have azure-broker IdP yet", gatewayRealmName);
            }

            config.put("tokenUrl", gatewayBase + "/protocol/openid-connect/token");
            config.put("userInfoUrl", gatewayBase + "/protocol/openid-connect/userinfo");
            config.put("jwksUrl", gatewayBase + "/protocol/openid-connect/certs");
            config.put("issuer", gatewayBase);
            config.put("forwardedQueryParameters", "login_hint");

            idp.setConfig(config);
            tenantRealmRes.identityProviders().create(idp);

            // Add IdP Mapper to Child to receive the tenant ID
            addTenantIdpMapper(tenantRealmRes, idpAlias);

            log.info("Successfully linked tenant '{}' to gateway '{}'", tenant.getTenantName(), gatewayRealmName);
        }
    }

    /**
     * IMPLEMENTED: Helper to add a mapper to the Child's IdP.
     * It reads 'azure_tenant_id' from the Parent's token and saves it to the Child's user attribute.
     */
    private void addTenantIdpMapper(RealmResource realmRes, String idpAlias) {
        try {
            IdentityProviderResource idpRes = realmRes.identityProviders().get(idpAlias);

            IdentityProviderMapperRepresentation mapper = new IdentityProviderMapperRepresentation();
            mapper.setName("Import Parent Tenant ID");
            mapper.setIdentityProviderAlias(idpAlias);
            mapper.setIdentityProviderMapper("oidc-user-attribute-idp-mapper");

            mapper.setConfig(Map.of(
                    "claim", "azure_tenant_id",       // The claim coming from the Parent Gateway
                    "user.attribute", "azure_tenant_id", // Where to store it in the Child User
                    "syncMode", "FORCE"
            ));

            idpRes.addMapper(mapper);
        } catch (Exception e) {
            log.error("Failed to add Tenant ID mapper to child IdP: {}", e.getMessage());
        }
    }

    /**
     * Adds a mapper to the PARENT Client to export 'azure_tenant_id' into the token sent to the child.
     */
    private void addGatewayClientMapper(RealmResource gatewayRes, String clientId) {
        try {
            String internalId = gatewayRes.clients().findByClientId(clientId).get(0).getId();
            ClientResource clientRes = gatewayRes.clients().get(internalId);

            ProtocolMapperRepresentation mapper = new ProtocolMapperRepresentation();
            mapper.setName("Pass Tenant ID");
            mapper.setProtocol("openid-connect");
            mapper.setProtocolMapper("oidc-usermodel-attribute-mapper");

            Map<String, String> config = new HashMap<>();
            config.put("user.attribute", "azure_tenant_id");
            config.put("claim.name", "azure_tenant_id");
            config.put("jsonType.label", "String");
            config.put("id.token.claim", "true");
            config.put("access.token.claim", "true");

            mapper.setConfig(config);
            clientRes.getProtocolMappers().createMapper(mapper);
        } catch (Exception e) {
            log.error("Failed to add gateway client mapper", e);
        }
    }

    /**
     * Retrieves the Client Secret for a specific client in a realm.
     */
    private String getClientSecret(RealmResource realmRes, String clientId) {
        try {
            // 1. Find the internal UUID of the client
            List<ClientRepresentation> clients = realmRes.clients().findByClientId(clientId);
            if (clients.isEmpty()) return null;

            String internalId = clients.get(0).getId();

            // 2. Fetch the secret
            return realmRes.clients().get(internalId).getSecret().getValue();
        } catch (Exception e) {
            log.error("Failed to retrieve client secret for client {}", clientId, e);
            return null;
        }
    }

    /**
     * Removes an Identity Provider from a realm.
     * Used during re-linking to clear old gateway connections.
     */
    public void removeIdentityProvider(String realmName, String alias) {
        try {
            RealmResource realmRes = keycloak.realm(realmName);
            realmRes.identityProviders().get(alias).remove();
            log.info("Removed Identity Provider '{}' from realm '{}'", alias, realmName);
        } catch (NotFoundException e) {
            // It's fine if it doesn't exist
            log.warn("IdP '{}' not found in realm '{}', skipping removal.", alias, realmName);
        } catch (Exception e) {
            log.error("Failed to remove IdP '{}' from realm '{}'", alias, realmName, e);
            throw new RuntimeException("Failed to remove IdP", e);
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

    /**
     * Checks if an Identity Provider exists in a realm
     * @param realm The RealmResource to check
     * @param alias The IdP alias to check for
     * @return true if the IdP exists, false otherwise
     */
    private boolean checkIfIdpExists(RealmResource realm, String alias) {
        try {
            realm.identityProviders().get(alias).toRepresentation();
            return true;
        } catch (NotFoundException e) {
            return false;
        } catch (Exception e) {
            log.error("Error checking if IdP '{}' exists: {}", alias, e.getMessage());
            return false;
        }
    }

}