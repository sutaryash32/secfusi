package com.secufusion.iam.service;

import com.secufusion.iam.dto.CreateTenantRequest;
import com.secufusion.iam.dto.TenantResponse;
import com.secufusion.iam.entity.*;
import com.secufusion.iam.exception.KeycloakOperationException;
import com.secufusion.iam.exception.ResourceNotFoundException;
import com.secufusion.iam.repository.*;
import com.secufusion.iam.util.JwtUtl;
import com.secufusion.iam.util.KeycloakAdminUtil;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.WebApplicationException;

import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Refactored TenantService that delegates all Keycloak interactions to KeycloakAdminUtil.
 * <p>
 * Behavior preserved from previous implementation:
 * - resumable provisioning flow
 * - domain normalization (Option A: extract first segment and append extension)
 * - Do NOT set password in Keycloak (Option C) — only trigger UPDATE_PASSWORD + VERIFY_EMAIL
 * - All Keycloak direct calls replaced with kcUtil calls
 */
@Service
public class TenantService {

    private static final Logger log = LoggerFactory.getLogger(TenantService.class);

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthProviderConfigRepository authProviderConfigRepository;

    @Autowired
    private TenantTypeRepository tenantTypeRepository;

    @Autowired
    private KeycloakAdminUtil kcUtil;

    @Autowired
    private GroupService groupService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private CountryRepository countryRepository;

    @Autowired
    private StatesRepository stateRepository;

    @Autowired
    private CityRepository cityRepository;

    @Autowired
    private JwtUtl jwtUtl;


    @Value("${keycloak.admin.server-url}")
    private String baseUrl;

    /**
     * Domain extension (e.g. ".motivitylabs.net") - must include leading dot.
     * Example in application.properties: domain.extension=.motivitylabs.net
     */
    @Value("${domain.extension}")
    private String extension;

    @Value("${mail.smtp.host}")
    private String smtpHost;

    @Value("${mail.smtp.port}")
    private String smtpPort;

    @Value("${mail.smtp.auth}")
    private String smtpAuth;

    @Value("${mail.smtp.starttls}")
    private String smtpStarttls;

    @Value("${mail.smtp.username}")
    private String smtpUsername;

    @Value("${mail.smtp.password}")
    private String smtpPassword;

    @Value("${mail.smtp.mail}")
    private String smtpMail;

    // ============================================================================ CREATE (RESUMABLE FLOW)

    @Transactional
    public TenantResponse createTenant(HttpServletRequest request, CreateTenantRequest req){

        Tenant parentTenant = jwtUtl.getTenantFromRequest(request);

        log.info("Starting createTenant for realm='{}'", req.getTenantName());

        Optional<Tenant> existingOpt = tenantRepository.findByTenantName(req.getTenantName());
        boolean realmExists = kcUtil.realmExists(req.getTenantName());

        log.debug("Existing tenant present={}, realmExistsInKeycloak={}",
                existingOpt.isPresent(), realmExists);

        if (existingOpt.isPresent()) {
            Tenant existing = existingOpt.get();
            log.info("Tenant already exists in DB. tenantId={}, status={}",
                    existing.getTenantID(), existing.getStatus());

            if ("ACTIVE".equalsIgnoreCase(existing.getStatus())) {
                log.warn("Attempt to create an already active tenant. tenantId={}, tenantName={}",
                        existing.getTenantID(), existing.getTenantName());
                throw new KeycloakOperationException("TENANT_ALREADY_ACTIVE", 1015, "Tenant already active.");
            }

            log.info("Resuming tenant setup for existing tenant. tenantId={}, status={}",
                    existing.getTenantID(), existing.getStatus());
            return resumeTenantSetup(req);
        }

        // Validation
        validateInputForNew(req);

        User userFromRequest = jwtUtl.getUserFromRequest(request);
        // Create tenant skeleton
        Tenant tenant = buildTenantSkeleton(req);
        tenant.setStatus("CREATING");
        tenant.setCreatedBy(userFromRequest.getPkUserId());
        tenant.setParentTenantId(parentTenant != null ? parentTenant.getTenantID() : null);
        Tenant savedTenant = tenantRepository.save(tenant);
        log.info("Created tenant skeleton in DB. tenantId={}, status={}",
                savedTenant.getTenantID(), savedTenant.getStatus());

        // Create admin skeleton
        User admin = buildAdminSkeleton(req, savedTenant);
        admin.setStatus("CREATING");
        admin.setCreatedBy(userFromRequest.getPkUserId());
        User savedUser = userRepository.save(admin);
        req.setAdminUserName(savedUser.getUserName());
        log.info("Created admin user skeleton in DB. userId={}, username={}, status={}",
                savedUser.getPkUserId(), savedUser.getUserName(), savedUser.getStatus());

        savedTenant.setUsers(new ArrayList<>(List.of(savedUser)));
        savedTenant.setStatus("CREATED_LOCAL");
        tenantRepository.save(savedTenant);
        log.info("Updated tenant status to CREATED_LOCAL. tenantId={}", savedTenant.getTenantID());

        final String tenantId = savedTenant.getTenantID();
        final String adminUserId = savedUser.getPkUserId();

        log.info("[AUTO-CONFIG] Creating default Admin role + Admin group for tenantId={}", tenantId);

        log.info("[AUTO-CONFIG] Creating default Admin role + Admin group for tenantId={}", tenantId);

        String tenantTypeLower = Optional.ofNullable(savedTenant.getTenantType())
                .map(String::trim)
                .map(s -> s.toLowerCase(Locale.ROOT))
                .orElse("");

        Roles adminRole;
        switch (tenantTypeLower) {
            case "enterprise":
                adminRole = roleService.createOrGetEnterpriseAdminRole(
                        tenantId,
                        adminUserId
                );
                break;
            case "mssp":
                adminRole = roleService.createOrGetMsspAdminRole(
                        tenantId,
                        adminUserId
                );
                break;
            case "master mssp":
            case "master_mssp":
            case "mastermssp":
                adminRole = roleService.createOrGetMasterMsspAdminRole(
                        tenantId,
                        adminUserId
                );
                break;
            default:
                // Fallback to enterprise admin role if tenant type is unknown
                adminRole = roleService.createOrGetEnterpriseAdminRole(
                        tenantId,
                        adminUserId
                );
        }
        log.info("[AUTO-CONFIG] Admin Role created id={}", adminRole.getPkRoleId());
        log.info("[AUTO-CONFIG] Admin Role created id={}", adminRole.getPkRoleId());

        Groups adminGroup = groupService.createOrGetDefaultGroup(
                tenantId,
                savedTenant.getTenantName() + "_Admin",
                true,
                adminUserId
        );
        log.info("[AUTO-CONFIG] Admin Group created id={}", adminGroup.getPkGroupId());

        groupService.assignRoleToGroup(adminGroup, adminRole);
        log.info("[AUTO-CONFIG] Role mapped to group");

        groupService.assignUserToGroup(adminGroup, savedUser);
        log.info("[AUTO-CONFIG] Admin user assigned to Admin group");

        // Register rollback compensation: if DB rolls back, delete realm if created
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    try {
                        log.warn("DB rollback detected for tenant '{}', attempting to delete realm.", req.getTenantName());
                        kcUtil.deleteRealm(req.getTenantName());
                    } catch (Exception e) {
                        log.error("Compensation failed during realm deletion for '{}': {}", req.getTenantName(), e.getMessage(), e);
                    }
                }
            }
        });

        return resumeTenantSetup(req);
    }

    private void validateInputForNew(CreateTenantRequest req) {
        log.debug("Validating input for new tenant. tenantName={}, adminUserName={}, adminEmail={}, domain={}",
                req.getTenantName(), req.getAdminUserName(), req.getAdminEmail(), req.getDomain());

        // tenant name - required then existence
                if (req.getTenantName() == null || req.getTenantName().trim().isEmpty()) {
                    log.warn("Validation failed: tenant name is required.");
                    throw new KeycloakOperationException("ORGANIZATION_NAME_REQUIRED", 1000, "Organization name is required.");
                }
                log.debug("Checking tenant name availability. tenantName={}", req.getTenantName());
                if (tenantRepository.existsByTenantName(req.getTenantName())) {
                    log.warn("Validation failed: tenant name already exists. tenantName={}", req.getTenantName());
                    throw new KeycloakOperationException("ORGANIZATION_NAME_ALREADY_EXISTS", 1001, "Organization name already exists.");
                }

                // domain - required then normalize & existence
                if (req.getDomain() == null || req.getDomain().trim().isEmpty()) {
                    log.warn("Validation failed: domain is required.");
                    throw new KeycloakOperationException("INVALID_DOMAIN", 1007, "A valid domain must be provided.");
                }
                String dbDomain = normalizeDomainForDB(req.getDomain());
                log.debug("Checking domain availability. normalizedDomain={}", dbDomain);
                if (tenantRepository.existsByDomain(dbDomain)) {
                    log.warn("Validation failed: domain already exists. normalizedDomain={}", dbDomain);
                    throw new KeycloakOperationException("DOMAIN_ALREADY_EXISTS", 1002, "This domain is already registered.");
                }

                // organization phone - required then existence
                if (req.getPhoneNo() == null || req.getPhoneNo().trim().isEmpty()) {
                    log.warn("Validation failed: organization phone number is required.");
                    throw new KeycloakOperationException("ORGANIZATION_PHONE_NUMBER_REQUIRED", 1008, "Organization phone number is required.");
                }
                log.debug("Checking organization phone availability. phoneNo={}", req.getPhoneNo());
                if (tenantRepository.existsByPhoneNo(req.getPhoneNo())) {
                    log.warn("Validation failed: organization phone number already exists. phoneNo={}", req.getPhoneNo());
                    throw new KeycloakOperationException("ORGANIZATION_PHONE_NUMBER_ALREADY_EXISTS", 1009, "Organization Phone number already exists.");
                }

                // organization email - required then existence
                if (req.getEmail() == null || req.getEmail().trim().isEmpty()) {
                    log.warn("Validation failed: organization email is required.");
                    throw new KeycloakOperationException("ORGANIZATION_EMAIL_REQUIRED", 1011, "Organization email is required.");
                }
                log.debug("Checking organization email availability. email={}", req.getEmail());
                if (tenantRepository.existsByEmail(req.getEmail())) {
                    log.warn("Validation failed: email already exists in tenant table. email={}", req.getEmail());
                    throw new KeycloakOperationException("ORGANIZATION_EMAIL_ALREADY_EXISTS", 1012, "Organization email already exists.");
                }

                // admin username - (kept commented as before)
        //        if (req.getAdminUserName() == null || req.getAdminUserName().trim().isEmpty()) {
        //            log.warn("Validation failed: admin username is required.");
        //            throw new KeycloakOperationException("ADMIN_USERNAME_REQUIRED", 1006, "Admin username is required.");
        //        }
        //        if (userRepository.findByUserName(req.getAdminUserName()).isPresent()) {
        //            log.warn("Validation failed: username already taken. username={}", req.getAdminUserName());
        //            throw new KeycloakOperationException("ADMIN_USERNAME_ALREADY_EXISTS", 1003, "Admin Username already taken.");
        //        }

                // admin email - required then existence
                if (req.getAdminEmail() == null || req.getAdminEmail().trim().isEmpty()) {
                    log.warn("Validation failed: admin email is required.");
                    throw new KeycloakOperationException("ADMIN_EMAIL_REQUIRED", 1007, "Admin email is required.");
                }
                log.debug("Checking admin email availability. adminEmail={}", req.getAdminEmail());
                if (userRepository.findByEmail(req.getAdminEmail()).isPresent()) {
                    log.warn("Validation failed: email already exists. email={}", req.getAdminEmail());
                    throw new KeycloakOperationException("ADMIN_EMAIL_ALREADY_EXISTS", 1004, "Admin Email already exists.");
                }

                // admin phone - required then existence
                if (req.getAdminPhoneNumber() == null || req.getAdminPhoneNumber().trim().isEmpty()) {
                    log.warn("Validation failed: admin phone number is required.");
                    throw new KeycloakOperationException("ADMIN_PHONE_NUMBER_REQUIRED", 1017, "Admin phone number is required.");
                }
                log.debug("Checking admin phone availability. adminPhone={}", req.getAdminPhoneNumber());
                if (userRepository.findByPhoneNo(req.getAdminPhoneNumber()).isPresent()) {
                    log.warn("Validation failed: admin phone number already exists. phoneNo={}", req.getAdminPhoneNumber());
                    throw new KeycloakOperationException("ADMIN_PHONE_NUMBER_ALREADY_EXISTS", 1016, "Admin Phone number already exists.");
                }

                // region/country/state/city validation (unchanged)
                boolean b = regionRepository.existsRegionCountryStateCity(
                        req.getRegion(),
                        req.getPermanentAddress() != null ? req.getPermanentAddress().getCountry() : null,
                        req.getPermanentAddress() != null ? req.getPermanentAddress().getState() : null,
                        req.getPermanentAddress() != null ? req.getPermanentAddress().getCity() : null
                );
                if (!b) {
                    log.warn("Validation failed: region/country/state/city combination is invalid. region={}, country={}, state={}, city={}",
                            req.getRegion(),
                            req.getPermanentAddress() != null ? req.getPermanentAddress().getCountry() : null,
                            req.getPermanentAddress() != null ? req.getPermanentAddress().getState() : null,
                            req.getPermanentAddress() != null ? req.getPermanentAddress().getCity() : null);
        //            throw new KeycloakOperationException("INVALID_REGION_COUNTRY_STATE_CITY", 1021,
        //                    "The combination of region, country, state, and city is invalid.");
                }

                // final: check Keycloak realm existence
                boolean realmExists = kcUtil.realmExists(req.getTenantName());
                if (realmExists) {
                    log.warn("Validation failed: realm already exists in Keycloak. realm={}", req.getTenantName());
                    throw new KeycloakOperationException("ORGANIZATION_REALM_ALREADY_EXISTS", 1005, "Organization Realm already exists.");
                }

        log.debug("Validation for new tenant passed. tenantName={}", req.getTenantName());
    }

    private Tenant buildTenantSkeleton(CreateTenantRequest req) {
        log.debug("Building tenant skeleton for tenantName={}", req.getTenantName());
        Tenant tenant = new Tenant();
        tenant.setTenantName(req.getTenantName());
        tenant.setRealmName(req.getTenantName());
        tenant.setDomain(normalizeDomainForDB(req.getDomain()));
        tenant.setRegion(req.getRegion());
        tenant.setPhoneNo(req.getPhoneNo());
        tenant.setTenantType(req.getTenantType());
        tenant.setIndustry(req.getIndustry());
        tenant.setTemporaryAddress(req.getTemporaryAddress());
        tenant.setPermanentAddress(req.getPermanentAddress());
        tenant.setBillingAddress(req.getBillingAddress());
        tenant.setBillingCycleType(req.getBillingCycleType());
        tenant.setCreatedAt(Instant.now());
        tenant.setEmail(req.getEmail());
        return tenant;
    }

    private User buildAdminSkeleton(CreateTenantRequest req, Tenant tenant) {
        log.debug("Building admin skeleton for tenantName={}, adminUserName={}",
                req.getTenantName(), req.getAdminUserName());
        User admin = new User();
        admin.setFirstName(req.getAdminFirstName());
        admin.setLastName(req.getAdminLastName());
        String generatedUsername;
        if (req.getAdminUserName() != null && !req.getAdminUserName().trim().isEmpty()) {
            generatedUsername = req.getAdminUserName().trim().toLowerCase();
        } else {
            generatedUsername = generateUniqueUsername(req.getAdminFirstName(), req.getAdminLastName());
        }
        admin.setUserName(generatedUsername);
        admin.setEmail(req.getAdminEmail());
        admin.setPhoneNo(req.getAdminPhoneNumber());
        admin.setTenant(tenant);
        admin.setDefaultUser(true);
        return admin;
    }

    private String generateUniqueUsername(String first, String last) {
        String f = first == null ? "" : first.trim().toLowerCase().replaceAll("[^a-z]", "");
        String l = last == null ? "" : last.trim().toLowerCase().replaceAll("[^a-z]", "");
        if (f.isEmpty() && l.isEmpty()) {
            f = "user";
        }
        Random rnd = new Random();
        for (int attempt = 0; attempt < 200; attempt++) {
            int targetLen = 5 + rnd.nextInt(4); // 5..8
            String base = (f + l);
            if (base.isEmpty()) base = "user";
            int namePartLen = Math.max(1, targetLen - 1); // reserve 1 digit
            String namePart;
            if (base.length() <= namePartLen) {
                namePart = base;
            } else {
                int start = rnd.nextInt(base.length() - namePartLen + 1);
                namePart = base.substring(start, start + namePartLen);
            }
            int digit = rnd.nextInt(10);
            String candidate = (namePart + digit).replaceAll("[^a-z0-9]", "");
            // pad if too short
            while (candidate.length() < targetLen) {
                candidate = candidate + (char) ('a' + rnd.nextInt(26));
            }
            if (!Character.isLetter(candidate.charAt(0))) {
                candidate = "u" + candidate;
            }
            if (candidate.length() > 8) {
                candidate = candidate.substring(0, 8);
            }
            if (candidate.length() < 5) continue;
            if (!userRepository.findByUserName(candidate).isPresent()) {
                return candidate;
            }
        }
        throw new KeycloakOperationException("USERNAME_GENERATION_FAILED", 1020, "Unable to generate unique username.");
    }

    // ============================================================================ RESUME FLOW

    @Transactional
    public TenantResponse resumeTenantSetup(CreateTenantRequest req) {
        log.info("Resuming tenant setup. tenantName={}", req.getTenantName());

        Tenant tenant = tenantRepository.findByTenantName(req.getTenantName())
                .orElseThrow(() -> {
                    log.error("Tenant not found while resuming setup. tenantName={}", req.getTenantName());
                    return new KeycloakOperationException("TENANT_NOT_FOUND", 1014, "Tenant not found.");
                });

        log.info("Current tenant status={} for tenantId={}", tenant.getStatus(), tenant.getTenantID());

        return switch (tenant.getStatus()) {
            case "CREATING", "CREATED_LOCAL" -> {
                log.info("Starting full setup from REALM for tenantId={}", tenant.getTenantID());
                createRealm(tenant, req);
                createClient(tenant, req);
                createKeycloakAdminUser(tenant, req);
                finalizeAndSendEmails(tenant, req);
                saveAuthProviderConfig(tenant, req);
                yield buildResponse(tenant);
            }
            case "REALM_CREATED" -> {
                log.info("Resuming from CLIENT creation for tenantId={}", tenant.getTenantID());
                createClient(tenant, req);
                createKeycloakAdminUser(tenant, req);
                finalizeAndSendEmails(tenant, req);
                saveAuthProviderConfig(tenant, req);
                yield buildResponse(tenant);
            }
            case "CLIENT_CREATED" -> {
                log.info("Resuming from USER creation for tenantId={}", tenant.getTenantID());
                createKeycloakAdminUser(tenant, req);
                finalizeAndSendEmails(tenant, req);
                saveAuthProviderConfig(tenant, req);
                yield buildResponse(tenant);
            }
            case "USER_CREATED" -> {
                log.info("Resuming from FINALIZE/EMAIL step for tenantId={}", tenant.getTenantID());
                finalizeAndSendEmails(tenant, req);
                saveAuthProviderConfig(tenant, req);
                yield buildResponse(tenant);
            }
            case "ACTIVE" -> {
                log.info("Tenant already ACTIVE, returning existing response. tenantId={}", tenant.getTenantID());
                yield buildResponse(tenant);
            }
            default -> {
                log.error("Unknown provisioning state for tenantId={}, status={}",
                        tenant.getTenantID(), tenant.getStatus());
                throw new KeycloakOperationException("UNKNOWN_STATE", 1013,
                        "Invalid provisioning state.");
            }
        };
    }

    // ============================================================================ REALM

    private void createRealm(Tenant tenant, CreateTenantRequest req) {
        log.info("Ensuring Keycloak realm exists for '{}'", req.getTenantName());

        boolean existsBefore = kcUtil.realmExists(req.getTenantName());
        if (!existsBefore) {
            RealmRepresentation realmRep = new RealmRepresentation();
            realmRep.setRealm(req.getTenantName());
            realmRep.setEnabled(true);
            realmRep.setSmtpServer(getSmtpConfig());

            try {
                kcUtil.createRealm(realmRep);
            } catch (Exception ex) {
                if (ex instanceof WebApplicationException) {
                    int status = ((WebApplicationException) ex).getResponse().getStatus();
                    if (status == 409) {
                        log.warn("Realm already exists (conflict) for {}", req.getTenantName());
                        tenant.setStatus("REALM_CREATED");
                        tenantRepository.save(tenant);
                        return;
                    }
                }
                log.error("Failed to create realm {}: {}", req.getTenantName(), ex.getMessage(), ex);
                throw new KeycloakOperationException("REALM_CREATION_FAILED", 1006,
                        "Unable to create tenant environment.");
            }
        } else {
            log.info("Realm already exists in Keycloak for '{}', skipping creation.", req.getTenantName());
        }

        tenant.setStatus("REALM_CREATED");
        tenantRepository.save(tenant);
        log.info("Tenant status updated to REALM_CREATED. tenantId={}", tenant.getTenantID());
    }

    // ============================================================================ CLIENT

    private void createClient(Tenant tenant, CreateTenantRequest req) {
        log.info("Ensuring Keycloak client exists for realm={} clientId={}", req.getTenantName(), req.getTenantName());

        boolean existsBefore = kcUtil.clientExists(req.getTenantName(), req.getTenantName());
        if (!existsBefore) {
            String redirectUri = normalizeDomainForRedirect(req.getDomain());

            ClientRepresentation clientRep = new ClientRepresentation();
            clientRep.setClientId(req.getTenantName());
            clientRep.setName(req.getTenantName());
            clientRep.setProtocol("openid-connect");
            clientRep.setPublicClient(true);
            clientRep.setRedirectUris(List.of(redirectUri));
            clientRep.setWebOrigins(List.of("*"));
            clientRep.setStandardFlowEnabled(true);
            clientRep.setEnabled(true);

            try {
                kcUtil.createClient(req.getTenantName(), clientRep);
            } catch (Exception e) {
                log.error("Failed to create client for realm {}: {}", req.getTenantName(), e.getMessage(), e);
                throw new KeycloakOperationException("CLIENT_CREATION_FAILED", 1008,
                        "Unable to configure login access.");
            }
        } else {
            log.info("Client already exists in Keycloak for realm={}, skipping creation.", req.getTenantName());
        }

        tenant.setStatus("CLIENT_CREATED");
        // Save the domain in DB as normalized DB format
        tenant.setDomain(normalizeDomainForDB(req.getDomain()));
        tenantRepository.save(tenant);
        log.info("Tenant status updated to CLIENT_CREATED and domain saved. tenantId={}", tenant.getTenantID());
    }

    // ============================================================================ USER

    private void createKeycloakAdminUser(Tenant tenant, CreateTenantRequest req) {
        log.info("Ensuring Keycloak admin user exists for realm={}, username={}",
                req.getTenantName(), req.getAdminUserName());

        List<User> localUsers = tenant.getUsers();
        User admin = localUsers.stream().filter(User::isDefaultUser).findFirst().orElseThrow(() -> {
            log.error("Default admin user not found in local DB for tenantId={}", tenant.getTenantID());
            return new IllegalStateException("Default admin user not found.");
        });

        // Check Keycloak for existing user by username
        List<UserRepresentation> existing = kcUtil.findUserByUsername(req.getTenantName(), req.getAdminUserName());
        if (!existing.isEmpty()) {
            String existingUserId = existing.get(0).getId();
            log.info("Keycloak user already exists, linking local admin. realm={}, username={}, keycloakUserId={}",
                    req.getTenantName(), req.getAdminUserName(), existingUserId);

            admin.setKeycloakUserId(existingUserId);
            admin.setStatus("ACTIVE");
            userRepository.save(admin);

            tenant.setStatus("USER_CREATED");
            tenantRepository.save(tenant);
            log.info("Tenant status updated to USER_CREATED (existing user). tenantId={}", tenant.getTenantID());
            return;
        }

        // Create new Keycloak admin user (do not set password per option C)
        try {
            String createdUserId = kcUtil.createUser(
                    req.getTenantName(),
                    req.getAdminUserName(),
                    req.getAdminEmail(),
                    req.getAdminFirstName(),
                    req.getAdminLastName(),
                    req.getAdminPassword() != null && !req.getAdminPassword().trim().isEmpty()
            );

            // kcUtil.createUser returns null on conflict in this util, but we already checked above.
            if (createdUserId == null) {
                throw new KeycloakOperationException("USER_CREATION_FAILED", 1010,
                        "Unable to create tenant admin user (conflict).");
            }

           // Trigger required actions: if admin password provided, set it and only VERIFY_EMAIL; otherwise require UPDATE_PASSWORD + VERIFY_EMAIL
           String adminPassword = req.getAdminPassword();
           if (adminPassword != null && !adminPassword.trim().isEmpty()) {
               try {
                   kcUtil.setPassword(req.getTenantName(), createdUserId, adminPassword, false);
                   kcUtil.sendRequiredActionEmail(req.getTenantName(), createdUserId, List.of("VERIFY_EMAIL"));
               } catch (Exception e) {
                   log.error("Failed to set password or send verify email for user {}: {}", createdUserId, e.getMessage(), e);
                   throw new KeycloakOperationException("USER_CONFIG_FAILED", 1010, "Unable to configure tenant admin user.");
               }
           } else {
               kcUtil.sendRequiredActionEmail(req.getTenantName(), createdUserId, List.of("UPDATE_PASSWORD", "VERIFY_EMAIL"));
           }
            // Assign realm-admin role
            kcUtil.assignRealmAdminRole(req.getTenantName(), createdUserId);

            admin.setKeycloakUserId(createdUserId);
            admin.setStatus("ACTIVE");
            userRepository.save(admin);

            tenant.setStatus("USER_CREATED");
            tenantRepository.save(tenant);
            log.info("Tenant status updated to USER_CREATED (new user). tenantId={}", tenant.getTenantID());

        } catch (KeycloakOperationException kex) {
            throw kex;
        } catch (Exception e) {
            log.error("Failed to create or configure admin user in Keycloak: {}", e.getMessage(), e);
            throw new KeycloakOperationException("USER_CREATION_FAILED", 1010,
                    "Unable to create tenant admin user.");
        }
    }

    // ============================================================================ FINALIZE + EMAIL

    private void finalizeAndSendEmails(Tenant tenant, CreateTenantRequest req) {
        log.info("Finalizing tenant setup and sending welcome email. tenantId={}, adminEmail={}",
                tenant.getTenantID(), req.getAdminEmail());
        try {
            String loginUrl = generateLoginUrl(req);
            log.debug("Generated login URL for tenantName={}: {}", req.getTenantName(), loginUrl);
            tenant.setLoginUrl(loginUrl);
            tenant.setStatus("ACTIVE");
            tenantRepository.save(tenant);

            kcUtil.sendWelcomeEmail(req.getAdminEmail(), loginUrl, req.getAdminUserName());
            log.info("Tenant activated and welcome email sent successfully. tenantId={}", tenant.getTenantID());

        } catch (Exception e) {
            log.error("Failed to send welcome email or finalize tenant. tenantId={}, adminEmail={}, error={}",
                    tenant.getTenantID(), req.getAdminEmail(), e.getMessage(), e);
            throw new KeycloakOperationException("EMAIL_SEND_FAILED", 1011,
                    "Unable to send welcome email.", e);
        }
    }

    // ============================================================================ SAVE AUTH CONFIG

    private void saveAuthProviderConfig(Tenant tenant, CreateTenantRequest req) {
        log.info("Saving auth provider config if not exists. tenantId={}", tenant.getTenantID());

        if (authProviderConfigRepository.findByTenant(tenant).isPresent()) {
            log.info("Auth provider config already exists for tenantId={}, skipping.", tenant.getTenantID());
            return;
        }

        String redirectUri = normalizeDomainForRedirect(req.getDomain());
        String loginUrl = generateLoginUrl(req);
        log.debug("Using redirectUri={} and loginUrl={} for auth config. tenantId={}",
                redirectUri, loginUrl, tenant.getTenantID());

        AuthProviderConfig cfg = new AuthProviderConfig();
        cfg.setTenant(tenant);
        cfg.setSsoType("KEYCLOAK");
        cfg.setIssuerUri(baseUrl + "/realms/" + tenant.getRealmName());
        cfg.setAuthServerUrl(baseUrl);
        cfg.setTokenEndpoint(baseUrl + "/realms/" + tenant.getRealmName() + "/protocol/openid-connect/token");
        cfg.setJwkUri(baseUrl + "/realms/" + tenant.getRealmName() + "/protocol/openid-connect/certs");
        cfg.setClientId(tenant.getTenantName());
        cfg.setRedirectUri(redirectUri);
        cfg.setLoginUrl(loginUrl);
        cfg.setScopes("openid profile email");

        authProviderConfigRepository.save(cfg);
        log.info("Auth provider config saved successfully. tenantId={}", tenant.getTenantID());
    }

    private Map<String, String> getSmtpConfig() {
        log.debug("Building SMTP configuration map.");
        Map<String, String> smtp = new HashMap<>();
        smtp.put("host", smtpHost);
        smtp.put("port", smtpPort);
        smtp.put("from", smtpMail);
        smtp.put("user", smtpUsername);
        smtp.put("password", smtpPassword);
        smtp.put("auth", smtpAuth);
        smtp.put("starttls", smtpStarttls);
        smtp.put("ssl", "false");
        return smtp;
    }

    // ============================================================================ HELPERS (domain + login URL)

    /**
     * Option A: extract first segment and append extension.
     * Examples:
     * "support" -> "support.motivitylabs.net"
     * "support.motivitylabs.net" -> "support.motivitylabs.net"
     * "abc.xyz.com" -> "abc.motivitylabs.net"
     */
    private String normalizeDomainForDB(String domain) {
        log.debug("Normalizing domain for DB. rawDomain={}", domain);

        if (domain == null || domain.trim().isBlank()) {
            log.warn("Invalid domain provided while normalizing for DB. domain={}", domain);
            throw new KeycloakOperationException("INVALID_DOMAIN", 1007, "A valid domain must be provided.");
        }

        String d = domain.trim().toLowerCase();

        if (d.startsWith("http://")) d = d.substring(7);
        if (d.startsWith("https://")) d = d.substring(8);

        // if domain already contains the extension, return as-is
        if (d.contains(extension.replaceFirst("^\\.", "")) || d.endsWith(extension)) {
            log.debug("Domain already contains extension. normalizedDomain={}", d);
            return d;
        }

        // take first segment before any dot and append extension
        String first = d.contains(".") ? d.split("\\.")[0] : d;
        String normalized = first + extension;
        log.debug("Domain normalized to {}", normalized);
        return normalized;
    }

    private String normalizeDomainForRedirect(String domain) {
        String redirect = "https://" + normalizeDomainForDB(domain) + "/*";
        log.debug("Normalized domain for redirect. rawDomain={}, redirectUri={}", domain, redirect);
        return redirect;
    }

    private String generateLoginUrl(CreateTenantRequest req) {
        String redirect = "https://" + normalizeDomainForDB(req.getDomain());
        String loginUrl = baseUrl + "/realms/" + req.getTenantName()
                + "/protocol/openid-connect/auth?client_id=" + req.getTenantName()
                + "&redirect_uri=" + URLEncoder.encode(redirect, StandardCharsets.UTF_8)
                + "&response_type=code";
        log.debug("Generated login URL. tenantName={}, loginUrl={}", req.getTenantName(), loginUrl);
        return loginUrl;
    }

    private TenantResponse buildResponse(Tenant tenant) {
        log.debug("Building TenantResponse for tenantId={}, status={}",
                tenant.getTenantID(), tenant.getStatus());
        TenantResponse resp = new TenantResponse();
        resp.setTenantID(tenant.getTenantID());
        resp.setTenantName(tenant.getTenantName());
        resp.setRealmName(tenant.getRealmName());
        resp.setDomain(tenant.getDomain());
        resp.setRegion(tenant.getRegion());
        resp.setPhoneNo(tenant.getPhoneNo());
        resp.setStatus(tenant.getStatus());
        resp.setTenantType(tenant.getTenantType());
        resp.setIndustry(tenant.getIndustry());
        resp.setCreatedAt(tenant.getCreatedAt() != null ? tenant.getCreatedAt() : Instant.now());
        resp.setLoginUrl(tenant.getLoginUrl());
        return resp;
    }

    // ============================================================================ CRUD / LISTS

   @Transactional
    public TenantResponse updateTenant(HttpServletRequest request, String id, CreateTenantRequest req) {
        log.info("Updating tenant. tenantId={}, newTenantName={}", id, req.getTenantName());
        Tenant t = tenantRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Tenant not found for update. tenantId={}", id);
                    return new ResourceNotFoundException("Tenant not found: " + id);
                });

       User userFromRequest = jwtUtl.getUserFromRequest(request);
       // authorize: only parent (or the same tenant) can perform updates
        ensureRequesterIsParentOrSelf(request, t);

        t.setTenantName(req.getTenantName());
        t.setRealmName(req.getTenantName());
        t.setDomain(normalizeDomainForDB(req.getDomain()));
        t.setRegion(req.getRegion());
        t.setPhoneNo(req.getPhoneNo());
        t.setTenantType(req.getTenantType());
        t.setIndustry(req.getIndustry());
        t.setTemporaryAddress(req.getTemporaryAddress());
        t.setPermanentAddress(req.getPermanentAddress());
        t.setBillingAddress(req.getBillingAddress());
        t.setBillingCycleType(req.getBillingCycleType());
        t.setUpdatedBy(userFromRequest.getPkUserId());
        t.setUpdatedAt(LocalDateTime.now());
        if (req.getStatus() != null && !req.getStatus().trim().isEmpty()) {
            t.setStatus(req.getStatus().toUpperCase());
        }
        tenantRepository.save(t);
        log.info("Tenant updated successfully. tenantId={}", id);
        return buildResponse(t);
    }

    private void ensureRequesterIsParentOrSelf(HttpServletRequest request, Tenant target) {
        Tenant requester = jwtUtl.getTenantFromRequest(request);
        log.debug("Authorizing update. requesterTenantId={}, targetTenantId={}",
                requester != null ? requester.getTenantID() : "null", target.getTenantID());

        // allow if requester is the same tenant
        if (requester != null && requester.getTenantID().equals(target.getTenantID())) {
            return;
        }

        // traverse upwards from target through parentTenantId chain to see if requester is an ancestor
        String currentParentId = target.getParentTenantId();
        while (currentParentId != null && !currentParentId.isBlank()) {
            if (requester != null && currentParentId.equals(requester.getTenantID())) {
                return;
            }
            Optional<Tenant> parentOpt = tenantRepository.findByTenantID(currentParentId);
            if (parentOpt.isEmpty()) break;
            currentParentId = parentOpt.get().getParentTenantId();
        }

        log.warn("Access denied: requester tenantId={} is not an ancestor of tenantId={}",
                requester != null ? requester.getTenantID() : "null", target.getTenantID());
        throw new KeycloakOperationException("ACCESS_DENIED", 1022, "Access denied to tenant.");
    }

    @Transactional
    public void deleteTenant(String id) {
        log.info("Deleting tenant. tenantId={}", id);

        Tenant t = tenantRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Tenant not found for delete. tenantId={}", id);
                    return new ResourceNotFoundException("Tenant not found: " + id);
                });

        try {
            log.info("Attempting to delete Keycloak realm. realmName={}", t.getRealmName());
            kcUtil.deleteRealm(t.getRealmName());
            log.info("Keycloak realm deleted successfully. realmName={}", t.getRealmName());
        } catch (Exception e) {
            log.warn("Failed to delete Keycloak realm, continuing with local delete. realmName={}, error={}",
                    t.getRealmName(), e.getMessage(), e);
        }

        List<User> users = userRepository.findByTenant(t);
        log.debug("Deleting {} users for tenantId={}", users.size(), id);
        userRepository.deleteAll(users);

        tenantRepository.delete(t);
        log.info("Tenant deleted successfully from DB. tenantId={}", id);
    }

 @Transactional
 public TenantResponse getTenant(String id) {
     log.info("Fetching tenant by ID. tenantId={}", id);
     Tenant t = tenantRepository.findByTenantID(id)
             .orElseThrow(() -> {
                 log.warn("Tenant not found by ID. tenantId={}", id);
                 return new ResourceNotFoundException("Tenant not found: " + id);
             });
     return buildResponse(t);
 }

 @Transactional
 public TenantResponse getTenantIfParent(HttpServletRequest request, String id) {
     Tenant requester = jwtUtl.getTenantFromRequest(request);
     log.info("Fetching tenant by ID with parent-hierarchy check. requesterTenantId={}, targetTenantId={}",
             requester != null ? requester.getTenantID() : "null", id);

     Tenant target = tenantRepository.findByTenantID(id)
             .orElseThrow(() -> {
                 log.warn("Tenant not found by ID. tenantId={}", id);
                 return new ResourceNotFoundException("Tenant not found: " + id);
             });

     // allow if requester is the same tenant
     if (requester != null && requester.getTenantID().equals(target.getTenantID())) {
         return buildResponse(target);
     }

     // traverse upwards from target through parentTenantId chain to see if requester is an ancestor
     String currentParentId = target.getParentTenantId();
     while (currentParentId != null && !currentParentId.isBlank()) {
         if (requester != null && currentParentId.equals(requester.getTenantID())) {
             return buildResponse(target);
         }
         Optional<Tenant> parentOpt = tenantRepository.findByTenantID(currentParentId);
         if (parentOpt.isEmpty()) break;
         currentParentId = parentOpt.get().getParentTenantId();
     }

     log.warn("Access denied: requester tenantId={} is not an ancestor of tenantId={}",
             requester != null ? requester.getTenantID() : "null", id);
     throw new KeycloakOperationException("ACCESS_DENIED", 1022, "Access denied to tenant.");
 }
    @Transactional
    public List<TenantResponse> getAllTenants(HttpServletRequest request) {
        Tenant parentTenant = jwtUtl.getTenantFromRequest(request);
        List<Tenant> tenants = tenantRepository.findByParentTenantId(parentTenant.getTenantID());
        log.debug("Fetched {} tenants from DB.", tenants.size());
        List<TenantResponse> responses = tenants.stream()
                .map(this::buildResponse)
                .collect(java.util.stream.Collectors.toList());
        log.debug("Converted {} tenants to TenantResponse.", responses.size());
        return responses;
    }

    @Transactional
    public List<TenantType> getAllTenantTypes(HttpServletRequest request) {
        Tenant tenantFromEmail = jwtUtl.getTenantFromRequest(request);
        log.info("Fetching all tenant types.");
        List<TenantType> types = tenantTypeRepository.findAll();
        log.debug("Fetched {} tenant types from DB.", types.size());
        return types;
    }

public boolean checkTenantNameAvailability(String tenantName) {
    boolean exists = tenantRepository.existsByTenantName(tenantName);
    return !exists;
}

public boolean checkExistsByDomain(String domain) {
    String domainName = normalizeDomainForDB(domain);
    boolean exist = tenantRepository.existsByDomain(domainName);
    return exist;
}

public boolean checkPhoneNumber(String phoneNumber) {
    boolean exist = tenantRepository.existsByPhoneNo(phoneNumber);
    return exist;
}

public boolean checkEmail(String email) {
    boolean exist = tenantRepository.existsByEmail(email);
    return exist;
}

    public List<TenantResponse> getTenantHierarchy(HttpServletRequest request) {
        Tenant parentTenant = jwtUtl.getTenantFromRequest(request);
        String tenantId = parentTenant.getTenantID();
        List<Tenant> result = new ArrayList<>();

        // Start recursive search
        collectChildren(tenantId, result);

        List<TenantResponse> responses = result.stream()
                .map(this::buildResponse)
                .collect(java.util.stream.Collectors.toList());
        log.debug("Converted {} tenants to TenantResponse.", responses.size());
        return responses;
    }

    private void collectChildren(String tenantId, List<Tenant> result) {
        List<Tenant> children = tenantRepository.findByParentTenantId(tenantId);

        for (Tenant child : children) {
            result.add(child);
            collectChildren(child.getTenantID(), result); // recursive
        }
    }
}
