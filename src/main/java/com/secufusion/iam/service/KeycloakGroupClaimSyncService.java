package com.secufusion.iam.service;

import com.secufusion.iam.entity.EventsGroup;
import com.secufusion.iam.entity.SsoConfiguration;
import com.secufusion.iam.repository.EventsGroupRepository;
import com.secufusion.iam.repository.SsoConfigurationRepository;
import com.secufusion.iam.util.KeycloakAdminUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Keeps the Keycloak Identity Provider's "Essential claim" filter in sync with
 * the set of authorized Azure groups for a tenant.
 *
 * Whenever an Azure group is authorized or unauthorized the claim value regex must be
 * rebuilt so that only users who belong to at least one authorized group can log in
 * through the identity broker.
 *
 * Regex format: .*(groupId1|groupId2|groupId3).*
 * Example:      .*(8fbefc86-...|5ac054dd-...|newGroupId).*
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class KeycloakGroupClaimSyncService {

    private final EventsGroupRepository groupRepository;
    private final SsoConfigurationRepository ssoConfigRepository;
    private final KeycloakAdminUtil keycloakAdminUtil;

    /**
     * Rebuilds and pushes the Keycloak IdP essential claim filter to reflect
     * all currently authorized Azure groups for the tenant.
     *
     * This method is intentionally non-throwing: a Keycloak connectivity failure
     * must not roll back the group authorization that already succeeded in the DB.
     *
     * @param tenantId Tenant ID
     * @param realm    Keycloak realm name (obtained from Tenant entity)
     */
    public void syncEssentialClaim(String tenantId, String realm) {
        log.info("Syncing Keycloak essential claim for tenantId={} realm={}", tenantId, realm);

        // 1. Find the active SSO configuration to resolve the IdP alias
        Optional<SsoConfiguration> ssoConfigOpt = ssoConfigRepository
                .findByFkTenantIdAndActive(tenantId, "ACTIVE");

        if (ssoConfigOpt.isEmpty()) {
            log.warn("No active SSO configuration found for tenantId={}. Skipping essential claim sync.", tenantId);
            return;
        }

        String idpAlias = ssoConfigOpt.get().getAlias();
        if (idpAlias == null || idpAlias.isBlank()) {
            log.warn("Active SSO configuration has no IdP alias for tenantId={}. Skipping essential claim sync.", tenantId);
            return;
        }

        // 2. Get all currently authorized Azure groups (reads DB state AFTER the authorization change)
        List<EventsGroup> authorizedGroups = groupRepository.findByTenantIdAndAuthorizedAndGroupType(
                tenantId, true, EventsGroup.GroupType.AZURE_GROUP);

        // 3. Collect Azure group OIDs — these become the regex alternation values
        List<String> groupIds = authorizedGroups.stream()
                .map(EventsGroup::getAzureGroupId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toList());

        log.info("Found {} authorized Azure group(s) for tenantId={}. Pushing updated claim to Keycloak IdP alias={}.",
                groupIds.size(), tenantId, idpAlias);

        // 4. Push the updated essential claim filter to Keycloak
        try {
            keycloakAdminUtil.updateIdpEssentialClaim(realm, idpAlias, groupIds);
            log.info("Essential claim synced successfully for tenantId={} idpAlias={}", tenantId, idpAlias);
        } catch (Exception e) {
            // Non-fatal: group authorization is already committed to DB.
            // Log the error so ops can investigate, but don't surface it as an API failure.
            log.error("Failed to sync essential claim for tenantId={} idpAlias={}. " +
                      "Group authorization succeeded in DB but Keycloak claim was NOT updated. " +
                      "Error: {}", tenantId, idpAlias, e.getMessage(), e);
        }
    }
}
