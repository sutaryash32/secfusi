package com.secufusion.iam.service;

import com.azure.core.exception.AzureException;
import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.microsoft.graph.models.ServicePrincipal;
import com.microsoft.graph.models.ServicePrincipalCollectionResponse;
import com.microsoft.graph.models.GroupCollectionResponse;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import com.secufusion.iam.dto.AzureResourceDto;
import com.secufusion.iam.entity.SsoConfiguration;
import com.secufusion.iam.entity.Tenant;
import com.secufusion.iam.exception.BadRequestException;
import com.secufusion.iam.exception.ExternalServiceException;
import com.secufusion.iam.exception.ResourceNotFoundException;
import com.secufusion.iam.exception.ValidationException;
import com.secufusion.iam.repository.SsoConfigurationRepository;
import com.secufusion.iam.util.ResponseCodes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class AzureGraphService {

    private final SsoConfigurationRepository ssoRepository;

    /**
     * Helper to get authenticated Graph Client using Tenant's stored config.
     */
    private GraphServiceClient getGraphClientForTenant(Tenant tenant) {
        // Validate tenant input
        if (tenant == null || tenant.getTenantID() == null) {
            log.error("Tenant or Tenant ID is null");
            throw new BadRequestException("Tenant cannot be null");
        }

        try {
            log.debug("Fetching Azure SSO configuration for tenant: {}", tenant.getTenantID());

            // 1. Fetch the ACTIVE Azure configuration for this tenant
            SsoConfiguration config = ssoRepository.findByFkTenantId(tenant.getTenantID())
                    .stream()
                    .filter(c -> "ACTIVE".equalsIgnoreCase(c.getActive()))
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "No active Azure SSO configuration found for tenant: " + tenant.getTenantID(),
                            ResponseCodes.AUTH_PROVIDER_CONFIG_MISSING));

            // 2. Validate credentials
            if (config.getClientId() == null || config.getClientId().isBlank()) {
                log.error("ClientID is missing in SSO configuration for tenant: {}", tenant.getTenantID());
                throw new ValidationException("Azure SSO ClientID is required but missing");
            }
            if (config.getClientSecret() == null || config.getClientSecret().isBlank()) {
                log.error("ClientSecret is missing in SSO configuration for tenant: {}", tenant.getTenantID());
                throw new ValidationException("Azure SSO ClientSecret is required but missing");
            }
            if (config.getTenantId() == null || config.getTenantId().isBlank()) {
                log.error("Azure TenantId is missing in SSO configuration for tenant: {}", tenant.getTenantID());
                throw new ValidationException("Azure TenantId is required but missing");
            }

            log.debug("Building Azure credentials for tenant: {} with clientId: {}", tenant.getTenantID(), config.getClientId());

            // 3. Build Azure Credential
            ClientSecretCredential credential = new ClientSecretCredentialBuilder()
                    .clientId(config.getClientId())
                    .clientSecret(config.getClientSecret())
                    .tenantId(config.getTenantId())
                    .build();

            // 4. Return Graph Client (SDK v6)
            return new GraphServiceClient(credential, "https://graph.microsoft.com/.default");

        } catch (ResourceNotFoundException | ValidationException | BadRequestException e) {
            // Re-throw our custom exceptions
            throw e;
        } catch (DataAccessException e) {
            log.error("Database error while fetching SSO configuration for tenant {}: {}", tenant.getTenantID(), e.getMessage(), e);
            throw new ExternalServiceException("Failed to retrieve SSO configuration from database", e);
        } catch (AzureException e) {
            log.error("Azure authentication error for tenant {}: {}", tenant.getTenantID(), e.getMessage(), e);
            throw new ExternalServiceException("Failed to authenticate with Azure AD: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error creating Graph Client for tenant {}: {}", tenant.getTenantID(), e.getMessage(), e);
            throw new ExternalServiceException("Failed to initialize Azure Graph Client: " + e.getMessage(), e);
        }
    }

    /**
     * Fetch App Roles defined in the Azure App Registration
     */
    public List<AzureResourceDto> getApplicationRoles(Tenant tenant) {
        // Validate tenant input
        if (tenant == null || tenant.getTenantID() == null) {
            log.error("Tenant or Tenant ID is null");
            throw new BadRequestException("Tenant cannot be null");
        }

        try {
            log.info("Fetching Azure App Roles for tenant: {}", tenant.getTenantID());

            GraphServiceClient graphClient = getGraphClientForTenant(tenant);

            // Fetch config again to get the Client ID
            SsoConfiguration config = ssoRepository.findByFkTenantId(tenant.getTenantID())
                    .stream()
                    .filter(c -> "ACTIVE".equalsIgnoreCase(c.getActive()))
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "No active SSO configuration found for tenant: " + tenant.getTenantID(),
                            ResponseCodes.AUTH_PROVIDER_CONFIG_MISSING));

            if (config.getClientId() == null || config.getClientId().isBlank()) {
                log.error("Client ID is missing in SSO configuration");
                throw new ValidationException("Client ID is missing in SSO configuration");
            }

            log.debug("Querying service principal for Client ID: {}", config.getClientId());

            // Query for service principal by appId
            ServicePrincipalCollectionResponse response = graphClient.servicePrincipals().get(requestConfiguration -> {
                requestConfiguration.queryParameters.filter = "appId eq '" + config.getClientId() + "'";
                requestConfiguration.queryParameters.select = new String[]{"id", "appRoles"};
            });

            if (response == null || response.getValue() == null || response.getValue().isEmpty()) {
                log.warn("Service Principal not found for Client ID: {}. Please ensure the app is registered in Azure AD.",
                        config.getClientId());
                return Collections.emptyList();
            }

            ServicePrincipal sp = response.getValue().get(0);

            if (sp.getAppRoles() == null || sp.getAppRoles().isEmpty()) {
                log.info("No app roles defined for Service Principal with Client ID: {}", config.getClientId());
                return Collections.emptyList();
            }

            List<AzureResourceDto> roles = sp.getAppRoles().stream()
                    .filter(role -> role != null && Boolean.TRUE.equals(role.getIsEnabled()))
                    .filter(role -> role.getId() != null && role.getDisplayName() != null)
                    .map(role -> new AzureResourceDto(
                            role.getId().toString(),
                            role.getDisplayName(),
                            role.getValue() != null ? role.getValue() : role.getDisplayName(),
                            "ROLE"
                    ))
                    .collect(Collectors.toList());

            log.info("Successfully fetched {} app roles for tenant: {}", roles.size(), tenant.getTenantID());
            return roles;

        } catch (ResourceNotFoundException | ValidationException | BadRequestException e) {
            // Re-throw our custom exceptions
            throw e;
        } catch (DataAccessException e) {
            log.error("Database error while fetching app roles for tenant {}: {}", tenant.getTenantID(), e.getMessage(), e);
            throw new ExternalServiceException("Failed to retrieve SSO configuration from database", e);
        } catch (AzureException e) {
            log.error("Azure AD error while fetching app roles for tenant {}: {}", tenant.getTenantID(), e.getMessage(), e);
            throw new ExternalServiceException("Failed to communicate with Azure AD: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error fetching App Roles for tenant {}: {}", tenant.getTenantID(), e.getMessage(), e);
            throw new ExternalServiceException("Error communicating with Azure AD: " + e.getMessage(), e);
        }
    }

    /**
     * Fetch Security Groups from the Azure Tenant (Searchable)
     */
    public List<AzureResourceDto> searchTenantGroups(Tenant tenant, String searchTerm) {
        // Validate tenant input
        if (tenant == null || tenant.getTenantID() == null) {
            log.error("Tenant or Tenant ID is null");
            throw new BadRequestException("Tenant cannot be null");
        }

        try {
            log.info("Searching Azure security groups for tenant: {} with search term: '{}'",
                    tenant.getTenantID(), searchTerm != null ? searchTerm : "none");

            GraphServiceClient graphClient = getGraphClientForTenant(tenant);

            GroupCollectionResponse response;

            if (searchTerm != null && !searchTerm.isBlank()) {
                // Sanitize search term to prevent OData injection
                String sanitizedSearchTerm = searchTerm.replace("'", "''");

                log.debug("Searching for groups with sanitized term: {}", sanitizedSearchTerm);

                // Search query with filter
                response = graphClient.groups().get(requestConfiguration -> {
                    requestConfiguration.queryParameters.filter = "startswith(displayName, '" + sanitizedSearchTerm + "')";
                    requestConfiguration.queryParameters.select = new String[]{"id", "displayName"};
                    requestConfiguration.queryParameters.top = 20;
                    // ConsistencyLevel header is required for advanced query capabilities
                    requestConfiguration.headers.add("ConsistencyLevel", "eventual");
                });
            } else {
                log.debug("Fetching top 20 groups without search filter");

                // Default: Get top 20 groups
                response = graphClient.groups().get(requestConfiguration -> {
                    requestConfiguration.queryParameters.select = new String[]{"id", "displayName"};
                    requestConfiguration.queryParameters.top = 20;
                });
            }

            if (response == null || response.getValue() == null || response.getValue().isEmpty()) {
                log.info("No groups found for tenant: {}", tenant.getTenantID());
                return Collections.emptyList();
            }

            List<AzureResourceDto> groups = response.getValue().stream()
                    .filter(g -> g != null && g.getId() != null && g.getDisplayName() != null)
                    .map(g -> new AzureResourceDto(
                            g.getId(),
                            g.getDisplayName(),
                            g.getId(), // For Groups, the Value for mapping is usually the ID (UUID)
                            "GROUP"
                    ))
                    .collect(Collectors.toList());

            log.info("Successfully fetched {} groups for tenant: {}", groups.size(), tenant.getTenantID());
            return groups;

        } catch (BadRequestException e) {
            // Re-throw our custom exceptions
            throw e;
        } catch (DataAccessException e) {
            log.error("Database error while searching groups for tenant {}: {}", tenant.getTenantID(), e.getMessage(), e);
            throw new ExternalServiceException("Failed to retrieve SSO configuration from database", e);
        } catch (AzureException e) {
            log.error("Azure AD error while searching groups for tenant {}: {}", tenant.getTenantID(), e.getMessage(), e);
            throw new ExternalServiceException("Failed to communicate with Azure AD: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error searching Groups for tenant {}: {}", tenant.getTenantID(), e.getMessage(), e);
            throw new ExternalServiceException("Error communicating with Azure AD: " + e.getMessage(), e);
        }
    }
}