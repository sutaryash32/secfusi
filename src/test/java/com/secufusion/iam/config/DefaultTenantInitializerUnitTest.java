//package com.secufusion.iam.config;
//
//import com.secufusion.iam.dto.CreateTenantRequest;
//import com.secufusion.iam.entity.Groups;
//import com.secufusion.iam.entity.Roles;
//import com.secufusion.iam.entity.Tenant;
//import com.secufusion.iam.entity.User;
//import com.secufusion.iam.repository.GroupsRepository;
//import com.secufusion.iam.repository.TenantRepository;
//import com.secufusion.iam.repository.UserRepository;
//import com.secufusion.iam.service.DefaultTenantInitializer;
//import com.secufusion.iam.service.GroupService;
//import com.secufusion.iam.service.RoleService;
//import com.secufusion.iam.service.TenantService;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.springframework.test.util.ReflectionTestUtils;
//
//import java.util.List;
//import java.util.Optional;
//import java.util.UUID;
//
//import static org.assertj.core.api.Assertions.assertThat;
//import static org.mockito.Mockito.*;
//
//class DefaultTenantInitializerUnitTest {
//
//    // mocks for constructor-injected dependencies
//    private TenantRepository tenantRepository;
//    private TenantService tenantService;
//    private RoleService roleService;
//    private GroupService groupService;
//    private GroupsRepository groupRepository;
//    private UserRepository userRepository;
//
//    // instance under test
//    private DefaultTenantInitializer initializer;
//
//    // common test data
//    private final String defaultTenantName = "secufusion";
//    private final String masterAdminEmail = "admin@secufusion.com";
//    private final String masterAdminUsername = "admin";
//    private final String masterAdminFirstName = "Admin";
//    private final String masterAdminLastName = "User";
//    private final String masterAdminPhone = "9999999999";
//    private final String masterTenantDomain = "secufusion.com";
//
//    @BeforeEach
//    void setUp() {
//        tenantRepository = mock(TenantRepository.class);
//        tenantService = mock(TenantService.class);
//        roleService = mock(RoleService.class);
//        groupService = mock(GroupService.class);
//        groupRepository = mock(GroupsRepository.class);
//        userRepository = mock(UserRepository.class);
//
//        // instantiate with mocks
//        initializer = new DefaultTenantInitializer(
//                tenantRepository,
//                tenantService,
//                roleService,
//                groupService,
//                groupRepository,
//                userRepository
//        );
//
//        // set @Value fields (ReflectionTestUtils from spring-test)
//        ReflectionTestUtils.setField(initializer, "masterAdminEmail", masterAdminEmail);
//        ReflectionTestUtils.setField(initializer, "masterAdminUsername", masterAdminUsername);
//        ReflectionTestUtils.setField(initializer, "masterAdminFirstName", masterAdminFirstName);
//        ReflectionTestUtils.setField(initializer, "masterAdminLastName", masterAdminLastName);
//        ReflectionTestUtils.setField(initializer, "masterAdminPhone", masterAdminPhone);
//        ReflectionTestUtils.setField(initializer, "masterTenantDomain", masterTenantDomain);
//        ReflectionTestUtils.setField(initializer, "defaultTenantName", defaultTenantName);
//    }
//
//    @Test
//    void whenTenantExists_thenNoCreation_butRolesAndGroupAssignmentHappenIfUserPresent() {
//        // prepare an existing tenant and default user
//        Tenant tenant = new Tenant();
//        tenant.setTenantID("TID-1");
//        tenant.setTenantName(defaultTenantName);
//
//        User defaultUser = new User();
//        defaultUser.setPkUserId(UUID.randomUUID().toString());
//        defaultUser.setUserName("sysadmin");
//        defaultUser.setDefaultUser(true);
//        tenant.setUsers(List.of(defaultUser));
//
//        when(tenantRepository.findByTenantName(defaultTenantName)).thenReturn(Optional.of(tenant));
//        when(userRepository.findByTenant_TenantIDAndDefaultUser("TID-1", true))
//                .thenReturn(Optional.of(defaultUser));
//
//        // roleService and groupRepository behavior
//        Roles adminRole = new Roles();
//        adminRole.setPkRoleId(UUID.randomUUID().toString());
//        when(roleService.createOrGetDefaultRole(eq("TID-1"), anyString(), anyString(), eq(defaultUser.getPkUserId())))
//                .thenReturn(adminRole);
//
//        Groups adminGroup = new Groups();
//        adminGroup.setPkGroupId(UUID.randomUUID().toString());
//        // simulate groupRepository returning existing admin group
//        when(groupRepository.findByTenant_TenantIDAndIsAdminAndIsDefault("TID-1", 'Y', 'Y'))
//                .thenReturn(Optional.of(adminGroup));
//
//        // call initializer
//        initializer.initialize();
//
//        // verify no tenant creation
//        verify(tenantService, never()).createTenant(any(), any(CreateTenantRequest.class));
//
//        // verify role created or fetched
//        verify(roleService, times(1)).createOrGetDefaultRole(eq("TID-1"), contains("_Admin"), anyString(), eq(defaultUser.getPkUserId()));
//
//        // admin group existed, but assignRoleToGroup and assignUserToGroup should be called
//        verify(groupService, times(1)).assignRoleToGroup(eq(adminGroup), eq(adminRole));
//        verify(groupService, times(1)).assignUserToGroup(eq(adminGroup), eq(defaultUser));
//    }
//
//    @Test
//    void whenTenantMissing_thenCreateTenant_andEnsureRoleGroupAndUserMapping() {
//        // tenantRepository returns empty first
//        when(tenantRepository.findByTenantName(defaultTenantName)).thenReturn(Optional.empty());
//
//        // simulate tenantService.createTenant creating tenant (we don't use HttpServletRequest here; initializer passes null)
//        // After createTenant, the tenantRepository should return the created tenant.
//        Tenant created = new Tenant();
//        created.setTenantID("CREATED-1");
//        created.setTenantName(defaultTenantName);
//
//        // user that will be present after creation
//        User createdDefaultUser = new User();
//        createdDefaultUser.setPkUserId(UUID.randomUUID().toString());
//        createdDefaultUser.setUserName("created-admin");
//        createdDefaultUser.setDefaultUser(true);
//        created.setUsers(List.of(createdDefaultUser));
//
//        // when tenantService.createTenant is called, we return a TenantResponse (not used), but initializer will re-query repo
//        // So simulate createTenant call and then tenantRepository.findByTenantName returns created tenant
//        doAnswer(invocation -> {
//            // optionally assert request content here
//            return null; // tenantService.createTenant returns void or TenantResponse in real service; initializer ignores return
//        }).when(tenantService).createTenant(isNull(), any(CreateTenantRequest.class));
//
//        when(tenantRepository.findByTenantName(defaultTenantName))
//                .thenReturn(Optional.empty())   // first call in initializer
//                .thenReturn(Optional.of(created)); // second call after createTenant
//
//        when(userRepository.findByTenant_TenantIDAndDefaultUser("CREATED-1", true))
//                .thenReturn(Optional.of(createdDefaultUser));
//
//        // role creation
//        Roles adminRole = new Roles();
//        adminRole.setPkRoleId("ROLE-1");
//        when(roleService.createOrGetDefaultRole(eq("CREATED-1"), anyString(), anyString(), eq(createdDefaultUser.getPkUserId())))
//                .thenReturn(adminRole);
//
//        // groupRepository returns null so group will be created via groupService
//        when(groupRepository.findByTenant_TenantIDAndIsAdminAndIsDefault("CREATED-1", 'Y', 'Y'))
//                .thenReturn(Optional.empty());
//
//        Groups createdGroup = new Groups();
//        createdGroup.setPkGroupId("GROUP-1");
//        when(groupService.createOrGetDefaultGroup(eq("CREATED-1"), contains("_Admin"), eq(true), eq(createdDefaultUser.getPkUserId())))
//                .thenReturn(createdGroup);
//
//        // call initializer
//        initializer.initialize();
//
//        // verify createTenant called once
//        verify(tenantService, times(1)).createTenant(isNull(), any(CreateTenantRequest.class));
//
//        // verify role and group creation and assignments
//        verify(roleService, times(1))
//                .createOrGetDefaultRole(eq("CREATED-1"), contains("_Admin"), anyString(), eq(createdDefaultUser.getPkUserId()));
//
//        verify(groupService, times(1)).createOrGetDefaultGroup(eq("CREATED-1"), contains("_Admin"), eq(true), eq(createdDefaultUser.getPkUserId()));
//        verify(groupRepository, times(1)).save(createdGroup);
//
//        verify(groupService, times(1)).assignRoleToGroup(eq(createdGroup), eq(adminRole));
//        verify(groupService, times(1)).assignUserToGroup(eq(createdGroup), eq(createdDefaultUser));
//    }
//
//    @Test
//    void whenTenantCreationFails_initializerShouldCatchException_andNotThrow() {
//        when(tenantRepository.findByTenantName(defaultTenantName)).thenReturn(Optional.empty());
//
//        // make tenantService.createTenant throw
//        doThrow(new RuntimeException("creation failed")).when(tenantService).createTenant(isNull(), any(CreateTenantRequest.class));
//
//        // subsequent repository call (after attempted create) also returns empty
//        when(tenantRepository.findByTenantName(defaultTenantName)).thenReturn(Optional.empty());
//
//        // just call initialize - should not throw
//        initializer.initialize();
//
//        // verify createTenant called
//        verify(tenantService, times(1)).createTenant(isNull(), any(CreateTenantRequest.class));
//
//        // since creation failed and tenant not found, no role or group interactions expected
//        verifyNoInteractions(roleService);
//        verify(groupRepository, never()).findByTenant_TenantIDAndIsAdminAndIsDefault(anyString(), anyChar(), anyChar());
//    }
//}
