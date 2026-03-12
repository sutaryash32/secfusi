package com.secufusion.iam.service;

import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.microsoft.graph.core.tasks.PageIterator;
import com.microsoft.graph.models.*;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import com.secufusion.iam.dto.AzureResourceDto;
import com.secufusion.iam.entity.SsoConfiguration;
import com.secufusion.iam.entity.Tenant;
import com.secufusion.iam.exception.*;
import com.secufusion.iam.repository.SsoConfigurationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class AzureGraphService {

    private final SsoConfigurationRepository ssoRepository;

    @Value("${AZURE_CLIENT_ID:}")
    private String configClientId;
    @Value("${AZURE_CLIENT_SECRET:}")
    private String configClientSecret;
    /**
     * Fetch App Roles defined in the Azure App Registration
     */
    public List<AzureResourceDto> getApplicationRoles(String azureTenantId, Tenant tenant) {
        // Validate tenant input
        if (tenant == null || tenant.getTenantID() == null) {
            log.error("Tenant or Tenant ID is null");
            throw new BadRequestException("Tenant cannot be null");
        }
        validateAzureAccess(tenant, azureTenantId);

        try {
            GraphServiceClient graphClient = getGraphClientByAzureTenantId(azureTenantId);

            SsoConfiguration config = ssoRepository.findByFkTenantId(tenant.getTenantID())
                    .stream()
                    .filter(c -> "ACTIVE".equalsIgnoreCase(c.getActive()))
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("No active Azure config"));

            // v6 Syntax: requestConfiguration lambda
            ServicePrincipalCollectionResponse response = graphClient.servicePrincipals().get(requestConfiguration -> {
                requestConfiguration.queryParameters.filter = "appId eq '" + config.getClientId() + "'";
                requestConfiguration.queryParameters.select = new String[]{"id", "appRoles"};
            });

            if (response == null || response.getValue() == null || response.getValue().isEmpty()) {
                return Collections.emptyList();
            }

            ServicePrincipal sp = response.getValue().get(0);

            // v6 uses Getters instead of direct field access
            if (sp.getAppRoles() == null) return Collections.emptyList();

            return sp.getAppRoles().stream()
                    .filter(role -> role != null && Boolean.TRUE.equals(role.getIsEnabled()))
                    .map(role -> new AzureResourceDto(
                            role.getId().toString(),
                            role.getDisplayName(),
                            role.getValue() != null ? role.getValue() : role.getDisplayName(),
                            "ROLE"
                    ))
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("v6 fetch roles failed: {}", e.getMessage());
            throw new ExternalServiceException("Azure AD Error", e);
        }
    }

    /**
     * Fetch Security Groups from the Azure Tenant (Searchable)
     */
    public List<AzureResourceDto> searchTenantGroups(
            Tenant tenant,
            String searchTerm,
            String azureTenantId) {

        if (tenant == null || tenant.getTenantID() == null) {
            throw new BadRequestException("Tenant cannot be null");
        }

        validateAzureAccess(tenant, azureTenantId);

        try {
            GraphServiceClient graphClient = getGraphClientByAzureTenantId(azureTenantId);

            GroupCollectionResponse response = graphClient.groups().get(requestConfiguration -> {
                requestConfiguration.queryParameters.select = new String[]{"id", "displayName"};
                requestConfiguration.queryParameters.top = 999;

                if (searchTerm != null && !searchTerm.isBlank()) {
                    String safe = searchTerm.replace("'", "''");
                    requestConfiguration.queryParameters.filter = "startswith(displayName,'" + safe + "')";
                    // Advanced search requires ConsistencyLevel: eventual
                    requestConfiguration.headers.add("ConsistencyLevel", "eventual");
                }
            });

            List<AzureResourceDto> groups = new ArrayList<>();

            PageIterator<Group, GroupCollectionResponse> iterator = new PageIterator.Builder<Group, GroupCollectionResponse>()
                    .client(graphClient)
                    .collectionPage(response)
                    .collectionPageFactory(GroupCollectionResponse::createFromDiscriminatorValue)
                    .processPageItemCallback(group -> {
                        groups.add(new AzureResourceDto(group.getId(), group.getDisplayName(), group.getId(), "GROUP"));
                        return true;
                    })
                    .build();

            iterator.iterate();
            return groups;

        } catch (Exception e) {
            throw new ExternalServiceException("Failed to fetch groups", e);
        }
    }


    private GraphServiceClient getGraphClientByAzureTenantId(String azureTenantId) {

        // TODO: idp specific config lookup needed here
//        SsoConfiguration config = ssoRepository.findActiveAzureConfig()
//                .orElseThrow(() -> new ResourceNotFoundException("SSO config missing", "404"));

        ClientSecretCredential credential = new ClientSecretCredentialBuilder()
                .tenantId(azureTenantId)
                .clientId(configClientId)
                .clientSecret(configClientSecret)
                .build();

        return new GraphServiceClient(credential, "https://graph.microsoft.com/.default");
    }

    /**
     * Fetch a specific Azure AD group by its ID
     */
    public AzureResourceDto getGroupById(Tenant tenant, String azureGroupId, String azureTenantId) {
        if (tenant == null || tenant.getTenantID() == null) {
            throw new BadRequestException("Tenant cannot be null");
        }
        validateAzureAccess(tenant, azureTenantId);

        try {
            GraphServiceClient graphClient = getGraphClientByAzureTenantId(azureTenantId);

            Group group = graphClient.groups().byGroupId(azureGroupId).get(requestConfiguration -> {
                requestConfiguration.queryParameters.select = new String[]{"id", "displayName"};
            });

            if (group == null) {
                return null;
            }

            return new AzureResourceDto(group.getId(), group.getDisplayName(), group.getId(), "GROUP");

        } catch (Exception e) {
            log.error("Failed to fetch Azure group by ID {}: {}", azureGroupId, e.getMessage());
            throw new ExternalServiceException("Failed to fetch Azure group", e);
        }
    }

    /**
     * Fetch members of a specific Azure AD group
     * Returns email addresses of group members (users only, not nested groups)
     */
    public List<String> getGroupMemberEmails(Tenant tenant, String azureGroupId, String azureTenantId) {
        if (tenant == null || tenant.getTenantID() == null) {
            throw new BadRequestException("Tenant cannot be null");
        }

        validateAzureAccess(tenant, azureTenantId);

        try {
            GraphServiceClient graphClient = getGraphClientByAzureTenantId(azureTenantId);

            DirectoryObjectCollectionResponse response = graphClient
                    .groups()
                    .byGroupId(azureGroupId)
                    .members()
                    .get(requestConfiguration -> {
                        requestConfiguration.queryParameters.select = new String[]{"id", "displayName", "mail", "userPrincipalName"};
                        requestConfiguration.queryParameters.top = 999;
                    });

            List<String> memberEmails = new ArrayList<>();

            if (response != null && response.getValue() != null) {
                PageIterator<DirectoryObject, DirectoryObjectCollectionResponse> iterator =
                        new PageIterator.Builder<DirectoryObject, DirectoryObjectCollectionResponse>()
                                .client(graphClient)
                                .collectionPage(response)
                                .collectionPageFactory(DirectoryObjectCollectionResponse::createFromDiscriminatorValue)
                                .processPageItemCallback(member -> {
                                    if (member instanceof User user) {
                                        // Prefer mail, fallback to userPrincipalName
                                        String email = user.getMail() != null ? user.getMail() : user.getUserPrincipalName();
                                        if (email != null && !email.isBlank()) {
                                            memberEmails.add(email.toLowerCase());
                                        }
                                    }
                                    return true;
                                })
                                .build();

                iterator.iterate();
            }

            log.info("Fetched {} member emails for Azure group: {}", memberEmails.size(), azureGroupId);
            return memberEmails;

        } catch (Exception e) {
            log.error("Failed to fetch group members for group {}: {}", azureGroupId, e.getMessage());
            throw new ExternalServiceException("Failed to fetch group members", e);
        }
    }

    private void validateAzureAccess(Tenant tenant, String azureTenantId) {

        if (tenant == null || tenant.getTenantID() == null) {
            log.info("Tenant or Tenant ID is null during Azure access validation for accessing groups/roles");
            throw new BadRequestException("Tenant cannot be null");
        }

        // Tenant not Azure-enabled → no Graph calls
        if (tenant.getAzureTenantId() == null || tenant.getAzureTenantId().isBlank()) {
            log.info("Tenant '{}' is not Azure-enabled", tenant.getTenantName());
            throw new AccessDeniedException("Tenant is not Azure-enabled");
        }

        // Token missing Azure tenant
        if (azureTenantId == null || azureTenantId.isBlank()) {
            log.info("Tenant '{}' is not Azure-enabled", tenant.getTenantName());
            throw new AccessDeniedException("Azure tenant ID missing in token");
        }

        // Token ↔ DB mismatch
        if (!tenant.getAzureTenantId().equalsIgnoreCase(azureTenantId)) {
            log.error(
                    "Azure tenant mismatch. DB='{}' TOKEN='{}' tenant='{}'",
                    tenant.getAzureTenantId(),
                    azureTenantId,
                    tenant.getTenantName()
            );
            throw new AccessDeniedException("Azure tenant mismatch");
        }
    }



}