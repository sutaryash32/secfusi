package com.secufusion.tenant.service;

import com.secufusion.tenant.dto.RolesDto;
import com.secufusion.tenant.entity.Groups;
import com.secufusion.tenant.entity.Roles;
import com.secufusion.tenant.entity.Scopes;
import com.secufusion.tenant.entity.Tenant;
import com.secufusion.tenant.entity.User;
import com.secufusion.tenant.exception.ResourceNotFoundException;
import com.secufusion.tenant.repository.RolesRepository;
import com.secufusion.tenant.repository.ScopesRepository;
import com.secufusion.tenant.repository.TenantRepository;
import com.secufusion.tenant.repository.UserRepository;
import com.secufusion.tenant.util.JwtUtl;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

    @Mock
    private RolesRepository rolesRepository;
    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private JwtUtl jwtUtl;
    @Mock
    private ScopesRepository scopesRepository;

    @InjectMocks
    private RoleService roleService;

    private Tenant tenant;

    @BeforeEach
    void setUp() {
        tenant = new Tenant();
        tenant.setTenantID("tenant-1");
        tenant.setTenantType("ENTERPRISE");
        tenant.setSelfManaged(false);
    }

    @Nested
    @DisplayName("createOrGetEnterpriseAdminRole")
    class CreateOrGetEnterpriseAdminRole {

        @Test
        void returnsNull_whenTenantNotFound_andDoesNotThrow() {
            // ARRANGE
            when(tenantRepository.findById("tenant-1")).thenReturn(Optional.empty());

            // ACT
            Roles role = roleService.createOrGetEnterpriseAdminRole("tenant-1", "admin");

            // ASSERT
            assertNull(role);
        }

        @Test
        void createsRole_whenNotExisting() {
            // ARRANGE
            when(tenantRepository.findById("tenant-1")).thenReturn(Optional.of(tenant));
            when(rolesRepository.findByNameAndIsDefaultAndIsSuperRoleWithScopes("ENTERPRISE ADMIN", 'Y', 'Y'))
                    .thenReturn(Optional.empty());
            when(rolesRepository.save(any(Roles.class))).thenAnswer(inv -> {
                Roles r = inv.getArgument(0);
                r.setPkRoleId("r-1");
                return r;
            });
            when(scopesRepository.findByUserTypes(anyList())).thenReturn(List.of());

            // ACT
            Roles role = roleService.createOrGetEnterpriseAdminRole("tenant-1", "admin");

            // ASSERT
            assertNotNull(role);
            assertEquals("r-1", role.getPkRoleId());
            assertEquals("ENTERPRISE ADMIN", role.getName());
            verify(rolesRepository).save(any(Roles.class));
        }
    }

    @Nested
    @DisplayName("resolveAdminRolesForTenant")
    class ResolveAdminRolesForTenant {

        @Test
        void forSelfManagedMssp_returnsTwoRoles() {
            // ARRANGE
            tenant.setTenantType("MSSP");
            tenant.setSelfManaged(true);

            when(tenantRepository.findById("tenant-1")).thenReturn(Optional.of(tenant));

            when(rolesRepository.findByNameAndIsDefaultAndIsSuperRoleWithScopes(anyString(), eq('Y'), eq('Y')))
                    .thenReturn(Optional.empty());
            when(rolesRepository.save(any(Roles.class))).thenAnswer(inv -> {
                Roles r = inv.getArgument(0);
                if (r.getPkRoleId() == null) {
                    r.setPkRoleId("rid-" + r.getName().replace(" ", "_"));
                }
                return r;
            });
            when(scopesRepository.findByUserTypes(anyList())).thenReturn(List.of());

            // ACT
            List<Roles> roles = roleService.resolveAdminRolesForTenant(tenant, "admin");

            // ASSERT
            assertNotNull(roles);
            assertEquals(2, roles.size());
            assertEquals("MSSP ADMIN", roles.get(0).getName());
            assertEquals("ENTERPRISE ADMIN", roles.get(1).getName());
        }

        @Test
        void throwsIllegalState_whenUnexpectedTenantType() {
            // ARRANGE
            tenant.setTenantType("SOMETHING_ELSE");

            // ACT + ASSERT
            assertThrows(IllegalStateException.class,
                    () -> roleService.resolveAdminRolesForTenant(tenant, "admin"));
        }
    }

    @Nested
    @DisplayName("assignScopesByRoleType")
    class AssignScopesByRoleType {

        @Test
        void addsScopesAndSaves_whenNewScopesFound() {
            // ARRANGE
            Roles role = new Roles();
            role.setPkRoleId("r-1");
            role.setName("ENTERPRISE ADMIN");
            role.setScopes(new HashSet<>());

            Scopes scope = new Scopes();
            scope.setPkScopeId("s-1");

            when(scopesRepository.findByUserTypes(List.of("ENTERPRISE"))).thenReturn(List.of(scope));

            // ACT
            roleService.assignScopesByRoleType(role);

            // ASSERT
            assertNotNull(role.getScopes());
            assertEquals(1, role.getScopes().size());
            verify(rolesRepository).save(role);
        }
    }

    @Nested
    @DisplayName("createRoles")
    class CreateRoles {

        @Test
        void throwsResourceNotFound_whenRoleNameAlreadyExistsForTenant() {
            // ARRANGE
            HttpServletRequest req = mock(HttpServletRequest.class);

            Tenant t = new Tenant();
            t.setTenantID("tenant-1");
            when(jwtUtl.getTenantFromRequest(req)).thenReturn(t);

            User u = new User();
            u.setPkUserId("u-1");
            when(jwtUtl.getUserFromRequest(req)).thenReturn(u);

            Roles existing = new Roles();
            existing.setPkRoleId("r-1");
            when(rolesRepository.findByNameAndTenant_TenantID("Custom", "tenant-1"))
                    .thenReturn(Optional.of(existing));

            RolesDto dto = new RolesDto();
            dto.setName("Custom");

            // ACT + ASSERT
            assertThrows(ResourceNotFoundException.class, () -> roleService.createRoles(req, dto));
            verify(rolesRepository, never()).save(any());
        }

        @Test
        void savesRole_whenUnique() {
            // ARRANGE
            HttpServletRequest req = mock(HttpServletRequest.class);

            Tenant t = new Tenant();
            t.setTenantID("tenant-1");
            when(jwtUtl.getTenantFromRequest(req)).thenReturn(t);

            User u = new User();
            u.setPkUserId("u-1");
            when(jwtUtl.getUserFromRequest(req)).thenReturn(u);

            when(rolesRepository.findByNameAndTenant_TenantID("Custom", "tenant-1")).thenReturn(Optional.empty());
            when(rolesRepository.save(any(Roles.class))).thenAnswer(inv -> inv.getArgument(0));

            RolesDto dto = new RolesDto();
            dto.setName("Custom");
            dto.setDescription("d");

            // ACT
            Roles saved = roleService.createRoles(req, dto);

            // ASSERT
            assertNotNull(saved);
            assertEquals("Custom", saved.getName());
            assertEquals("u-1", saved.getCreatedBy());
            verify(rolesRepository).save(any(Roles.class));
        }
    }

    @Nested
    @DisplayName("removeEnterpriseAdminRoleFromUser")
    class RemoveEnterpriseAdminRoleFromUser {

        @Test
        void removesRoleFromMappedGroups_andSavesUser() {
            // ARRANGE
            Roles enterprise = new Roles();
            enterprise.setPkRoleId("r-enterprise");
            enterprise.setName("ENTERPRISE ADMIN");

            when(rolesRepository.findByNameAndIsDefaultAndIsSuperRole("ENTERPRISE ADMIN", 'Y', 'Y'))
                    .thenReturn(Optional.of(enterprise));

            Groups group = new Groups();
            group.setPkGroupId("g-1");
            group.setMappedRoles(new HashSet<>(Set.of(enterprise)));

            User user = new User();
            user.setPkUserId("u-1");
            user.setMappedGroups(Set.of(group));

            when(userRepository.findById("u-1")).thenReturn(Optional.of(user));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            // ACT
            roleService.removeEnterpriseAdminRoleFromUser("tenant-1", "u-1");

            // ASSERT
            assertFalse(group.getMappedRoles().contains(enterprise));
            verify(userRepository).save(user);
        }
    }
}

