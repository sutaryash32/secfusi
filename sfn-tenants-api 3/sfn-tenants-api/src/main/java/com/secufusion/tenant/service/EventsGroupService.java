package com.secufusion.tenant.service;

import com.secufusion.tenant.entity.EventsGroup;
import com.secufusion.tenant.repository.EventsGroupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class EventsGroupService {

    private final EventsGroupRepository groupRepository;

    @Transactional
    public void deleteByTenantId(String tenantId) {
        log.info("Deleting events groups for tenantId={}", tenantId);
        groupRepository.deleteByTenantId(tenantId);
    }

    /**
     * Create default API key group for tenant (called during tenant setup)
     *
     * @param tenantId Tenant ID
     * @param createdBy Creator identifier
     * @return Created default group
     */
    @Transactional
    public EventsGroup createDefaultGroupForTenant(String tenantId, String createdBy) {
        log.info("Creating default events group for tenant: {}", tenantId);

        // Check if default group already exists
        return groupRepository.findByTenantIdAndIsDefault(tenantId, true)
                .orElseGet(() -> {
                    EventsGroup defaultGroup = new EventsGroup();
                    defaultGroup.setPkEventsGroupId(UUID.randomUUID().toString());
                    defaultGroup.setTenantId(tenantId);
                    defaultGroup.setName("Default API Key Group");
                    defaultGroup.setDescription("Auto-created default group for API key users");
                    defaultGroup.setGroupType(EventsGroup.GroupType.APIKEY_GROUP);
                    defaultGroup.setAuthorized(true); // Default group is always authorized
                    defaultGroup.setIsDefault(true);
                    defaultGroup.setIsActive(true);
                    defaultGroup.setCreatedBy(createdBy);

                    EventsGroup saved = groupRepository.save(defaultGroup);
                    log.info("Default events group created: {} for tenant: {}", saved.getPkEventsGroupId(), tenantId);
                    return saved;
                });
    }

    /**
     * Check if group exists and belongs to tenant
     *
     * @param tenantId Tenant ID
     * @param groupId Group ID
     * @return true if exists, false otherwise
     */
    @Transactional(readOnly = true)
    public boolean exists(String tenantId, String groupId) {
        return groupRepository.existsByIdAndTenantId(groupId, tenantId);
    }
}
