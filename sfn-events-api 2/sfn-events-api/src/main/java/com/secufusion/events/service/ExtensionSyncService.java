package com.secufusion.events.service;

import com.secufusion.events.dto.*;
import com.secufusion.events.entity.*;
import com.secufusion.events.exception.ResourceNotFoundException;
import com.secufusion.events.repository.*;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for handling extension sync from browser extension.
 * Supports MSI, Intune, and email-based tenant identification.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExtensionSyncService {

    private final TenantRepository tenantRepository;
    private final DeviceRepository deviceRepository;
    private final InstalledExtensionRepository installedExtensionRepository;
    private final ExtensionEventRepository extensionEventRepository;
    private final EntityManager entityManager;

    // High-risk permissions that increase risk score
    private static final Set<String> HIGH_RISK_PERMISSIONS = Set.of(
            "tabs", "webNavigation", "webRequest", "webRequestBlocking",
            "cookies", "history", "bookmarks", "downloads", "management",
            "nativeMessaging", "debugger", "privacy", "proxy",
            "clipboardRead", "clipboardWrite", "geolocation"
    );

    /**
     * Handle public (unauthenticated) extension sync.
     * Identifies tenant by tenantCode or email domain.
     */
    @Transactional
    public ExtensionSyncResponse syncExtensionsPublic(PublicExtensionSyncRequest request, String clientIp) {
        log.info("Processing public extension sync - tenantCode={}, email={}, fingerprint={}",
                request.getTenantCode(), request.getUserEmail(), request.getDeviceFingerprint());

        // 1. Identify tenant
        Tenant tenant = resolveTenant(request);
        String tenantId = tenant.getTenantID();

        // 2. Find or create device
        Device device = findOrCreateDevice(request, tenantId, clientIp);

        // 3. Sync extensions
        List<ExtensionSyncResponse.ExtensionPolicyResult> results = syncExtensions(
                device, request.getExtensions(), tenantId);

        // 4. Build response
        int allowedCount = (int) results.stream().filter(r -> "ALLOW".equals(r.getAction())).count();
        int blockedCount = (int) results.stream().filter(r -> "BLOCK".equals(r.getAction())).count();
        int warnedCount = (int) results.stream().filter(r -> "WARN".equals(r.getAction())).count();

        return ExtensionSyncResponse.builder()
                .success(true)
                .message("Extension sync completed")
                .syncTimestamp(LocalDateTime.now())
                .deviceId(device.getIsAnonymous() ? null : device.getDeviceId())
                .deviceToken(device.getDeviceToken())
                .totalExtensions(results.size())
                .allowedCount(allowedCount)
                .blockedCount(blockedCount)
                .warnedCount(warnedCount)
                .results(results)
                .policyConfig(buildPolicyConfig(tenantId))
                .build();
    }

    /**
     * Handle authenticated extension sync (user logged in).
     */
    @Transactional
    public ExtensionSyncResponse syncExtensionsAuthenticated(
            ExtensionSyncRequest request,
            String tenantId,
            String userId,
            String userName,
            String clientIp) {

        log.info("Processing authenticated extension sync - tenant={}, user={}, deviceId={}",
                tenantId, userId, request.getDeviceId());

        // 1. Find or create device
        Device device = findOrCreateAuthenticatedDevice(request, tenantId, userId, userName, clientIp);

        // 2. Check if this was an anonymous device being linked
        if (Boolean.TRUE.equals(device.getIsAnonymous()) && device.getLinkedUserId() == null) {
            linkAnonymousDevice(device, userId);
        }

        // 3. Sync extensions
        List<ExtensionSyncResponse.ExtensionPolicyResult> results = syncExtensions(
                device, request.getExtensions(), tenantId);

        // Update extensions with user info
        updateExtensionsWithUser(device.getDeviceId(), userId);

        // 4. Build response
        int allowedCount = (int) results.stream().filter(r -> "ALLOW".equals(r.getAction())).count();
        int blockedCount = (int) results.stream().filter(r -> "BLOCK".equals(r.getAction())).count();
        int warnedCount = (int) results.stream().filter(r -> "WARN".equals(r.getAction())).count();

        return ExtensionSyncResponse.builder()
                .success(true)
                .message("Extension sync completed")
                .syncTimestamp(LocalDateTime.now())
                .deviceId(device.getDeviceId())
                .deviceToken(device.getDeviceToken())
                .totalExtensions(results.size())
                .allowedCount(allowedCount)
                .blockedCount(blockedCount)
                .warnedCount(warnedCount)
                .results(results)
                .policyConfig(buildPolicyConfig(tenantId))
                .build();
    }

    /**
     * Resolve tenant from request using tenantCode or email domain.
     */
    private Tenant resolveTenant(PublicExtensionSyncRequest request) {
        // Try tenant code first (MSI/Intune deployment)
        if (request.getTenantCode() != null && !request.getTenantCode().isBlank()) {
            return tenantRepository.findByTenantCodeIgnoreCase(request.getTenantCode())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Tenant not found for code: " + request.getTenantCode()));
        }

        // Try email domain
        String domain = request.getEmailDomain();
        if (domain != null) {
            return tenantRepository.findByDomainIgnoreCase(domain)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Tenant not found for domain: " + domain));
        }

        throw new IllegalArgumentException(
                "Tenant identification required: provide tenantCode or userEmail");
    }

    /**
     * Resolve tenant from registration request using tenantCode or email domain.
     */
    private Tenant resolveTenantFromRequest(AnonymousDeviceRegistrationRequest request) {
        // Try tenant code first (MSI/Intune deployment)
        if (request.getTenantCode() != null && !request.getTenantCode().isBlank()) {
            return tenantRepository.findByTenantCodeIgnoreCase(request.getTenantCode())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Tenant not found for code: " + request.getTenantCode()));
        }

        // Try email domain
        String domain = request.getEmailDomain();
        if (domain != null) {
            return tenantRepository.findByDomainIgnoreCase(domain)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Tenant not found for domain: " + domain));
        }

        throw new IllegalArgumentException(
                "Tenant identification required: provide tenantCode or userEmail");
    }

    /**
     * Find or create device for public (anonymous) sync.
     */
    private Device findOrCreateDevice(PublicExtensionSyncRequest request, String tenantId, String clientIp) {
        // 1. Try to find by device token (returning device)
        if (request.isReturningDevice()) {
            Optional<Device> existingDevice = deviceRepository.findByDeviceTokenAndTenantId(
                    request.getDeviceToken(), tenantId);
            if (existingDevice.isPresent()) {
                Device device = existingDevice.get();
                updateDeviceInfo(device, request, clientIp);
                return deviceRepository.save(device);
            }
            log.warn("Device token not found: {}, creating new device", request.getDeviceToken());
        }

        // 2. Try to find by fingerprint
        if (request.getDeviceFingerprint() != null) {
            Optional<Device> existingDevice = deviceRepository.findByDeviceFingerprintAndTenantId(
                    request.getDeviceFingerprint(), tenantId);
            if (existingDevice.isPresent()) {
                Device device = existingDevice.get();
                updateDeviceInfo(device, request, clientIp);
                return deviceRepository.save(device);
            }
        }

        // 3. Create new anonymous device
        return createAnonymousDevice(request, tenantId, clientIp);
    }

    /**
     * Find or create device for authenticated sync.
     */
    private Device findOrCreateAuthenticatedDevice(
            ExtensionSyncRequest request,
            String tenantId,
            String userId,
            String userName,
            String clientIp) {

        // Try to find by device ID
        if (request.getDeviceId() != null) {
            Optional<Device> existingDevice = deviceRepository.findByDeviceIdAndTenantId(
                    request.getDeviceId(), tenantId);
            if (existingDevice.isPresent()) {
                Device device = existingDevice.get();
                updateAuthenticatedDeviceInfo(device, request, userId, userName, clientIp);
                return deviceRepository.save(device);
            }
        }

        // Try to find by device token
        if (request.getDeviceToken() != null) {
            Optional<Device> existingDevice = deviceRepository.findByDeviceTokenAndTenantId(
                    request.getDeviceToken(), tenantId);
            if (existingDevice.isPresent()) {
                Device device = existingDevice.get();
                updateAuthenticatedDeviceInfo(device, request, userId, userName, clientIp);
                return deviceRepository.save(device);
            }
        }

        // Create new authenticated device
        return createAuthenticatedDevice(request, tenantId, userId, userName, clientIp);
    }

    /**
     * Create new anonymous device.
     */
    private Device createAnonymousDevice(PublicExtensionSyncRequest request, String tenantId, String clientIp) {
        LocalDateTime now = LocalDateTime.now();
        String deviceType = request.getDeviceType() != null ?
                request.getDeviceType() : Device.parseDeviceType(request.getUserAgent());

        Device device = Device.builder()
                .tenantId(tenantId)
                .deviceName(Device.sanitizeDeviceName(
                        request.getDeviceName(), request.getBrowserType(),
                        request.getOsInfo(), deviceType, null))
                .deviceFingerprint(request.getDeviceFingerprint())
                .deviceToken(generateDeviceToken())
                .isAnonymous(true)
                .browserType(request.getBrowserType())
                .extensionVersion(request.getExtensionVersion())
                .userAgent(request.getUserAgent())
                .osInfo(request.getOsInfo())
                .deviceType(deviceType)
                .ipAddress(clientIp)
                .status(DeviceStatus.ACTIVE)
                .firstSeenAt(now)
                .lastSeenAt(now)
                .build();

        log.info("Creating anonymous device for tenant={}, fingerprint={}",
                tenantId, request.getDeviceFingerprint());

        device = deviceRepository.save(device);
        // Refresh to get trigger-populated relationships (deviceUser)
        entityManager.flush();
        entityManager.refresh(device);
        return device;
    }

    /**
     * Create new authenticated device.
     */
    private Device createAuthenticatedDevice(
            ExtensionSyncRequest request,
            String tenantId,
            String userId,
            String userName,
            String clientIp) {

        LocalDateTime now = LocalDateTime.now();

        Device device = Device.builder()
                .tenantId(tenantId)
                .userName(userName)
                .deviceToken(generateDeviceToken())
                .isAnonymous(false)
                .browserType(request.getBrowserType())
                .extensionVersion(request.getExtensionVersion())
                .osInfo(Device.parseDeviceType(null)) // Will be updated on next sync with user agent
                .deviceType("Desktop")
                .ipAddress(clientIp)
                .status(DeviceStatus.ACTIVE)
                .firstSeenAt(now)
                .lastSeenAt(now)
                .build();

        log.info("Creating authenticated device for tenant={}, user={}", tenantId, userId);

        device = deviceRepository.save(device);
        // Refresh to get trigger-populated relationships (deviceUser)
        entityManager.flush();
        entityManager.refresh(device);
        return device;
    }

    /**
     * Update device info from public request.
     */
    private void updateDeviceInfo(Device device, PublicExtensionSyncRequest request, String clientIp) {
        device.setLastSeenAt(LocalDateTime.now());
        device.setBrowserType(request.getBrowserType());
        device.setExtensionVersion(request.getExtensionVersion());
        device.setIpAddress(clientIp);
        if (request.getOsInfo() != null) device.setOsInfo(request.getOsInfo());
        if (request.getUserAgent() != null) device.setUserAgent(request.getUserAgent());
        if (request.getDeviceName() != null) {
            device.setDeviceName(Device.sanitizeDeviceName(
                    request.getDeviceName(), device.getBrowserType(),
                    device.getOsInfo(), device.getDeviceType(), device.getUserName()));
        }
        device.setStatus(DeviceStatus.ACTIVE);
    }

    /**
     * Update device info from authenticated request.
     */
    private void updateAuthenticatedDeviceInfo(
            Device device,
            ExtensionSyncRequest request,
            String userId,
            String userName,
            String clientIp) {

        device.setLastSeenAt(LocalDateTime.now());
        device.setUserName(userName);
        device.setBrowserType(request.getBrowserType());
        device.setExtensionVersion(request.getExtensionVersion());
        device.setIpAddress(clientIp);
        device.setStatus(DeviceStatus.ACTIVE);
    }

    /**
     * Link anonymous device to user.
     */
    private void linkAnonymousDevice(Device device, String userId) {
        device.setLinkedUserId(userId);
        device.setLinkedAt(LocalDateTime.now());
        log.info("Linked anonymous device {} to user {}", device.getDeviceId(), userId);
    }

    /**
     * Update extensions with user ID when user logs in.
     */
    private void updateExtensionsWithUser(String deviceId, String userId) {
        List<InstalledExtension> extensions = installedExtensionRepository
                .findByDeviceDeviceIdOrderByExtensionNameAsc(deviceId);
        for (InstalledExtension ext : extensions) {
            if (ext.getUserId() == null) {
                ext.setUserId(userId);
            }
        }
        installedExtensionRepository.saveAll(extensions);
    }

    /**
     * Sync extensions from browser to database.
     *
     * Before: 3N + M individual queries
     *   N = incoming extensions  → findByDeviceAndExtensionId + saveAndFlush + (maybe) createExtensionEvent.save per item
     *   M = uninstalled extensions → findByDeviceAndExtensionId + save + createExtensionEvent.save per item
     *
     * After: 4 queries total regardless of extension count:
     *   1. findActiveExtensionIds   – get current active set
     *   2. findByDeviceDeviceIdAndExtensionIdIn – batch load existing records for incoming IDs
     *   3. saveAll(toSave)          – batch upsert new + updated extensions
     *   4. findByDeviceDeviceIdAndExtensionIdIn – batch load records to mark uninstalled  (only if removals exist)
     *   5. saveAll(toUninstall)     – batch update uninstalled status            (only if removals exist)
     *   6. saveAll(pendingEvents)   – batch insert all events
     */
    private List<ExtensionSyncResponse.ExtensionPolicyResult> syncExtensions(
            Device device,
            List<ExtensionSyncRequest.ExtensionInfo> extensions,
            String tenantId) {

        LocalDateTime syncTime = LocalDateTime.now();

        // QUERY 1: existing active extension IDs on this device
        Set<String> existingActiveIds = new HashSet<>(
                installedExtensionRepository.findActiveExtensionIds(device.getDeviceId()));

        // Deduplicate incoming extensions by extensionId (keep first occurrence)
        Map<String, ExtensionSyncRequest.ExtensionInfo> uniqueExtensions = new LinkedHashMap<>();
        for (ExtensionSyncRequest.ExtensionInfo extInfo : extensions) {
            if (extInfo.getExtensionId() != null && !extInfo.getExtensionId().isBlank()) {
                uniqueExtensions.putIfAbsent(extInfo.getExtensionId(), extInfo);
            }
        }
        Set<String> incomingIds = uniqueExtensions.keySet();

        // QUERY 2: batch load all existing DB records for incoming IDs in one round-trip
        Map<String, InstalledExtension> existingMap = installedExtensionRepository
                .findByDeviceDeviceIdAndExtensionIdIn(device.getDeviceId(), new ArrayList<>(incomingIds))
                .stream()
                .collect(Collectors.toMap(InstalledExtension::getExtensionId, e -> e));

        List<InstalledExtension> toSave = new ArrayList<>();
        List<ExtensionEvent> pendingEvents = new ArrayList<>();
        List<ExtensionSyncResponse.ExtensionPolicyResult> results = new ArrayList<>();

        // Process each incoming extension entirely in-memory (no per-item DB calls)
        for (ExtensionSyncRequest.ExtensionInfo extInfo : uniqueExtensions.values()) {
            InstalledExtension extension;
            boolean isNew = false;
            boolean isUpdated = false;

            if (existingMap.containsKey(extInfo.getExtensionId())) {
                extension = existingMap.get(extInfo.getExtensionId());
                isUpdated = updateExtensionIfChanged(extension, extInfo);
                extension.setLastSeenAt(syncTime);
            } else {
                extension = createExtension(device, extInfo, tenantId);
                isNew = true;
            }

            evaluateExtensionPolicy(extension);
            toSave.add(extension);

            if (isNew) {
                pendingEvents.add(buildExtensionEvent(extension,
                        ExtensionEventType.EXTENSION_INSTALLED, "Extension installed on device"));
            } else if (isUpdated) {
                pendingEvents.add(buildExtensionEvent(extension,
                        ExtensionEventType.EXTENSION_UPDATED, "Extension version updated"));
            }

            results.add(buildPolicyResult(extension));
        }

        // QUERY 3: batch-save all new/updated extensions (assigns PKs to new entities)
        installedExtensionRepository.saveAll(toSave);

        // QUERIES 4-5: mark uninstalled extensions (only when there are removals)
        Set<String> uninstalledIds = new HashSet<>(existingActiveIds);
        uninstalledIds.removeAll(incomingIds);
        if (!uninstalledIds.isEmpty()) {
            List<InstalledExtension> toUninstall = installedExtensionRepository
                    .findByDeviceDeviceIdAndExtensionIdIn(device.getDeviceId(), new ArrayList<>(uninstalledIds));
            for (InstalledExtension ext : toUninstall) {
                ext.setStatus(ExtensionStatus.UNINSTALLED);
                ext.setUninstalledAt(syncTime);
                pendingEvents.add(buildExtensionEvent(ext,
                        ExtensionEventType.EXTENSION_UNINSTALLED, "Extension uninstalled from device"));
            }
            installedExtensionRepository.saveAll(toUninstall);
        }

        // QUERY 6: batch-save all events in one round-trip
        if (!pendingEvents.isEmpty()) {
            extensionEventRepository.saveAll(pendingEvents);
        }

        return results;
    }

    /**
     * Create new extension record.
     */
    private InstalledExtension createExtension(
            Device device,
            ExtensionSyncRequest.ExtensionInfo extInfo,
            String tenantId) {

        LocalDateTime now = LocalDateTime.now();

        return InstalledExtension.builder()
                .device(device)
                .tenantId(tenantId)
                .userId(device.getLinkedUserId())  // Use linkedUserId if device was linked to portal user
                .extensionId(extInfo.getExtensionId())
                .extensionName(extInfo.getName())
                .version(extInfo.getVersion())
                .description(extInfo.getDescription())
                .homepageUrl(extInfo.getHomepageUrl())
                .iconUrl(extInfo.getIconUrl())
                .permissions(extInfo.getPermissions())
                .hostPermissions(extInfo.getHostPermissions())
                .optionalPermissions(extInfo.getOptionalPermissions())
                .installType(extInfo.getInstallType())
                .isManaged(extInfo.getIsManaged() != null ? extInfo.getIsManaged() : false)
                .mayDisable(extInfo.getMayDisable() != null ? extInfo.getMayDisable() : true)
                .offlineEnabled(extInfo.getOfflineEnabled() != null ? extInfo.getOfflineEnabled() : false)
                .status(ExtensionStatus.ACTIVE)
                .firstSeenAt(now)
                .lastSeenAt(now)
                .installedAt(now)
                .build();
    }

    /**
     * Update extension if changed.
     */
    private boolean updateExtensionIfChanged(
            InstalledExtension extension,
            ExtensionSyncRequest.ExtensionInfo extInfo) {

        boolean updated = false;

        if (extInfo.getVersion() != null && !extInfo.getVersion().equals(extension.getVersion())) {
            extension.setVersion(extInfo.getVersion());
            updated = true;
        }
        if (extInfo.getName() != null) extension.setExtensionName(extInfo.getName());
        if (extInfo.getPermissions() != null) extension.setPermissions(extInfo.getPermissions());
        if (extInfo.getHostPermissions() != null) extension.setHostPermissions(extInfo.getHostPermissions());

        // Reactivate if was uninstalled
        if (extension.getStatus() == ExtensionStatus.UNINSTALLED) {
            extension.setStatus(ExtensionStatus.ACTIVE);
            extension.setUninstalledAt(null);
            updated = true;
        }

        return updated;
    }

    /**
     * Evaluate extension policy and set risk level.
     */
    private void evaluateExtensionPolicy(InstalledExtension extension) {
        // Calculate risk score based on permissions
        int riskScore = 0;
        List<String> highRiskPerms = new ArrayList<>();

        if (extension.getPermissions() != null) {
            for (String perm : extension.getPermissions()) {
                if (HIGH_RISK_PERMISSIONS.contains(perm.toLowerCase())) {
                    riskScore += 15;
                    highRiskPerms.add(perm);
                }
            }
        }

        // Host permissions with broad access increase risk
        if (extension.getHostPermissions() != null) {
            for (String host : extension.getHostPermissions()) {
                if (host.contains("<all_urls>") || host.contains("*://*/*")) {
                    riskScore += 30;
                    highRiskPerms.add("all_urls");
                    break;
                }
            }
        }

        // Cap at 100
        riskScore = Math.min(riskScore, 100);

        // Set risk level
        String riskLevel;
        if (riskScore >= 70) {
            riskLevel = "HIGH";
        } else if (riskScore >= 40) {
            riskLevel = "MEDIUM";
        } else if (riskScore >= 10) {
            riskLevel = "LOW";
        } else {
            riskLevel = "NONE";
        }

        extension.setRiskScore(riskScore);
        extension.setRiskLevel(riskLevel);
        extension.setHighRiskPermissions(highRiskPerms.isEmpty() ? null : highRiskPerms);

        // Set policy action based on risk
        // TODO: Integrate with actual policy rules from database
        if (Boolean.TRUE.equals(extension.getIsBlacklisted())) {
            extension.setPolicyAction("BLOCK");
            extension.setPolicyReason("Extension is blacklisted");
        } else if (Boolean.TRUE.equals(extension.getIsWhitelisted())) {
            extension.setPolicyAction("ALLOW");
            extension.setPolicyReason("Extension is whitelisted");
        } else if ("HIGH".equals(riskLevel)) {
            extension.setPolicyAction("WARN");
            extension.setPolicyReason("High-risk permissions detected");
        } else {
            extension.setPolicyAction("ALLOW");
            extension.setPolicyReason("Extension meets policy requirements");
        }
    }

    /**
     * Build an ExtensionEvent entity without saving.
     * Caller is responsible for batch-saving via extensionEventRepository.saveAll().
     */
    private ExtensionEvent buildExtensionEvent(InstalledExtension extension, ExtensionEventType eventType, String description) {
        return ExtensionEvent.builder()
                .device(extension.getDevice())
                .installedExtension(extension)
                .tenantId(extension.getTenantId())
                .userId(extension.getUserId())
                .extensionId(extension.getExtensionId())
                .extensionName(extension.getExtensionName())
                .extensionVersion(extension.getVersion())
                .eventType(eventType)
                .eventDescription(description)
                .policyAction(extension.getPolicyAction())
                .eventTimestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Build policy result for response.
     */
    private ExtensionSyncResponse.ExtensionPolicyResult buildPolicyResult(InstalledExtension extension) {
        return ExtensionSyncResponse.ExtensionPolicyResult.builder()
                .extensionId(extension.getExtensionId())
                .extensionName(extension.getExtensionName())
                .action(extension.getPolicyAction())
                .reason(extension.getPolicyReason())
                .matchedPolicyId(extension.getMatchedPolicyId())
                .isWhitelisted(extension.getIsWhitelisted())
                .isBlacklisted(extension.getIsBlacklisted())
                .riskLevel(extension.getRiskLevel())
                .riskScore(extension.getRiskScore())
                .highRiskPermissions(extension.getHighRiskPermissions())
                .warningMessage("WARN".equals(extension.getPolicyAction()) ?
                        "This extension has high-risk permissions" : null)
                .build();
    }

    /**
     * Build policy config for response.
     */
    private ExtensionSyncResponse.PolicyConfig buildPolicyConfig(String tenantId) {
        // TODO: Load actual policy config from database
        return ExtensionSyncResponse.PolicyConfig.builder()
                .policyId("default")
                .policyVersion("1.0")
                .defaultAction("ALLOW")
                .unknownExtensionAction("WARN")
                .blockHighRisk(false)
                .syncIntervalSeconds(300) // 5 minutes
                .build();
    }

    /**
     * Generate unique device token.
     */
    private String generateDeviceToken() {
        try {
            String source = UUID.randomUUID().toString() + System.nanoTime();
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(source.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // Fallback to simple UUID-based token
            return UUID.randomUUID().toString().replace("-", "") +
                   UUID.randomUUID().toString().replace("-", "");
        }
    }

    /**
     * Derive OS info from User-Agent string when osInfo is not explicitly provided.
     * Extracts the platform portion (e.g., "Windows NT 10.0" → "Windows 10/11").
     */
    private String deriveOsInfoFromUserAgent(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) return null;
        String ua = userAgent.toLowerCase();
        if (ua.contains("windows nt 10")) return "Windows 10/11";
        if (ua.contains("windows nt 6.3")) return "Windows 8.1";
        if (ua.contains("windows nt 6.1")) return "Windows 7";
        if (ua.contains("windows")) return "Windows";
        if (ua.contains("mac os x")) return "macOS";
        if (ua.contains("android")) return "Android";
        if (ua.contains("iphone") || ua.contains("ipad")) return "iOS";
        if (ua.contains("linux")) return "Linux";
        if (ua.contains("cros")) return "ChromeOS";
        return null;
    }

    // ===== Methods for existing PublicExtensionController =====

    /**
     * Register an anonymous device using tenant code (MSI/Intune) or email domain.
     */
    @Transactional
    public AnonymousDeviceResponse registerAnonymousDevice(AnonymousDeviceRegistrationRequest request) {
        log.info("Registering anonymous device for tenantCode={}, email={}, fingerprint={}",
                request.getTenantCode(), request.getUserEmail(), request.getDeviceFingerprint());

        // Validate tenant identifier
        if (!request.hasTenantIdentifier()) {
            throw new IllegalArgumentException("Tenant identification required: provide tenantCode or userEmail");
        }

        // Find tenant by code or email domain
        Tenant tenant = resolveTenantFromRequest(request);

        String tenantId = tenant.getTenantID();

        // Parse browser/device/OS info from user agent for all paths
        String parsedBrowserType = Device.parseBrowserType(request.getUserAgent());
        String parsedDeviceType = Device.parseDeviceType(request.getUserAgent());
        String osInfo = request.getOsInfo();
        // If osInfo not provided by extension, derive from user agent
        if ((osInfo == null || osInfo.isBlank()) && request.getUserAgent() != null) {
            osInfo = deriveOsInfoFromUserAgent(request.getUserAgent());
        }

        // Check if device already exists by fingerprint (includes authenticated devices)
        if (request.getDeviceFingerprint() != null && !request.getDeviceFingerprint().isBlank()) {
            Optional<Device> existingDevice = deviceRepository.findByDeviceFingerprintAndTenantId(
                    request.getDeviceFingerprint(), tenantId);
            if (existingDevice.isPresent()) {
                Device device = existingDevice.get();
                device.setLastSeenAt(LocalDateTime.now());
                device.setExtensionVersion(request.getExtensionVersion());
                device.setBrowserType(parsedBrowserType);
                device.setDeviceType(parsedDeviceType);
                device.setUserAgent(request.getUserAgent());
                device.setOsInfo(osInfo);
                if (request.getIpAddress() != null) {
                    device.setIpAddress(request.getIpAddress());
                }
                device.setDeviceName(Device.sanitizeDeviceName(
                        request.getDeviceName(), parsedBrowserType,
                        osInfo, parsedDeviceType, device.getUserName()));
                device.setStatus(DeviceStatus.ACTIVE);
                // Ensure device has a token for anonymous access
                if (device.getDeviceToken() == null || device.getDeviceToken().isBlank()) {
                    device.setDeviceToken(generateDeviceToken());
                }
                deviceRepository.save(device);
                log.info("Reusing existing device {} (anonymous={}) for tenant {}",
                        device.getDeviceId(), device.getIsAnonymous(), tenantId);
                return AnonymousDeviceResponse.fromEntity(device, false);
            }
        }

        // Create new anonymous device
        LocalDateTime now = LocalDateTime.now();

        Device device = Device.builder()
                .tenantId(tenantId)
                .deviceName(Device.sanitizeDeviceName(
                        request.getDeviceName(), parsedBrowserType,
                        osInfo, parsedDeviceType, null))
                .deviceFingerprint(request.getDeviceFingerprint())
                .deviceToken(generateDeviceToken())
                .isAnonymous(true)
                .browserType(parsedBrowserType)
                .deviceType(parsedDeviceType)
                .extensionVersion(request.getExtensionVersion())
                .userAgent(request.getUserAgent())
                .osInfo(osInfo)
                .ipAddress(request.getIpAddress())
                .status(DeviceStatus.ACTIVE)
                .firstSeenAt(now)
                .lastSeenAt(now)
                .build();

        device = deviceRepository.save(device);
        // Refresh to get trigger-populated relationships (deviceUser)
        entityManager.flush();
        entityManager.refresh(device);
        log.info("Created anonymous device {} for tenant {}", device.getDeviceId(), tenantId);

        return AnonymousDeviceResponse.fromEntity(device, true);
    }

    /**
     * Sync extensions for an anonymous device using device token.
     */
    @Transactional
    public ExtensionSyncResponse syncExtensionsAnonymous(ExtensionSyncRequest request) {
        log.info("Anonymous extension sync for deviceToken={}",
                request.getDeviceToken() != null ? request.getDeviceToken().substring(0, 8) + "..." : "null");

        // Find device by token
        Device device = deviceRepository.findByDeviceToken(request.getDeviceToken())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Device not found for token"));

        String tenantId = device.getTenantId();

        // Update device info
        device.setLastSeenAt(LocalDateTime.now());
        device.setBrowserType(request.getBrowserType());
        device.setExtensionVersion(request.getExtensionVersion());
        device.setStatus(DeviceStatus.ACTIVE);
        deviceRepository.save(device);

        // Sync extensions
        List<ExtensionSyncResponse.ExtensionPolicyResult> results = syncExtensions(
                device, request.getExtensions(), tenantId);

        int allowedCount = (int) results.stream().filter(r -> "ALLOW".equals(r.getAction())).count();
        int blockedCount = (int) results.stream().filter(r -> "BLOCK".equals(r.getAction())).count();
        int warnedCount = (int) results.stream().filter(r -> "WARN".equals(r.getAction())).count();

        return ExtensionSyncResponse.builder()
                .success(true)
                .message("Extension sync completed")
                .syncTimestamp(LocalDateTime.now())
                .deviceToken(device.getDeviceToken())
                .totalExtensions(results.size())
                .allowedCount(allowedCount)
                .blockedCount(blockedCount)
                .warnedCount(warnedCount)
                .results(results)
                .policyConfig(buildPolicyConfig(tenantId))
                .build();
    }

    /**
     * Process heartbeat from device.
     */
    @Transactional
    public HeartbeatResult processHeartbeat(String deviceToken, String extensionVersion) {
        Device device = deviceRepository.findByDeviceToken(deviceToken)
                .orElseThrow(() -> new ResourceNotFoundException("Device not found"));

        device.setLastSeenAt(LocalDateTime.now());
        if (extensionVersion != null) {
            device.setExtensionVersion(extensionVersion);
        }
        device.setStatus(DeviceStatus.ACTIVE);
        deviceRepository.save(device);

        return HeartbeatResult.builder()
                .success(true)
                .syncIntervalSeconds(300)
                .policyVersion("1.0")
                .requiresSync(false)
                .build();
    }

    /**
     * Result class for heartbeat processing.
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class HeartbeatResult {
        private Boolean success;
        private Integer syncIntervalSeconds;
        private String policyVersion;
        private Boolean requiresSync;
    }

    // ===== Methods for ExtensionController =====

    /**
     * Link anonymous device to user account.
     */
    @Transactional
    public DeviceResponse linkAnonymousDeviceToUser(String deviceToken, String tenantId, String userId, String userName) {
        Device device = deviceRepository.findByDeviceTokenAndTenantId(deviceToken, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Device not found for token"));

        if (!Boolean.TRUE.equals(device.getIsAnonymous())) {
            throw new IllegalStateException("Device is not anonymous");
        }

        device.setLinkedUserId(userId);
        device.setLinkedAt(LocalDateTime.now());
        device.setUserName(userName);
        device = deviceRepository.save(device);
        // Refresh to get trigger-populated relationships (deviceUser)
        entityManager.flush();
        entityManager.refresh(device);

        // Update extensions with user info
        updateExtensionsWithUser(device.getDeviceId(), userId);

        log.info("Linked anonymous device {} to user {}", device.getDeviceId(), userId);
        return DeviceResponse.fromEntity(device);
    }

    /**
     * Record user's acknowledgment of extension warning.
     */
    @Transactional
    public void acknowledgeWarning(String tenantId, String deviceId, String extensionId,
                                   String userId, String userName, String userAction, String userReason) {
        InstalledExtension extension = installedExtensionRepository
                .findByDeviceDeviceIdAndExtensionId(deviceId, extensionId)
                .orElseThrow(() -> new ResourceNotFoundException("Extension not found on device"));

        // Create warning acknowledgment event
        ExtensionEvent event = ExtensionEvent.builder()
                .device(extension.getDevice())
                .installedExtension(extension)
                .tenantId(tenantId)
                .userId(userId)
                .userName(userName)
                .extensionId(extensionId)
                .extensionName(extension.getExtensionName())
                .extensionVersion(extension.getVersion())
                .eventType(ExtensionEventType.EXTENSION_WARNING_ACKNOWLEDGED)
                .eventDescription("User " + userAction + " warning for extension")
                .policyAction(extension.getPolicyAction())
                .userAction(userAction)
                .userReason(userReason)
                .eventTimestamp(LocalDateTime.now())
                .build();

        extensionEventRepository.save(event);
        log.info("Recorded warning acknowledgment for extension {} action={}", extensionId, userAction);
    }

    /**
     * Search extensions by name or ID.
     */
    @Transactional(readOnly = true)
    public Page<InstalledExtensionDto> searchExtensions(String tenantId, String searchTerm, int page, int size) {
        return installedExtensionRepository.searchExtensions(tenantId, searchTerm,
                PageRequest.of(page, size))
                .map(InstalledExtensionDto::fromEntity);
    }

    /**
     * Get all extensions for tenant with pagination.
     */
    @Transactional(readOnly = true)
    public Page<InstalledExtensionDto> getAllExtensions(String tenantId, int page, int size) {
        return installedExtensionRepository.findByTenantIdOrderByLastSeenAtDesc(tenantId,
                PageRequest.of(page, size))
                .map(InstalledExtensionDto::fromEntity);
    }

    /**
     * Get extensions for a specific device.
     */
    @Transactional(readOnly = true)
    public List<InstalledExtensionDto> getDeviceExtensions(String deviceId) {
        return installedExtensionRepository.findByDeviceDeviceIdOrderByExtensionNameAsc(deviceId)
                .stream()
                .map(InstalledExtensionDto::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Get high-risk extensions for tenant.
     */
    @Transactional(readOnly = true)
    public List<InstalledExtensionDto> getHighRiskExtensions(String tenantId) {
        return installedExtensionRepository.findHighRiskExtensions(tenantId)
                .stream()
                .map(InstalledExtensionDto::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Get blocked extensions for tenant.
     */
    @Transactional(readOnly = true)
    public List<InstalledExtensionDto> getBlockedExtensions(String tenantId) {
        return installedExtensionRepository.findBlockedExtensions(tenantId)
                .stream()
                .map(InstalledExtensionDto::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Get extension statistics for tenant.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getExtensionStats(String tenantId) {
        Map<String, Object> stats = new HashMap<>();

        stats.put("totalExtensions", installedExtensionRepository.countUniqueExtensions(tenantId));
        stats.put("highRiskCount", installedExtensionRepository.countHighRiskExtensions(tenantId));
        stats.put("blockedCount", installedExtensionRepository.countByTenantIdAndPolicyAction(tenantId, "BLOCK"));

        // Risk level distribution
        List<Object[]> riskDistribution = installedExtensionRepository.countByRiskLevel(tenantId);
        Map<String, Long> riskLevels = new HashMap<>();
        for (Object[] row : riskDistribution) {
            riskLevels.put((String) row[0], ((Number) row[1]).longValue());
        }
        stats.put("riskDistribution", riskLevels);

        // Most common extensions
        List<Object[]> commonExts = installedExtensionRepository.findMostCommonExtensions(tenantId, 10);
        List<Map<String, Object>> common = new ArrayList<>();
        for (Object[] row : commonExts) {
            Map<String, Object> ext = new HashMap<>();
            ext.put("extensionId", row[0]);
            ext.put("extensionName", row[1]);
            ext.put("installCount", row[2]);
            common.add(ext);
        }
        stats.put("mostCommon", common);

        return stats;
    }

    /**
     * Get extension events for tenant with pagination.
     */
    @Transactional(readOnly = true)
    public Page<ExtensionEventDto> getExtensionEvents(String tenantId, int page, int size) {
        return extensionEventRepository.findByTenantIdOrderByEventTimestampDesc(tenantId,
                PageRequest.of(page, size))
                .map(ExtensionEventDto::fromEntity);
    }

    /**
     * Get extension events for a specific device.
     */
    @Transactional(readOnly = true)
    public Page<ExtensionEventDto> getDeviceExtensionEvents(String deviceId, int page, int size) {
        return extensionEventRepository.findByDeviceDeviceIdOrderByEventTimestampDesc(deviceId,
                PageRequest.of(page, size))
                .map(ExtensionEventDto::fromEntity);
    }

    /**
     * Clean up duplicate extensions in the database.
     * Keeps the oldest record for each device+extension combination.
     * @return number of duplicates removed
     */
    @Transactional
    public int cleanupDuplicateExtensions() {
        List<String> duplicateIds = installedExtensionRepository.findDuplicateExtensionIds();
        if (duplicateIds.isEmpty()) {
            log.info("No duplicate extensions found");
            return 0;
        }
        log.info("Found {} duplicate extensions to remove", duplicateIds.size());
        int deleted = installedExtensionRepository.deleteByIds(duplicateIds);
        log.info("Removed {} duplicate extensions", deleted);
        return deleted;
    }
}
