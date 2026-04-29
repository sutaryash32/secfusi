package com.secufusion.tenant.service;

import com.secufusion.tenant.entity.EventsGroup;
import com.secufusion.tenant.repository.EventsGroupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventsGroupServiceTest {

    @Mock
    private EventsGroupRepository groupRepository;

    @InjectMocks
    private EventsGroupService eventsGroupService;

    private EventsGroup existingDefault;

    @BeforeEach
    void setUp() {
        existingDefault = new EventsGroup();
        existingDefault.setPkEventsGroupId("g-1");
        existingDefault.setTenantId("tenant-1");
        existingDefault.setIsDefault(true);
        existingDefault.setIsActive(true);
        existingDefault.setAuthorized(true);
        existingDefault.setGroupType(EventsGroup.GroupType.APIKEY_GROUP);
        existingDefault.setName("Default API Key Group");
        existingDefault.setDescription("Auto-created default group for API key users");
        existingDefault.setCreatedBy("system");
    }

    @Nested
    @DisplayName("deleteByTenantId")
    class DeleteByTenantId {

        @Test
        void delegatesToRepository() {
            // ARRANGE + ACT
            eventsGroupService.deleteByTenantId("tenant-1");

            // ASSERT
            verify(groupRepository).deleteByTenantId("tenant-1");
        }
    }

    @Nested
    @DisplayName("createDefaultGroupForTenant")
    class CreateDefaultGroupForTenant {

        @Test
        void returnsExistingDefault_whenPresent() {
            // ARRANGE
            when(groupRepository.findByTenantIdAndIsDefault("tenant-1", true))
                    .thenReturn(Optional.of(existingDefault));

            // ACT
            EventsGroup result = eventsGroupService.createDefaultGroupForTenant("tenant-1", "system");

            // ASSERT
            assertNotNull(result);
            assertEquals("g-1", result.getPkEventsGroupId());
            verify(groupRepository, never()).save(any());
        }

        @Test
        void createsAndSavesDefault_whenMissing() {
            // ARRANGE
            when(groupRepository.findByTenantIdAndIsDefault("tenant-1", true))
                    .thenReturn(Optional.empty());
            when(groupRepository.save(any(EventsGroup.class))).thenAnswer(inv -> inv.getArgument(0));

            // ACT
            EventsGroup result = eventsGroupService.createDefaultGroupForTenant("tenant-1", "creator");

            // ASSERT
            assertNotNull(result);
            assertEquals("tenant-1", result.getTenantId());
            assertEquals("Default API Key Group", result.getName());
            assertEquals(EventsGroup.GroupType.APIKEY_GROUP, result.getGroupType());
            assertEquals(Boolean.TRUE, result.getAuthorized());
            assertEquals(Boolean.TRUE, result.getIsDefault());
            assertEquals(Boolean.TRUE, result.getIsActive());
            assertEquals("creator", result.getCreatedBy());
            verify(groupRepository).save(any(EventsGroup.class));
        }
    }

    @Nested
    @DisplayName("exists")
    class Exists {

        @Test
        void returnsRepositoryResult() {
            // ARRANGE
            when(groupRepository.existsByIdAndTenantId("g-1", "tenant-1")).thenReturn(true);

            // ACT
            boolean exists = eventsGroupService.exists("tenant-1", "g-1");

            // ASSERT
            assertTrue(exists);
            verify(groupRepository).existsByIdAndTenantId("g-1", "tenant-1");
        }
    }
}

