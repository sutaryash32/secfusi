package com.secufusion.tenant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SsoDiagnosticDto {

    private String tenantRealm;
    private String tenantName;
    private String domain;
    private String azureIdpAlias;

    @Builder.Default
    private List<String> issues = new ArrayList<>();

    @Builder.Default
    private List<String> warnings = new ArrayList<>();

    @Builder.Default
    private List<String> validConfigurations = new ArrayList<>();

    private boolean requiresRepair;
    private String recommendation;

    // Individual check results
    private MasterHubIdpCheck masterHubIdpCheck;
    private IdpMappersCheck masterHubIdpMappersCheck;
    private ClientMappersCheck tenantClientMappersCheck;
    private ClientMappersCheck brokerClientMappersCheck;
    private AzureIdpMappersCheck masterAzureIdpMappersCheck;
    private OrganizationCheck organizationCheck;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MasterHubIdpCheck {
        private boolean exists;
        private boolean hasCorrectAuthorizationUrl;
        private String currentAuthorizationUrl;
        private String expectedAuthorizationUrl;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IdpMappersCheck {
        private boolean hasAzureTenantIdMapper;
        private boolean hasRolesMapper;
        private boolean hasGroupsMapper;
        private int totalMappers;
        private List<String> missingMappers;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClientMappersCheck {
        private boolean clientExists;
        private boolean hasAzureTenantIdMapper;
        private boolean hasRolesMapper;
        private boolean hasGroupsMapper;
        private int totalMappers;
        private List<String> missingMappers;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AzureIdpMappersCheck {
        private boolean hasRolesMapper;
        private boolean hasGroupsMapper;
        private boolean rolesMapperHasCorrectSyncMode;
        private boolean groupsMapperHasCorrectSyncMode;
        private int totalMappers;
        private List<String> issues;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrganizationCheck {
        private boolean organizationExists;
        private String organizationId;
        private boolean idpLinkedToOrganization;
    }
}
