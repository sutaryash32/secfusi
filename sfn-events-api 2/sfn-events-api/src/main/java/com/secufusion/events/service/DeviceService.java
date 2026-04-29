package com.secufusion.events.service;

import com.secufusion.events.dto.DeviceRegistrationRequest;
import com.secufusion.events.dto.DeviceResponse;
import com.secufusion.events.dto.DeviceStatusUpdateRequest;
import com.secufusion.events.entity.Device;
import com.secufusion.events.entity.DeviceStatus;
import com.secufusion.events.entity.DeviceUser;
import com.secufusion.events.entity.PolicyAssignment;
import com.secufusion.events.exception.ResourceNotFoundException;
import com.secufusion.events.repository.DeviceRepository;
import com.secufusion.events.util.JwtUtl;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final EntityManager entityManager;
    private final DeviceUserGroupMappingService deviceUserGroupMappingService;
    private final JwtUtl jwtGroupExtractor;

    /**
     * Register a new device or update existing device by fingerprint.
     * For Azure tenants: Auto-assigns device user to authorized groups and resolves policies.
     */
    @Transactional
    public DeviceResponse registerDevice(String tenantId, String userId, String userName,
                                         String email, String displayName,
                                         DeviceRegistrationRequest request, HttpServletRequest httpRequest) {
        log.info("Registering device for tenant={} user={} email={}", tenantId, userName, email);

        // Check if device already exists by fingerprint
        Optional<Device> existingDevice = Optional.empty();
        if (request.getDeviceFingerprint() != null && !request.getDeviceFingerprint().isBlank()) {
            existingDevice = deviceRepository.findByDeviceFingerprintAndTenantId(
                    request.getDeviceFingerprint(), tenantId);
        }

        Device device;
        if (existingDevice.isPresent()) {
            // Update existing device
            device = existingDevice.get();
            device.setLastSeenAt(LocalDateTime.now());
            device.setUserAgent(request.getUserAgent());
            device.setExtensionVersion(request.getExtensionVersion());
            device.setIpAddress(request.getIpAddress());
            device.setDeviceType(Device.parseDeviceType(request.getUserAgent()));
            device.setBrowserType(Device.parseBrowserType(request.getUserAgent()));
            if (request.getDeviceName() != null && !request.getDeviceName().isBlank()) {
                device.setDeviceName(Device.sanitizeDeviceName(
                        request.getDeviceName(), device.getBrowserType(),
                        device.getOsInfo(), device.getDeviceType(), device.getUserName()));
            }
            if (device.getStatus() == DeviceStatus.INACTIVE) {
                device.setStatus(DeviceStatus.ACTIVE);
            }
            log.info("Updated existing device={} for tenant={}", device.getDeviceId(), tenantId);
        } else {
            // Create new device
            String parsedBrowser = Device.parseBrowserType(request.getUserAgent());
            String parsedDeviceType = Device.parseDeviceType(request.getUserAgent());
            device = Device.builder()
                    .tenantId(tenantId)
                    .userName(userName)
                    .deviceName(Device.sanitizeDeviceName(
                            request.getDeviceName(), parsedBrowser,
                            request.getOsInfo(), parsedDeviceType, userName))
                    .userAgent(request.getUserAgent())
                    .deviceType(parsedDeviceType)
                    .browserType(parsedBrowser)
                    .extensionVersion(request.getExtensionVersion())
                    .ipAddress(request.getIpAddress())
                    .osInfo(request.getOsInfo())
                    .deviceFingerprint(request.getDeviceFingerprint())
                    .status(DeviceStatus.ACTIVE)
                    .firstSeenAt(LocalDateTime.now())
                    .lastSeenAt(LocalDateTime.now())
                    .build();
            log.info("Created new device for tenant={} user={}", tenantId, userName);
        }

        device = deviceRepository.save(device);

        // Flush and refresh to get trigger-populated deviceUser relationship
        entityManager.flush();
        entityManager.refresh(device);

        // ========== AUTO-ASSIGN AND RESOLVE POLICIES ==========
        String deviceUserId = null;
        Map<String, Object> policies = Collections.emptyMap();
        boolean policiesRetrieved = false;

        try {
            // Extract Azure-specific claims (tid, groups) from JWT — only needed for Azure flow
            String azureTenantId = null;
            List<String> azureGroupIds = Collections.emptyList();

            String authHeader = httpRequest.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String jwtToken = authHeader.substring(7);
                azureTenantId = jwtGroupExtractor.extractAzureTenantIdFromToken(jwtToken);
                azureGroupIds = jwtGroupExtractor.extractAzureGroupsFromToken(jwtToken);
            }

            log.debug("JWT claims: userId={}, email={}, displayName={}, azureTenantId={}, groupCount={}",
                    userId, email, displayName, azureTenantId,
                    azureGroupIds != null ? azureGroupIds.size() : 0);

            // Check if Azure tenant
            boolean isAzureTenant = azureTenantId != null && !azureTenantId.isBlank();

            DeviceUser deviceUser;
            if (isAzureTenant && azureGroupIds != null && !azureGroupIds.isEmpty()) {
                // Azure tenant with groups — use email and name from JWT claims
                log.info("Processing Azure tenant auto-assignment for user: {}", email);
                deviceUser = deviceUserGroupMappingService.autoAssignAzureUserToGroups(
                        tenantId,
                        userId,       // userId from 'sub' claim
                        email,        // email from 'email' claim
                        displayName,  // displayName from 'name' claim
                        azureGroupIds // Azure AD group OIDs
                );
            } else {
                // APIKEY tenant or Azure tenant with no groups or MSI/Intune
                String effectiveEmail = email;           // from JWT 'email' claim (passed by controller)
                String effectiveDisplayName = displayName; // from JWT 'name' claim (passed by controller)

                if (effectiveEmail == null || effectiveEmail.isBlank()) {
                    // JWT has no email claim — use request payload (APIKEY flow)
                    effectiveEmail = request.getUserEmail();
                    effectiveDisplayName = request.getUserDisplayName();
                }
                if (effectiveEmail == null || effectiveEmail.isBlank()) {
                    // MSI/Intune flow: no JWT email, no request email.
                    // Use device fingerprint to create a unique per-device user
                    // so that all Intune-pushed devices don't collapse into one DeviceUser.
                    if (request.getDeviceFingerprint() != null && !request.getDeviceFingerprint().isBlank()) {
                        effectiveEmail = "device-" + request.getDeviceFingerprint() + "@" + tenantId;
                        log.info("MSI/Intune flow detected — using device fingerprint as identity: {}", effectiveEmail);
                    } else {
                        // Absolute fallback — no fingerprint either
                        effectiveEmail = "device-" + device.getDeviceId() + "@" + tenantId;
                        log.warn("MSI/Intune flow with no fingerprint — using deviceId as identity: {}", effectiveEmail);
                    }
                }
                if (effectiveDisplayName == null || effectiveDisplayName.isBlank()) {
                    effectiveDisplayName = request.getDeviceName() != null ? request.getDeviceName() : effectiveEmail;
                }

                log.info("Processing non-Azure or no-groups scenario for user: {} (email={}, displayName={})",
                        userName, effectiveEmail, effectiveDisplayName);

                deviceUser = deviceUserGroupMappingService.autoAssignAzureUserToGroups(
                        tenantId,
                        userId != null ? userId : UUID.randomUUID().toString(),
                        effectiveEmail,
                        effectiveDisplayName,
                        Collections.emptyList() // no groups
                );
            }

            deviceUserId = deviceUser.getPkDeviceUserId();

            // Resolve policies (one per type: browser, network, extension)
            List<PolicyAssignment> policyAssignments =
                    deviceUserGroupMappingService.resolvePoliciesForDeviceUser(tenantId, deviceUserId);

            policies = formatPoliciesResponse(policyAssignments);
            policiesRetrieved = true;

            log.info("Successfully processed device user registration: deviceUserId={}, policyCount={}",
                    deviceUserId, policyAssignments.size());
        } catch (Exception e) {
            log.error("Failed to process device user auto-assignment: {}", e.getMessage(), e);
            // Don't fail device registration - continue without policies
        }

        // Return response with policies
        return DeviceResponse.fromEntity(device);
    }

    /**
     * Format policy assignments response
     * Returns map with browserPolicy, networkPolicy, extensionPolicy keys
     */
    private Map<String, Object> formatPoliciesResponse(List<PolicyAssignment> assignments) {
        Map<String, Object> result = new HashMap<>();

        Map<String, Object> browserPolicy = null;
        Map<String, Object> networkPolicy = null;
        Map<String, Object> extensionPolicy = null;

        for (PolicyAssignment assignment : assignments) {
            if (assignment.getBrowserPolicy() != null && browserPolicy == null) {
                browserPolicy = Map.of(
                        "policyId", assignment.getBrowserPolicy().getPkBrowserPolicyId(),
                        "policyName", assignment.getBrowserPolicy().getName(),
                        "assignedVia", assignment.getAzureResourceName() != null ?
                                assignment.getAzureResourceName() : "Unknown",
                        "assignmentId", assignment.getId()
                );
            }
            if (assignment.getNetworkPolicy() != null && networkPolicy == null) {
                networkPolicy = Map.of(
                        "policyId", assignment.getNetworkPolicy().getPkNetworkPolicyId(),
                        "policyName", assignment.getNetworkPolicy().getName(),
                        "assignedVia", assignment.getAzureResourceName() != null ?
                                assignment.getAzureResourceName() : "Unknown",
                        "assignmentId", assignment.getId()
                );
            }
            if (assignment.getExtensionPolicy() != null && extensionPolicy == null) {
                extensionPolicy = Map.of(
                        "policyId", assignment.getExtensionPolicy().getPkExtensionPolicyId(),
                        "policyName", assignment.getExtensionPolicy().getName(),
                        "assignedVia", assignment.getAzureResourceName() != null ?
                                assignment.getAzureResourceName() : "Unknown",
                        "assignmentId", assignment.getId()
                );
            }
        }

        result.put("browserPolicy", browserPolicy);
        result.put("networkPolicy", networkPolicy);
        result.put("extensionPolicy", extensionPolicy);
        result.put("totalPolicies", assignments.size());

        return result;
    }
    /**
     * Get all devices for a tenant with pagination.
     */
    @Transactional(readOnly = true)
    public Page<DeviceResponse> getDevices(String tenantId, int page, int size) {
        log.debug("Getting devices for tenant={} page={} size={}", tenantId, page, size);
        Pageable pageable = PageRequest.of(page, size);
        return deviceRepository.findByTenantIdOrderByLastSeenAtDesc(tenantId, pageable)
                .map(DeviceResponse::fromEntity);
    }

    /**
     * Get devices filtered by status.
     */
    @Transactional(readOnly = true)
    public Page<DeviceResponse> getDevicesByStatus(String tenantId, DeviceStatus status,
                                                   int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return deviceRepository.findByTenantIdAndStatusOrderByLastSeenAtDesc(
                tenantId, status, pageable).map(DeviceResponse::fromEntity);
    }

    /**
     * Get a specific device.
     */
    @Transactional(readOnly = true)
    public DeviceResponse getDevice(String tenantId, String deviceId) {
        Device device = deviceRepository.findByDeviceIdAndTenantId(deviceId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Device not found: " + deviceId));
        return DeviceResponse.fromEntity(device);
    }

    /**
     * Get device entity by ID (internal use).
     */
    @Transactional(readOnly = true)
    public Optional<Device> getDeviceEntity(String deviceId) {
        return deviceRepository.findById(deviceId);
    }

    /**
     * Update device status.
     */
    @Transactional
    public DeviceResponse updateDeviceStatus(String tenantId, String deviceId,
                                             DeviceStatusUpdateRequest request) {
        log.info("Updating device={} status to {} for tenant={}",
                deviceId, request.getStatus(), tenantId);

        Device device = deviceRepository.findByDeviceIdAndTenantId(deviceId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Device not found: " + deviceId));

        device.setStatus(request.getStatus());
        device = deviceRepository.save(device);

        return DeviceResponse.fromEntity(device);
    }

    /**
     * Update device last seen timestamp (called on event receipt).
     */
    @Transactional
    public void updateLastSeen(String deviceId) {
        deviceRepository.findById(deviceId).ifPresent(device -> {
            device.setLastSeenAt(LocalDateTime.now());
            if (device.getStatus() == DeviceStatus.INACTIVE) {
                device.setStatus(DeviceStatus.ACTIVE);
            }
            deviceRepository.save(device);
        });
    }

    /**
     * Get device count statistics.
     */
    @Transactional(readOnly = true)
    public Map<String, Long> getDeviceStats(String tenantId) {
        Map<String, Long> stats = new HashMap<>();
        stats.put("total", deviceRepository.countByTenantId(tenantId));
        stats.put("active", deviceRepository.countByTenantIdAndStatus(tenantId, DeviceStatus.ACTIVE));
        stats.put("inactive", deviceRepository.countByTenantIdAndStatus(tenantId, DeviceStatus.INACTIVE));
        stats.put("blocked", deviceRepository.countByTenantIdAndStatus(tenantId, DeviceStatus.BLOCKED));
        return stats;
    }

    /**
     * Get device distribution by type.
     */
    @Transactional(readOnly = true)
    public Map<String, Long> getDevicesByType(String tenantId) {
        List<Object[]> results = deviceRepository.countDevicesByType(tenantId);
        Map<String, Long> byType = new HashMap<>();
        for (Object[] row : results) {
            String type = row[0] != null ? row[0].toString() : "Unknown";
            long count = ((Number) row[1]).longValue();
            byType.put(type, count);
        }
        return byType;
    }

    /**
     * Get recent devices.
     */
    @Transactional(readOnly = true)
    public List<DeviceResponse> getRecentDevices(String tenantId, int limit) {
        return deviceRepository.findRecentDevices(tenantId, limit)
                .stream()
                .map(DeviceResponse::fromEntity)
                .toList();
    }

    /**
     * Get devices for a specific user.
     */
    @Transactional(readOnly = true)
    public List<DeviceResponse> getDevicesForUser(String tenantId, String userName) {
        return deviceRepository.findByTenantIdAndUserNameOrderByLastSeenAtDesc(tenantId, userName)
                .stream()
                .map(DeviceResponse::fromEntity)
                .toList();
    }

    /**
     * Mark inactive devices (devices not seen in specified hours).
     */
    @Transactional
    public int markInactiveDevices(String tenantId, int hoursThreshold) {
        LocalDateTime threshold = LocalDateTime.now().minusHours(hoursThreshold);
        List<Device> inactiveDevices = deviceRepository.findInactiveDevices(tenantId, threshold);

        for (Device device : inactiveDevices) {
            device.setStatus(DeviceStatus.INACTIVE);
        }

        deviceRepository.saveAll(inactiveDevices);
        log.info("Marked {} devices as inactive for tenant={}", inactiveDevices.size(), tenantId);
        return inactiveDevices.size();
    }

    /**
     * Deactivate (soft delete) a device by setting status to INACTIVE.
     */
    @Transactional
    public DeviceResponse deactivateDevice(String tenantId, String deviceId) {
        log.info("Deactivating device={} for tenant={}", deviceId, tenantId);

        Device device = deviceRepository.findByDeviceIdAndTenantId(deviceId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Device not found: " + deviceId));

        device.setStatus(DeviceStatus.INACTIVE);
        device = deviceRepository.save(device);

        log.info("Device {} deactivated successfully", deviceId);
        return DeviceResponse.fromEntity(device);
    }

    /**
     * Search devices by name, OS, browser, or username.
     */
    @Transactional(readOnly = true)
    public Page<DeviceResponse> searchDevices(String tenantId, String searchTerm,
                                               DeviceStatus status, int page, int size) {
        log.debug("Searching devices for tenant={} term={} status={}", tenantId, searchTerm, status);
        Pageable pageable = PageRequest.of(page, size);

        if (status != null) {
            return deviceRepository.searchDevicesByStatus(tenantId, searchTerm, status, pageable)
                    .map(DeviceResponse::fromEntity);
        }
        return deviceRepository.searchDevices(tenantId, searchTerm, pageable)
                .map(DeviceResponse::fromEntity);
    }
}
