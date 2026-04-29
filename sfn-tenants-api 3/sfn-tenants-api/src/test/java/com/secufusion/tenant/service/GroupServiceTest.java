package com.secufusion.tenant.service;

import com.secufusion.tenant.entity.Groups;
import com.secufusion.tenant.entity.Roles;
import com.secufusion.tenant.entity.Tenant;
import com.secufusion.tenant.entity.User;
import com.secufusion.tenant.exception.ResourceNotFoundException;
import com.secufusion.tenant.repository.GroupsRepository;
import com.secufusion.tenant.repository.TenantRepository;
import com.secufusion.tenant.repository.UserRepository;
import com.secufusion.tenant.util.JwtUtl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

    @Mock
    private GroupsRepository groupsRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtUtl jwtUtl;

    @InjectMocks
    private GroupService groupService;

    private Tenant tenant;

    @BeforeEach
    void setUp() {
        tenant = new Tenant();
        tenant.setTenantID("tenant-1");
        tenant.setTenantName("acme");
    }

    @Nested
    @DisplayName("createOrGetDefaultGroup")
    class CreateOrGetDefaultGroup {

        @Test
        void throwsResourceNotFound_whenTenantMissing() {
            // ARRANGE
            when(tenantRepository.findById("tenant-1")).thenReturn(Optional.empty());

            // ACT + ASSERT
            assertThrows(ResourceNotFoundException.class,
                    () -> groupService.createOrGetDefaultGroup("tenant-1", "Admins", true, "system"));
            verifyNoInteractions(groupsRepository);
        }

        @Test
        void returnsExistingGroup_whenFound() {
            // ARRANGE
            when(tenantRepository.findById("tenant-1")).thenReturn(Optional.of(tenant));
            Groups g = new Groups();
            g.setPkGroupId("g-1");
            g.setName("Admins");
            when(groupsRepository.findByNameAndTenantId("Admins", "tenant-1")).thenReturn(Optional.of(g));

            // ACT
            Groups result = groupService.createOrGetDefaultGroup("tenant-1", "Admins", true, "system");

            // ASSERT
            assertNotNull(result);
            assertEquals("g-1", result.getPkGroupId());
            verify(groupsRepository, never()).save(any());
        }

        @Test
        void createsAndSavesGroup_whenMissing() {
            // ARRANGE
            when(tenantRepository.findById("tenant-1")).thenReturn(Optional.of(tenant));
            when(groupsRepository.findByNameAndTenantId("Admins", "tenant-1")).thenReturn(Optional.empty());
            when(groupsRepository.save(any(Groups.class))).thenAnswer(inv -> {
                Groups g = inv.getArgument(0);
                g.setPkGroupId("g-new");
                return g;
            });

            // ACT
            Groups result = groupService.createOrGetDefaultGroup("tenant-1", "Admins", true, "system");

            // ASSERT
            assertNotNull(result);
            assertEquals("g-new", result.getPkGroupId());
            assertEquals("Admins", result.getName());
            assertEquals("tenant-1", result.getTenantId());
            assertEquals(Character.valueOf('Y'), result.getIsAdmin());
            assertEquals(Character.valueOf('Y'), result.getIsDefault());
            assertEquals(Boolean.TRUE, result.getActive());
            assertNotNull(result.getMappedRoles());
            verify(groupsRepository).save(any(Groups.class));
        }
    }

    @Nested
    @DisplayName("assignRoleToGroup")
    class AssignRoleToGroup {

        @Test
        void noOp_whenNullInputs() {
            // ARRANGE + ACT
            groupService.assignRoleToGroup(null, null);

            // ASSERT
            verifyNoInteractions(groupsRepository);
        }

        @Test
        void noOp_whenRoleAlreadyMapped() {
            // ARRANGE
            Groups group = new Groups();
            group.setPkGroupId("g-1");
            group.setName("Admins");
            Roles role1 = new Roles();
            role1.setPkRoleId("r-1");
            role1.setName("ROLE_ADMIN");
            Set<Roles> mapped = new HashSet<>();
            mapped.add(role1);
            group.setMappedRoles(mapped);

            Roles roleDuplicate = new Roles();
            roleDuplicate.setPkRoleId("r-1");
            roleDuplicate.setName("ROLE_ADMIN");

            // ACT
            groupService.assignRoleToGroup(group, roleDuplicate);

            // ASSERT
            verify(groupsRepository, never()).save(any());
        }

        @Test
        void addsRoleAndSaves_whenNotMapped() {
            // ARRANGE
            Groups group = new Groups();
            group.setPkGroupId("g-1");
            group.setName("Admins");
            group.setMappedRoles(new HashSet<>());

            Roles role = new Roles();
            role.setPkRoleId("r-1");
            role.setName("ROLE_ADMIN");

            when(groupsRepository.save(any(Groups.class))).thenAnswer(inv -> inv.getArgument(0));

            // ACT
            groupService.assignRoleToGroup(group, role);

            // ASSERT
            assertTrue(group.getMappedRoles().contains(role));
            verify(groupsRepository).save(group);
        }
    }

    @Nested
    @DisplayName("assignUserToGroup")
    class AssignUserToGroup {

        @Test
        void noOp_whenNullInputs() {
            // ARRANGE + ACT
            groupService.assignUserToGroup(null, null);

            // ASSERT
            verifyNoInteractions(groupsRepository, userRepository);
        }

        @Test
        void savesGroupThenUser() {
            // ARRANGE
            Groups group = new Groups();
            group.setPkGroupId("g-1");
            group.setName("Admins");

            User user = new User();
            user.setPkUserId("u-1");
            user.setUserName("bob");

            when(groupsRepository.save(group)).thenReturn(group);
            when(userRepository.save(user)).thenReturn(user);

            // ACT
            groupService.assignUserToGroup(group, user);

            // ASSERT
            verify(groupsRepository).save(group);
            verify(userRepository).save(user);
        }
    }

    @Nested
    @DisplayName("deleteGroupsByTenantId")
    class DeleteGroupsByTenantId {

        @Test
        void returnsTrue_whenDeleteSucceeds() {
            // ARRANGE + ACT
            boolean ok = groupService.deleteGroupsByTenantId("tenant-1");

            // ASSERT
            assertTrue(ok);
            verify(groupsRepository).deleteByTenantId("tenant-1");
        }

        @Test
        void returnsFalse_whenRepositoryThrows() {
            // ARRANGE
            doThrow(new RuntimeException("fail")).when(groupsRepository).deleteByTenantId("tenant-1");

            // ACT
            boolean ok = groupService.deleteGroupsByTenantId("tenant-1");

            // ASSERT
            assertFalse(ok);
            verify(groupsRepository).deleteByTenantId("tenant-1");
        }
    }

    @Nested
    @DisplayName("assignRoleToGroupIfMissing")
    class AssignRoleToGroupIfMissing {

        @Test
        void noOp_whenNullInputs() {
            // ARRANGE + ACT
            groupService.assignRoleToGroupIfMissing(null, null);

            // ASSERT
            verifyNoInteractions(groupsRepository);
        }

        @Test
        void delegatesToInsertRoleMappingIfAbsent() {
            // ARRANGE
            Groups group = new Groups();
            group.setPkGroupId("g-1");
            Roles role = new Roles();
            role.setPkRoleId("r-1");

            // ACT
            groupService.assignRoleToGroupIfMissing(group, role);

            // ASSERT
            verify(groupsRepository).insertRoleMappingIfAbsent("g-1", "r-1");
        }
    }

    @Nested
    @DisplayName("deleteGroupsByTenantIdsBatch")
    class DeleteGroupsByTenantIdsBatch {

        @Test
        void delegatesToRepository() {
            // ARRANGE + ACT
            groupService.deleteGroupsByTenantIdsBatch(java.util.List.of("t1", "t2"));

            // ASSERT
            verify(groupsRepository).deleteByTenantIdIn(java.util.List.of("t1", "t2"));
        }
    }

    @Nested
    @DisplayName("getDefaultGroupForTenant")
    class GetDefaultGroupForTenant {

        @Test
        void delegatesToRepository() {
            // ARRANGE
            Groups g = new Groups();
            g.setPkGroupId("g-1");
            when(groupsRepository.findByTenantIdAndIsAdminAndIsDefault("tenant-1", 'Y', 'Y'))
                    .thenReturn(Optional.of(g));

            // ACT
            Optional<Groups> result = groupService.getDefaultGroupForTenant("tenant-1");

            // ASSERT
            assertTrue(result.isPresent());
            assertEquals("g-1", result.get().getPkGroupId());
            verify(groupsRepository).findByTenantIdAndIsAdminAndIsDefault("tenant-1", 'Y', 'Y');
        }
    }
}

