package com.secufusion.iam.service;

import com.secufusion.iam.dto.UsersDto;
import com.secufusion.iam.entity.Groups;
import com.secufusion.iam.entity.Tenant;
import com.secufusion.iam.entity.User;
import com.secufusion.iam.exception.KeycloakOperationException;
import com.secufusion.iam.exception.ResourceNotFoundException;
import com.secufusion.iam.repository.GroupsRepository;
import com.secufusion.iam.repository.TenantRepository;
import com.secufusion.iam.repository.UserRepository;
import com.secufusion.iam.util.JwtUtl;
import com.secufusion.iam.util.KeycloakAdminUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.security.access.AccessDeniedException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service responsible for user CRUD operations and validations.
 *
 * This file has been fully commented and instrumented with detailed logging statements
 * that trace internal state and decisions for easier debugging and observability.
 *
 * Logging conventions used:
 * - log.info for high level operation start/finish and success markers
 * - log.debug for intermediate state, parameters, DB/Keycloak responses
 * - log.error for failures and exceptional conditions
 *
 * NOTE: Avoid logging sensitive data in production (passwords, tokens). Current logs
 * intentionally avoid any secret values.
 */
@Slf4j
@Service
public class UserService {

    // Repositories and utilities injected by Spring
    @Autowired private UserRepository userRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private KeycloakAdminUtil kcUtil;
    @Autowired private JwtUtl jwtUtl;
    @Autowired private GroupsRepository groupsRepository;

    // ========================================================================
    // CREATE USER
    // ========================================================================

    /**
     * Create a new user in our DB and provision it to Keycloak.
     *
     * Flow:
     * 1. Load tenant
     * 2. Validate uniqueness constraints (DB + Keycloak)
     * 3. Persist local user with status 'CREATING'
     * 4. Create user in Keycloak and trigger required actions
     * 5. Assign roles and update local record with Keycloak id and 'ACTIVE' status
     *
     * Detailed logs added at every step.
     */
  @Transactional
    public UsersDto createUser(String tenantId, UsersDto dto) {

        log.info("➡️ [CREATE USER] Start (explicit tenantId). tenantId={}, dtoSummary={}", tenantId, summarizeDto(dto));
        log.debug("createUser() received DTO details: username={}, email={}, firstName={}, lastName={}, phone={}",
                dto.getEmail(), dto.getEmail(), dto.getFirstName(), dto.getLastName(), dto.getPhoneNumber());

        // Load tenant or fail fast
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> {
                    log.error("❌ Tenant not found while creating user. tenantId={}", tenantId);
                    return new ResourceNotFoundException("Tenant not found: " + tenantId);
                });

        return createUserInternal(tenant, dto);
    }

    @Transactional
    public UsersDto createUser(HttpServletRequest request, UsersDto dto) {

        log.info("➡️ [CREATE USER] Start (from request). dtoSummary={}", summarizeDto(dto));
        log.debug("createUser(request) received DTO details: username={}, email={}, firstName={}, lastName={}, phone={}",
                dto.getEmail(), dto.getEmail(), dto.getFirstName(), dto.getLastName(), dto.getPhoneNumber());

        Tenant tenant = jwtUtl.getTenantFromRequest(request);
        if (tenant == null) {
            log.error("❌ Could not resolve tenant from request while creating user.");
            throw new ResourceNotFoundException("Tenant not found from request");
        }

        User userFromRequest = jwtUtl.getUserFromRequest(request);
        dto.setCreatedBy(userFromRequest.getPkUserId());

        log.debug("Tenant resolved from request: tenantId={} realm={}", tenant.getTenantID(), tenant.getRealmName());
        return createUserInternal(tenant, dto);
    }

    /**
     * Shared internal implementation that expects a resolved Tenant.
     */
    private UsersDto createUserInternal(Tenant tenant, UsersDto dto) {

        log.debug("Proceeding with createUserInternal. tenantId={} realm={} dtoSummary={}",
                tenant.getTenantID(), tenant.getRealmName(), summarizeDto(dto));

        // VALIDATE (new user → excludeUserId=null)
        validateUserFields(tenant, dto, null);

        try {
            log.debug("Creating local DB user record (prepare entity)...");
            User user = new User();
            user.setFirstName(dto.getFirstName());
            user.setLastName(dto.getLastName());
            user.setEmail(dto.getEmail());
            user.setUserName(dto.getEmail());
            user.setPhoneNo(dto.getPhoneNumber());
            user.setTenant(tenant);
            user.setStatus("CREATING");
            user.setCreatedAt(LocalDateTime.now());
            user.setCreatedBy(dto.getCreatedBy());

            log.debug("Persisting local user to DB with status=C:\\'CREATING\\' (pre-keycloak). username={}", dto.getEmail());
            User savedUser = userRepository.save(user);

            log.info("✔ Local user created successfully in DB. userId={} username={}",
                    savedUser.getPkUserId(), savedUser.getUserName());
            log.debug("Saved user details: pkUserId={} tenantId={} status={}",
                    savedUser.getPkUserId(), savedUser.getTenant().getTenantID(), savedUser.getStatus());

            // Create user in Keycloak
            log.debug("Calling Keycloak createUser API. realm={} username={} email={}",
                    tenant.getRealmName(), dto.getEmail(), dto.getEmail());

            String kcUserId = kcUtil.createUser(
                    tenant.getRealmName(),
                    dto.getEmail(),
                    dto.getEmail(),
                    dto.getFirstName(),
                    dto.getLastName(),
                    false
            );

            log.debug("Keycloak createUser returned id={}", kcUserId);
            if (kcUserId == null) {
                log.error("❌ Keycloak returned null userId. Username may already exist in realm. realm={} username={}",
                        tenant.getRealmName(), dto.getEmail());
                throw new KeycloakOperationException(
                        "KC_USER_CREATION_FAILED", 3001,
                        "Failed to create user in Keycloak (duplicate?)"
                );
            }

            log.info("✔ Keycloak user created. kcUserId={}", kcUserId);

            // Send required actions (update password, verify email)
            log.debug("Triggering Keycloak required actions: kcUserId={} actions=[UPDATE_PASSWORD, VERIFY_EMAIL]", kcUserId);
            kcUtil.sendRequiredActionEmail(
                    tenant.getRealmName(),
                    kcUserId,
                    List.of("UPDATE_PASSWORD", "VERIFY_EMAIL")
            );

            log.debug("Triggering welcome email via auth provider config if available. savedUserEmail={} loginUrlExists={}",
                    savedUser.getEmail(), tenant.getAuthProviderConfig() != null && tenant.getAuthProviderConfig().getLoginUrl() != null);
            kcUtil.sendWelcomeEmail(savedUser.getEmail(), tenant.getAuthProviderConfig().getLoginUrl(), savedUser.getUserName());

            log.info("✔ Required action and welcome emails triggered for kcUserId={}", kcUserId);

            // Role assignment
            log.debug("Assigning realm-admin role to Keycloak user. realm={} kcUserId={}", tenant.getRealmName(), kcUserId);
            kcUtil.assignRealmAdminRole(tenant.getRealmName(), kcUserId);
            log.info("✔ Assigned realm-admin role to kcUserId={}", kcUserId);

            // Update DB with KC ID and mark ACTIVE
            log.debug("Updating saved local user with keycloak id and setting status=ACTIVE. dbUserId={} kcUserId={}",
                    savedUser.getPkUserId(), kcUserId);
            savedUser.setKeycloakUserId(kcUserId);
            savedUser.setStatus("ACTIVE");
            if (dto.getGroups() != null && !dto.getGroups().isEmpty()) {
                updateUserGroups(user, dto.getGroups());
            }

            userRepository.save(savedUser);

            log.info("🎉 User successfully created in DB + KC. userId={} kcUserId={}",
                    savedUser.getPkUserId(), kcUserId);

            return mapToDto(savedUser);

        } catch (KeycloakOperationException ex) {
            log.error("❌ KeycloakOperationException during user creation. message={} cause={}", ex.getMessage(), ex.getCause().getMessage());
            throw ex;
        } catch (Exception ex) {
            log.error("❌ Unexpected error while creating user username={} message={} stackTrace={}",
                    dto.getEmail(), ex.getMessage(), ex.getMessage());
            throw new KeycloakOperationException(
                    "USER_CREATION_FAILED", 3002,
                    "Unexpected error while creating user"
            );
        }
    }


    // ========================================================================
    // UPDATE USER
    // ========================================================================

    /**
     * Update an existing user both in DB and Keycloak.
     *
     * Steps:
     * - Load user and tenant
     * - Validate uniqueness (excluding this user)
     * - Update local DB (including mapped groups)
     * - Update Keycloak user
     */
    @Transactional
    public UsersDto updateUser(String userId, UsersDto dto) {

        log.info("➡️ [UPDATE USER] Start. userId={} dtoSummary={}", userId, summarizeDto(dto));
        log.debug("updateUser() incoming DTO details: username={}, email={}, phone={}", dto.getEmail(), dto.getEmail(), dto.getPhoneNumber());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.error("❌ Cannot update user. User not found: {}", userId);
                    return new ResourceNotFoundException("User not found: " + userId);
                });

        Tenant tenant = user.getTenant();
        log.debug("User resolved for update. userId={} tenantId={} realm={}", userId, tenant.getTenantID(), tenant.getRealmName());

        // Validate uniqueness, exclude this user id when checking
        validateUserFields(tenant, dto, userId);

        try {
            log.debug("Updating local DB user fields (firstName,lastName,email,username,phone,groups) for userId={}", userId);
            user.setFirstName(dto.getFirstName());
            user.setLastName(dto.getLastName());
            user.setEmail(dto.getEmail());
            user.setUserName(dto.getEmail());
            user.setPhoneNo(dto.getPhoneNumber());
            user.setMappedGroups(dto.getGroups());
            String status = dto.getStatus();
            if (status != null && !status.trim().isEmpty()) {
                user.setStatus(status.trim().toUpperCase());
            }
            userRepository.save(user);

            log.info("✔ Local DB user updated. userId={} username={}", userId, dto.getEmail());

            // Update Keycloak
            log.debug("Invoking Keycloak updateUser. realm={} kcUserId={} newUsername={} newEmail={}",
                    tenant.getRealmName(), user.getKeycloakUserId(), dto.getEmail(), dto.getEmail());

            kcUtil.updateUser(
                    tenant.getRealmName(),
                    user.getKeycloakUserId(),
                    dto.getEmail(),
                    dto.getEmail(),
                    dto.getFirstName(),
                    dto.getLastName()
            );

            log.info("✔ Keycloak user updated. kcUserId={}", user.getKeycloakUserId());
            log.debug("Completed update operations for userId={}", userId);

            return mapToDto(user);

        } catch (KeycloakOperationException ex) {
            log.error("❌ KC failure during update for userId={} message={} cause={}", userId, ex.getMessage(), ex.getCause().getMessage());
            throw ex;
        } catch (Exception ex) {
            log.error("❌ Unexpected error updating user {} message={} stackTrace={}", userId, ex.getMessage(), ex.getMessage());
            throw new KeycloakOperationException(
                    "USER_UPDATE_FAILED", 3003,
                    "Unexpected error updating user"
            );
        }
    }

    @Transactional
    public UsersDto updateUsersByParent(HttpServletRequest request, String userId, UsersDto dto) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        Tenant userTenant = user.getTenant();
        Tenant requestingTenant = jwtUtl.getTenantFromRequest(request);
        User requestingUser = jwtUtl.getUserFromRequest(request);

        // ---------------------------
        // AUTHORIZATION CHECK
        // ---------------------------
        boolean allowed = false;

        if (requestingTenant.getTenantID().equals(userTenant.getTenantID())) {
            allowed = true;
        } else {
            String parentId = userTenant.getParentTenantId();
            while (parentId != null) {
                if (parentId.equals(requestingTenant.getTenantID())) {
                    allowed = true;
                    break;
                }
                Tenant parent = tenantRepository.findById(parentId).orElse(null);
                if (parent == null) break;
                parentId = parent.getParentTenantId();
            }
        }

        if (!allowed) {
            throw new AccessDeniedException("Access denied to update user");
        }

        // ---------------------------
        // VALIDATE UNIQUE FIELDS
        // ---------------------------
        validateUserFields(userTenant, dto, userId);

        try {

            boolean identityChanged =
                    !dto.getEmail().equals(user.getUserName()) ||
                            !dto.getEmail().equals(user.getEmail()) ||
                            !dto.getFirstName().equals(user.getFirstName()) ||
                            !dto.getLastName().equals(user.getLastName());

            // ---------------------------
            // UPDATE SIMPLE FIELDS
            // ---------------------------
            user.setFirstName(dto.getFirstName());
            user.setLastName(dto.getLastName());
            user.setEmail(dto.getEmail());
            user.setUserName(dto.getEmail());
            user.setPhoneNo(dto.getPhoneNumber());
            user.setUpdatedAt(LocalDateTime.now());
            user.setLastUpdatedBy(requestingUser.getPkUserId());

            // ---------------------------
            // UPDATE GROUPS (bidirectional)
            // ---------------------------
            updateUserGroups(user, dto.getGroups());

            userRepository.save(user);

            // ---------------------------
            // KEYCLOAK UPDATE
            // ---------------------------
            if (identityChanged && user.getKeycloakUserId() != null) {
                kcUtil.updateUser(
                        userTenant.getRealmName(),
                        user.getKeycloakUserId(),
                        dto.getEmail(),
                        dto.getEmail(),
                        dto.getFirstName(),
                        dto.getLastName()
                );
            }

            return mapToDto(user);

        } catch (KeycloakOperationException ex) {
            throw ex;

        } catch (Exception ex) {
            throw new KeycloakOperationException(
                    "USER_UPDATE_FAILED", 3003,
                    "Unexpected error updating user"
            );
        }
    }


    private void updateUserGroups(User user, Set<Groups> incomingGroups) {

        if (incomingGroups == null) incomingGroups = Set.of();

        // Load groups as managed entities
        Set<Groups> managed = incomingGroups.stream()
                .map(g -> groupsRepository.findById(g.getPkGroupId())
                        .orElseThrow(() -> new ResourceNotFoundException("Group not found: " + g.getPkGroupId())))
                .collect(Collectors.toSet());

        // Replace user's group list
        user.setMappedGroups(managed);
    }






    // ========================================================================
    // DELETE USER
    // ========================================================================

    /**
     * Delete user from Keycloak and DB.
     *
     * Returns true if Keycloak deletion succeeded; DB deletion must succeed or method returns false.
     * Detailed logs added for both success and failure branches.
     */
    @Transactional
    public boolean deleteUser(String id) {

        log.info("➡️ [DELETE USER] Start. userId={}", id);

        User user = userRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("❌ Cannot delete. User not found: {}", id);
                    return new ResourceNotFoundException("User not found: " + id);
                });

        log.debug("Resolved user to delete: userId={} tenantId={} kcUserId={}",
                user.getPkUserId(), user.getTenant().getTenantID(), user.getKeycloakUserId());

        boolean kcDeleted = false;

        // Delete from Keycloak first (best-effort)
        try {
            log.debug("Attempting to delete from Keycloak. realm={} kcUserId={}",
                    user.getTenant().getRealmName(), user.getKeycloakUserId());

            kcUtil.removeUser(user.getTenant().getRealmName(), user.getKeycloakUserId());
            kcDeleted = true;

            log.info("✔ Deleted user from Keycloak. userId={} kcUserId={}", id, user.getKeycloakUserId());

        } catch (Exception e) {
            log.error("⚠️ Failed to delete user {} from Keycloak. kcUserId={} errorMessage={}", id, user.getKeycloakUserId(), e.getMessage());
            log.debug("Keycloak deletion stackTrace:", e);
        }

        // Delete from DB (must succeed for consistency)
        try {
            log.debug("Deleting user from DB now. dbUserId={}", id);
            userRepository.delete(user);
            log.info("✔ Deleted user from DB. userId={}", id);
        } catch (Exception e) {
            log.error("❌ Failed to delete user {} from DB: {}", id, e.getMessage());
            log.debug("DB delete stackTrace:", e);
            return false;
        }

        log.debug("deleteUser() completed for userId={} kcDeleted={}", id, kcDeleted);
        return kcDeleted;
    }


    // ========================================================================
    // GET USER
    // ========================================================================

    /**
     * Fetch user by DB id (no tenant checks).
     */
    public UsersDto getUser(String id) {
        log.info("➡️ [GET USER] Start. userId={}", id);

        User user = userRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("❌ User not found: {}", id);
                    return new ResourceNotFoundException("User not found");
                });

        log.info("✔ User fetched from DB: userId={}", id);
        log.debug("Fetched user details: username={} email={} tenantId={}",
                user.getUserName(), user.getEmail(), user.getTenant().getTenantID());

        return mapToDto(user);
    }

    /**
     * Fetch user by DB id but ensure requesting tenant is either same tenant or an ancestor (parent).
     * Uses jwtUtl to resolve requesting tenant from request principal.
     */
    public UsersDto getUserByParents(HttpServletRequest request, String id) {
        log.info("➡️ [GET USER BY PARENTS] Start. userId={}", id);

        User user = userRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("❌ User not found: {}", id);
                    return new ResourceNotFoundException("User not found");
                });

        log.debug("Target user resolved for parent-check. userId={} tenantId={}", id, user.getTenant().getTenantID());

        // Resolve requesting tenant from request (uses existing jwtUtl helper elsewhere in the project)
        Tenant requestingTenant = jwtUtl.getTenantFromRequest(request);
        String requestingTenantId = requestingTenant.getTenantID();
        log.debug("Requesting tenant resolved from JWT/email: tenantId={}", requestingTenantId);

        Tenant userTenant = user.getTenant();
        // Allow if requester is the same tenant
        if (requestingTenantId.equals(userTenant.getTenantID())) {
            log.info("✔ User fetched: {} (requesting tenant is same as user tenant)", id);
            return mapToDto(user);
        }

        // Walk up the user's tenant parent chain; allow if requesting tenant is an ancestor (parent) of the user's tenant
        log.debug("Walking up tenant parent chain to check ancestor relationship. startTenantId={}", userTenant.getTenantID());
        String parentId = userTenant.getParentTenantId();
        while (parentId != null) {
            log.debug("Checking parent tenantId={}", parentId);
            if (parentId.equals(requestingTenantId)) {
                log.info("✔ User fetched by parent tenant: {} (ancestor tenantId={})", id, requestingTenantId);
                return mapToDto(user);
            }
            Tenant parent = tenantRepository.findById(parentId).orElse(null);
            if (parent == null) {
                log.debug("Parent tenant not found in repository: {}. Breaking parent traversal.", parentId);
                break;
            }
            parentId = parent.getParentTenantId();
        }

        log.error("❌ Access denied for tenant {} to user {} after parent traversal", requestingTenantId, id);
        throw new org.springframework.security.access.AccessDeniedException("Access denied to user");
    }


    // ========================================================================
    // GET ALL USERS
    // ========================================================================

    /**
     * Return all users in the system. This method is verbose in logs to know how many users returned.
     */
    public List<UsersDto> getAllUsers(HttpServletRequest request) {
        log.info("➡️ [GET ALL USERS] Start fetching users for requesting tenant from DB");

        Tenant tenantFromRequest = jwtUtl.getTenantFromRequest(request);
        if (tenantFromRequest == null) {
            log.error("❌ Could not resolve tenant from request while fetching all users.");
            throw new ResourceNotFoundException("Tenant not found from request");
        }

        log.debug("Resolved tenant for getAllUsers: tenantId={} realm={}", tenantFromRequest.getTenantID(), tenantFromRequest.getRealmName());

        List<UsersDto> list = userRepository.findByTenant(tenantFromRequest).stream()
                .filter(u -> {
                    try {
                        return !u.isDefaultUser();
                    } catch (NoSuchMethodError | NullPointerException ex) {
                        return true;
                    }
                })
                .map(this::mapToDto)
                .collect(Collectors.toList());

        log.info("✔ Total users fetched for tenantId={} ={}", tenantFromRequest.getTenantID(), list.size());
        log.debug("User fetch complete. Sample size for debug: {}", Math.min(list.size(), 10));
        return list;
    }


    // ========================================================================
    // GET USERS BY TENANT
    // ========================================================================

    /**
     * Fetch users scoped to a tenant.
     */
    public List<UsersDto> getUsersByTenantId(String tenantId) {

        log.info("➡️ [GET USERS BY TENANT] Start. tenantId={}", tenantId);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> {
                    log.error("❌ Tenant not found while fetching users. tenantId={}", tenantId);
                    return new ResourceNotFoundException("Tenant not found");
                });

        log.debug("Resolved tenant for user fetch: tenantId={} realm={}", tenant.getTenantID(), tenant.getRealmName());

        List<UsersDto> list = userRepository.findByTenant(tenant)
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());

        log.info("✔ Users fetched for tenantId={} count={}", tenantId, list.size());
        return list;
    }


    // ========================================================================
    // VALIDATION (DB + KEYCLOAK)
    // ========================================================================

    /**
     * Validate uniqueness of email, username, phone in DB scoped to tenant and check Keycloak realm uniqueness.
     *
     * @param tenant tenant context to scope DB checks and KC realm
     * @param dto user DTO to validate
     * @param excludeUserId when updating, provide the existing user id to exclude from uniqueness checks
     */
    private void validateUserFields(Tenant tenant, UsersDto dto, String excludeUserId) {

        String tenantId = tenant.getTenantID();
        String realm = tenant.getRealmName();

        log.info("➡️ [VALIDATE USER] Start. tenantId={} realm={} excludeUserId={}", tenantId, realm, excludeUserId);
        log.debug("validateUserFields() parameters: username={} email={} phone={}", dto.getEmail(), dto.getEmail(), dto.getPhoneNumber());

        // ----- DB UNIQUE CHECKS -----
        log.debug("Checking DB uniqueness for EMAIL, USERNAME, PHONE");

        // EMAIL
        userRepository.findByEmailAndTenant_TenantID(dto.getEmail(), tenantId)
                .ifPresent(existing -> {
                    log.debug("DB email search found existing user: existingId={} existingEmail={}", existing.getPkUserId(), existing.getEmail());
                    if (!existing.getPkUserId().equals(excludeUserId)) {
                        log.error("❌ Email already exists in tenant. tenantId={} email={}", tenantId, dto.getEmail());
                        throw new KeycloakOperationException(
                                "EMAIL_EXISTS", 3101, "Email already exists in this tenant");
                    } else {
                        log.debug("Email match is the same user being updated (excluded). existingId={}", existing.getPkUserId());
                    }
                });

        // USERNAME
        userRepository.findByUserNameAndTenant_TenantID(dto.getEmail(), tenantId)
                .ifPresent(existing -> {
                    log.debug("DB username search found existing user: existingId={} existingUsername={}", existing.getPkUserId(), existing.getUserName());
                    if (!existing.getPkUserId().equals(excludeUserId)) {
                        log.error("❌ Username already exists in tenant. tenantId={} username={}", tenantId, dto.getEmail());
                        throw new KeycloakOperationException(
                                "USERNAME_EXISTS", 3102, "Username already exists in this tenant");
                    } else {
                        log.debug("Username match is the same user being updated (excluded). existingId={}", existing.getPkUserId());
                    }
                });

        // PHONE
        userRepository.findByPhoneNoAndTenant_TenantID(dto.getPhoneNumber(), tenantId)
                .ifPresent(existing -> {
                    log.debug("DB phone search found existing user: existingId={} existingPhone={}", existing.getPkUserId(), existing.getPhoneNo());
                    if (!existing.getPkUserId().equals(excludeUserId)) {
                        log.error("❌ Phone already exists in tenant. tenantId={} phone={}", tenantId, dto.getPhoneNumber());
                        throw new KeycloakOperationException(
                                "PHONE_EXISTS", 3103, "Phone number already exists in this tenant");
                    } else {
                        log.debug("Phone match is the same user being updated (excluded). existingId={}", existing.getPkUserId());
                    }
                });


        // ----- KEYCLOAK UNIQUE CHECK -----
        log.debug("Checking Keycloak realm uniqueness for username/email. realm={} username={} email={}", realm, dto.getEmail(), dto.getEmail());

        List<UserRepresentation> kcUsers =
                kcUtil.findUsersByUsernameOrEmail(
                        realm,
                        dto.getEmail(),
                        dto.getEmail()
                );

        log.debug("KC search result count={} realm={}", kcUsers.size(), realm);

        // If updating → exclude the same KC record
        String excludeKcId = null;
        if (excludeUserId != null) {
            excludeKcId = userRepository.findById(excludeUserId)
                    .map(User::getKeycloakUserId)
                    .orElse(null);
            log.debug("Excluding KC id from conflict check since updating user: excludeKcId={}", excludeKcId);
        }

        for (UserRepresentation kc : kcUsers) {
            log.debug("Examining KC user representation: id={} username={} email={}", kc.getId(), kc.getUsername(), kc.getEmail());
            if (excludeKcId != null && kc.getId().equals(excludeKcId)) {
                log.debug("Skipping KC user {} because it matches the excluded KC id (updating same user).", excludeKcId);
                continue;
            }

            log.error("❌ KC user conflict detected for realm={} username={} email={} existingKcId={}",
                    realm, dto.getEmail(), dto.getEmail(), kc.getId());

            throw new KeycloakOperationException(
                    "KC_USER_EXISTS", 3104,
                    "User already exists in Keycloak realm"
            );
        }

        log.info("✔ Validation passed for user={} realm={}", dto.getEmail(), realm);
    }


    // ========================================================================
    // DTO MAPPER
    // ========================================================================

    /**
     * Map domain User to UsersDto. Keep mapping explicit and log the mapping.
     */
    private UsersDto mapToDto(User user) {
        log.debug("Mapping User -> UsersDto. userId={}", user.getPkUserId());
        UsersDto dto = new UsersDto();
        dto.setPkUserId(user.getPkUserId());
        dto.setUserName(user.getUserName());
        dto.setFirstName(user.getFirstName());
        dto.setLastName(user.getLastName());
        dto.setEmail(user.getEmail());
        dto.setPhoneNumber(user.getPhoneNo());
        dto.setCreatedBy(user.getCreatedBy());
        dto.setLastUpdatedBy(user.getLastUpdatedBy());
        dto.setCreatedAt(user.getCreatedAt());
        dto.setUpdatedAt(user.getUpdatedAt());
        dto.setStatus(user.getStatus());
        dto.setGroups(user.getMappedGroups());
        log.debug("Mapped UsersDto: pkUserId={} username={} email={}", dto.getPkUserId(), dto.getEmail(), dto.getEmail());
        return dto;
    }

    // ========================================================================
    // QUICK EXISTENCE CHECKS (UTILITY ENDPOINTS)
    // ========================================================================

    /**
     * Check whether a mobile number exists in DB.
     */
    public boolean checkMobileNumber(String mobileNumber){
        log.info("➡️ [CHECK MOBILE] Start. mobileNumber={}", maskPhone(mobileNumber));
        boolean exists = userRepository.existsByPhoneNo(mobileNumber);
        log.debug("DB existsByPhoneNo returned: {}", exists);
        if (exists){
            log.info("✔ Mobile number exists in DB. mobileNumber={}", maskPhone(mobileNumber));
        } else{
            log.info("✔ Mobile number is available. mobileNumber={}", maskPhone(mobileNumber));
        }
        return exists;
    }

    /**
     * Check whether an email exists in DB.
     */
    public boolean checkEmail(String email){
        log.info("➡️ [CHECK EMAIL] Start. emailSummary={}", summarizeEmail(email));
        boolean exists = userRepository.existsByEmail(email);
        log.debug("DB existsByEmail returned: {}", exists);
        if (exists){
            log.info("✔ Email exists in DB. emailSummary={}", summarizeEmail(email));
        } else{
            log.info("✔ Email is available. emailSummary={}", summarizeEmail(email));
        }
        return exists;
    }

    /**
     * Check whether a username exists in DB.
     */
    public boolean checkUserName(String userName) {
        log.info("➡️ [CHECK USERNAME] Start. userName={}", userName);
        boolean exists = userRepository.existsByUserName(userName);
        log.debug("DB existsByUserName returned: {}", exists);
        if(exists){
            log.info("✔ Username exists in DB. username={}", userName);
        } else {
            log.info("✔ Username is available. username={}", userName);
        }
        return exists;
    }

    // ========================================================================
    // PRIVATE HELPERS (logging-friendly)
    // ========================================================================

    /**
     * Summarize DTO for logging without exposing sensitive fields.
     */
    private String summarizeDto(UsersDto dto) {
        if (dto == null) return "null";
        return String.format("username=%s email=%s phone=%s",
                dto.getEmail(), summarizeEmail(dto.getEmail()), maskPhone(dto.getPhoneNumber()));
    }

    /**
     * Mask phone for logs: show last 4 digits only.
     */
    private String maskPhone(String phone) {
        if (phone == null) return "null";
        int len = phone.length();
        if (len <= 4) return "****";
        return "****" + phone.substring(len - 4);
    }

    /**
     * Summarize email for logs: show domain and first char.
     */
    private String summarizeEmail(String email) {
        if (email == null) return "null";
        int at = email.indexOf('@');
        if (at <= 1) return "****" + (at > 0 ? email.substring(at) : "");
        String first = email.substring(0, 1);
        String domain = at > 0 ? email.substring(at) : "";
        return first + "****" + domain;
    }

    public User findByEmailAndTenant(String email, String tenantId) {
        return userRepository.findByEmailAndTenant_TenantID(email, tenantId).orElse(null);
    }
}