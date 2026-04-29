package com.secufusion.events.service;

import com.secufusion.events.dto.*;
import com.secufusion.events.entity.Device;
import com.secufusion.events.entity.DeviceStatus;
import com.secufusion.events.entity.DeviceUser;
import com.secufusion.events.entity.Event;
import com.secufusion.events.entity.InstalledExtension;
import com.secufusion.events.entity.User;
import com.secufusion.events.exception.ResourceNotFoundException;
import com.secufusion.events.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for user activity views.
 * Provides user list with devices, extensions, and activity summaries.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserActivityService {

    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;
    private final DeviceUserRepository deviceUserRepository;
    private final InstalledExtensionRepository installedExtensionRepository;
    private final EventRepository eventRepository;
    private final ExtensionEventRepository extensionEventRepository;

    /**
     * Get paginated list of users with summary counts.
     *
     * Previously called enrichUserListDto() per user which fired 5+ queries each.
     * Now fetches all enrichment data in 4 batch queries for the entire page.
     */
    @Transactional(readOnly = true)
    public Page<UserListDto> getUserList(String tenantId, int page, int size) {
        log.debug("Getting user list for tenant={} page={} size={}", tenantId, page, size);

        Pageable pageable = PageRequest.of(page, size);
        Page<User> usersPage = userRepository.findByTenant_TenantIDOrderByUserNameAsc(tenantId, pageable);

        List<UserListDto> userDtos = enrichUserPage(usersPage.getContent(), tenantId);
        return new PageImpl<>(userDtos, pageable, usersPage.getTotalElements());
    }

    /**
     * Search users with summary counts.
     */
    @Transactional(readOnly = true)
    public Page<UserListDto> searchUsers(String tenantId, String searchTerm, int page, int size) {
        log.debug("Searching users for tenant={} term={}", tenantId, searchTerm);

        Pageable pageable = PageRequest.of(page, size);
        Page<User> usersPage = userRepository.searchUsers(tenantId, searchTerm, pageable);

        List<UserListDto> userDtos = enrichUserPage(usersPage.getContent(), tenantId);
        return new PageImpl<>(userDtos, pageable, usersPage.getTotalElements());
    }

    /**
     * Batch-enrich a page of users.
     *
     * Before: 1 + 5×N queries (N users on the page)
     * After:  6 queries total regardless of page size:
     *   BATCH 1 – devices for page
     *   BATCH 2 – event counts by userName
     *   BATCH 3 – security event counts by userName
     *   BATCH 4 – last event times by userName
     *   BATCH 5 – total extension counts by userId
     *   BATCH 6 – high-risk extension counts by userId
     */
    private List<UserListDto> enrichUserPage(List<User> users, String tenantId) {
        if (users.isEmpty()) return Collections.emptyList();

        List<String> userIds = users.stream().map(User::getPkUserId).collect(Collectors.toList());
        List<String> userNames = users.stream()
                .map(User::getUserName).filter(n -> n != null).collect(Collectors.toList());

        // BATCH 1: load all devices for all users on this page in one query
        List<Device> allDevices = deviceRepository.findAllDevicesForUsers(tenantId, userIds, userNames);
        Map<String, List<Device>> devicesByUserId = new HashMap<>();
        Map<String, List<Device>> devicesByUserName = new HashMap<>();
        for (Device d : allDevices) {
            if (d.getLinkedUserId() != null) {
                devicesByUserId.computeIfAbsent(d.getLinkedUserId(), k -> new ArrayList<>()).add(d);
            } else if (d.getUserName() != null) {
                devicesByUserName.computeIfAbsent(d.getUserName(), k -> new ArrayList<>()).add(d);
            }
        }

        // BATCH 2: event counts by userName
        Map<String, Long> eventCountMap = eventRepository.countEventsByUserNames(tenantId, userNames)
                .stream().collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> ((Number) row[1]).longValue()));

        // BATCH 3: security event counts by userName
        Map<String, Long> secEventCountMap = eventRepository.countSecurityEventsByUserNames(tenantId, userNames)
                .stream().collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> ((Number) row[1]).longValue()));

        // BATCH 4: last event times by userName
        Map<String, LocalDateTime> lastEventMap = eventRepository.findLastEventTimesByUserNames(tenantId, userNames)
                .stream().collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> {
                            Object ts = row[1];
                            if (ts instanceof Timestamp) return ((Timestamp) ts).toLocalDateTime();
                            if (ts instanceof LocalDateTime) return (LocalDateTime) ts;
                            return null;
                        }));

        // BATCH 5: total extension counts per userId
        Map<String, Long> extCountMap = installedExtensionRepository
                .countExtensionsByUserIds(tenantId, userIds)
                .stream().collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> ((Number) row[1]).longValue()));

        // BATCH 6: high-risk extension counts per userId
        Map<String, Long> highRiskExtCountMap = installedExtensionRepository
                .countHighRiskExtensionsByUserIds(tenantId, userIds)
                .stream().collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> ((Number) row[1]).longValue()));

        return users.stream().map(user -> {
            UserListDto dto = UserListDto.fromEntity(user);
            String userId = user.getPkUserId();
            String userName = user.getUserName();

            // Resolve devices from the batch map
            List<Device> devices = devicesByUserId.getOrDefault(userId,
                    devicesByUserName.getOrDefault(userName, Collections.emptyList()));
            dto.setDeviceCount(devices.size());

            // Last activity from device
            LocalDateTime lastDeviceActivity = devices.stream()
                    .map(Device::getLastSeenAt)
                    .filter(d -> d != null)
                    .max(LocalDateTime::compareTo)
                    .orElse(null);

            // Extension counts from batch maps (0 when user has no devices)
            if (!devices.isEmpty()) {
                dto.setExtensionCount(extCountMap.getOrDefault(userId, 0L).intValue());
                dto.setHighRiskExtensionCount(highRiskExtCountMap.getOrDefault(userId, 0L).intValue());
            } else {
                dto.setExtensionCount(0);
                dto.setHighRiskExtensionCount(0);
            }

            // Event counts from batch maps
            dto.setEventCount(eventCountMap.getOrDefault(userName, 0L));
            dto.setSecurityEventCount(secEventCountMap.getOrDefault(userName, 0L));

            // Pick the more recent of device activity vs last event
            LocalDateTime lastEventTime = lastEventMap.get(userName);
            LocalDateTime lastActivity = (lastEventTime != null &&
                    (lastDeviceActivity == null || lastEventTime.isAfter(lastDeviceActivity)))
                    ? lastEventTime : lastDeviceActivity;
            dto.setLastActivityAt(lastActivity);

            return dto;
        }).collect(Collectors.toList());
    }

    /**
     * Get detailed user info with all devices.
     */
    @Transactional(readOnly = true)
    public UserWithDevicesDto getUserWithDevices(String tenantId, String userId) {
        log.debug("Getting user with devices for tenant={} userId={}", tenantId, userId);

        User user = userRepository.findByPkUserIdAndTenant_TenantID(userId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        UserWithDevicesDto dto = UserWithDevicesDto.fromEntity(user);

        // Get all devices for this user
        String userName = user.getUserName();
        List<Device> devices = deviceRepository.findAllUserDevices(tenantId, userId, userName);
        List<DeviceResponse> deviceResponses = devices.stream()
                .map(DeviceResponse::fromEntity)
                .collect(Collectors.toList());

        dto.setDevices(deviceResponses);
        dto.setTotalDevices(devices.size());
        dto.setActiveDevices((int) devices.stream()
                .filter(d -> d.getStatus() == DeviceStatus.ACTIVE)
                .count());

        // Last activity
        LocalDateTime lastActivity = devices.stream()
                .map(Device::getLastSeenAt)
                .filter(d -> d != null)
                .max(LocalDateTime::compareTo)
                .orElse(null);
        dto.setLastActivityAt(lastActivity);

        // Extension counts
        var extensions = installedExtensionRepository.findByTenantIdAndUserIdOrderByExtensionNameAsc(
                tenantId, userId);
        dto.setTotalExtensions(extensions.size());
        dto.setHighRiskExtensions((int) extensions.stream()
                .filter(ext -> "HIGH".equals(ext.getRiskLevel()))
                .count());
        dto.setBlockedExtensions((int) extensions.stream()
                .filter(ext -> "BLOCK".equals(ext.getPolicyAction()))
                .count());

        // Activity counts - get actual counts from EventRepository
        if (userName != null) {
            dto.setTotalEvents(eventRepository.countByTenantIdAndUserName(tenantId, userName));
            dto.setSecurityEvents(eventRepository.countSecurityEventsByUserName(tenantId, userName));
            dto.setPolicyViolations(eventRepository.countPolicyViolationsByUserName(tenantId, userName));

            // Also check last activity from events
            LocalDateTime lastEventTime = eventRepository.findLastEventTimeByUserName(tenantId, userName);
            if (lastEventTime != null && (lastActivity == null || lastEventTime.isAfter(lastActivity))) {
                dto.setLastActivityAt(lastEventTime);
            }
        } else {
            dto.setTotalEvents(0L);
            dto.setSecurityEvents(0L);
            dto.setPolicyViolations(0L);
        }

        return dto;
    }

    /**
     * Get devices for a specific user.
     */
    @Transactional(readOnly = true)
    public List<DeviceResponse> getUserDevices(String tenantId, String userId) {
        log.debug("Getting devices for user={} tenant={}", userId, tenantId);

        // Verify user exists
        User user = userRepository.findByPkUserIdAndTenant_TenantID(userId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        List<Device> devices = deviceRepository.findAllUserDevices(tenantId, userId, user.getUserName());
        return devices.stream()
                .map(DeviceResponse::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Get extensions for a specific user (across all devices).
     */
    @Transactional(readOnly = true)
    public List<InstalledExtensionDto> getUserExtensions(String tenantId, String userId) {
        log.debug("Getting extensions for user={} tenant={}", userId, tenantId);

        // Verify user exists
        userRepository.findByPkUserIdAndTenant_TenantID(userId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        return installedExtensionRepository.findByTenantIdAndUserIdOrderByExtensionNameAsc(tenantId, userId)
                .stream()
                .map(InstalledExtensionDto::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Get extension events for a specific user.
     */
    @Transactional(readOnly = true)
    public Page<ExtensionEventDto> getUserExtensionEvents(String tenantId, String userId, int page, int size) {
        log.debug("Getting extension events for user={} tenant={}", userId, tenantId);

        Pageable pageable = PageRequest.of(page, size);
        return extensionEventRepository.findByTenantIdAndUserIdOrderByEventTimestampDesc(tenantId, userId, pageable)
                .map(ExtensionEventDto::fromEntity);
    }

    /**
     * Get user statistics for tenant dashboard.
     *
     * Before: loads ALL users then fires 2N individual queries (catastrophic N+1).
     * After:  4 aggregate queries total — O(1) regardless of tenant size.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getUserStats(String tenantId) {
        Map<String, Object> stats = new HashMap<>();

        stats.put("totalUsers", userRepository.countByTenant_TenantID(tenantId));
        stats.put("activeUsers", userRepository.countActiveUsers(tenantId));
        stats.put("usersWithDevices", userRepository.countUsersWithDevices(tenantId));
        stats.put("usersWithHighRiskExtensions", userRepository.countUsersWithHighRiskExtensions(tenantId));

        return stats;
    }

    // ==================== DEVICE USER METHODS (Extension Users) ====================

    /**
     * Get paginated list of device users (extension users).
     *
     * Previously called enrichDeviceUserDTO() per user = 3N+1 queries (31 for page=10).
     * Now fetches all counts in 3 batch queries → 4 queries total regardless of page size.
     */
    @Transactional(readOnly = true)
    public Page<DeviceUserDTO> getDeviceUserList(String tenantId, int page, int size) {
        log.debug("Getting device user list for tenant={} page={} size={}", tenantId, page, size);

        Pageable pageable = PageRequest.of(page, size);
        Page<DeviceUser> duPage = deviceUserRepository.findByTenantIdOrderByLastSeenAtDesc(tenantId, pageable);
        List<DeviceUserDTO> dtos = enrichDeviceUserPage(duPage.getContent(), tenantId);
        return new PageImpl<>(dtos, pageable, duPage.getTotalElements());
    }

    /**
     * Search device users by email or name.
     */
    @Transactional(readOnly = true)
    public Page<DeviceUserDTO> searchDeviceUsers(String tenantId, String searchTerm, int page, int size) {
        log.debug("Searching device users for tenant={} term={}", tenantId, searchTerm);

        Pageable pageable = PageRequest.of(page, size);
        Page<DeviceUser> duPage = deviceUserRepository.searchUsers(tenantId, searchTerm, pageable);
        List<DeviceUserDTO> dtos = enrichDeviceUserPage(duPage.getContent(), tenantId);
        return new PageImpl<>(dtos, pageable, duPage.getTotalElements());
    }

    /**
     * Batch-enrich a page of DeviceUsers with device/event counts.
     *
     * Before: 1 + 3×N queries  → 31 DB round-trips for a page of 10
     * After:  1 (page) + 3 (batch counts) = 4 DB round-trips total
     */
    private List<DeviceUserDTO> enrichDeviceUserPage(List<DeviceUser> users, String tenantId) {
        if (users.isEmpty()) return Collections.emptyList();

        List<String> deviceUserIds = users.stream()
                .map(DeviceUser::getPkDeviceUserId)
                .collect(Collectors.toList());

        // BATCH 1: device counts for all users in one GROUP BY query
        Map<String, Long> deviceCountMap = deviceRepository
                .countDevicesByDeviceUserIds(tenantId, deviceUserIds)
                .stream().collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> ((Number) row[1]).longValue()));

        // BATCH 2: event counts for all users in one GROUP BY query
        Map<String, Long> eventCountMap = eventRepository
                .countEventsByDeviceUserIds(tenantId, deviceUserIds)
                .stream().collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> ((Number) row[1]).longValue()));

        // BATCH 3: security event counts for all users in one GROUP BY query
        Map<String, Long> secEventCountMap = eventRepository
                .countSecurityEventsByDeviceUserIds(tenantId, deviceUserIds)
                .stream().collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> ((Number) row[1]).longValue()));

        return users.stream().map(du -> {
            DeviceUserDTO dto = DeviceUserDTO.fromEntity(du);
            String id = du.getPkDeviceUserId();
            dto.setDeviceCount(deviceCountMap.getOrDefault(id, 0L));
            dto.setEventCount(eventCountMap.getOrDefault(id, 0L));
            dto.setSecurityEventCount(secEventCountMap.getOrDefault(id, 0L));
            return dto;
        }).collect(Collectors.toList());
    }

    /**
     * Get device user by ID with enriched data.
     */
    @Transactional(readOnly = true)
    public DeviceUserDTO getDeviceUser(String tenantId, String deviceUserId) {
        log.debug("Getting device user={} for tenant={}", deviceUserId, tenantId);

        DeviceUser deviceUser = deviceUserRepository.findById(deviceUserId)
                .filter(du -> du.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Device user not found: " + deviceUserId));

        return enrichDeviceUserDTO(deviceUser);
    }

    /**
     * Get device user by email.
     */
    @Transactional(readOnly = true)
    public Optional<DeviceUserDTO> getDeviceUserByEmail(String tenantId, String email) {
        return deviceUserRepository.findByTenantIdAndEmail(tenantId, email)
                .map(this::enrichDeviceUserDTO);
    }

    /**
     * Get devices for a specific device user.
     */
    @Transactional(readOnly = true)
    public List<DeviceResponse> getDeviceUserDevices(String tenantId, String deviceUserId) {
        log.debug("Getting devices for device user={} tenant={}", deviceUserId, tenantId);

        // Verify device user exists
        DeviceUser deviceUser = deviceUserRepository.findById(deviceUserId)
                .filter(du -> du.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Device user not found: " + deviceUserId));

        return deviceRepository.findByTenantIdAndDeviceUser_PkDeviceUserIdOrderByLastSeenAtDesc(tenantId, deviceUserId)
                .stream()
                .map(DeviceResponse::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Get events for a specific device user with pagination.
     */
    @Transactional(readOnly = true)
    public Page<EventDto> getDeviceUserEvents(String tenantId, String deviceUserId, int page, int size) {
        log.debug("Getting events for device user={} tenant={}", deviceUserId, tenantId);

        Pageable pageable = PageRequest.of(page, size);
        return eventRepository.findByTenant_TenantIDAndDeviceUser_PkDeviceUserIdOrderByTimeStampDesc(
                tenantId, deviceUserId, pageable)
                .map(event -> {
                    EventDto dto = new EventDto();
                    dto.setId(event.getPkEventId());
                    dto.setUrl(event.getUrl());
                    dto.setDomain(event.getDomain());
                    dto.setTimeStamp(event.getTimeStamp() != null ? event.getTimeStamp().toString() : null);
                    dto.setEventType(event.getEventType() != null ? event.getEventType().name() : null);
                    dto.setUserName(event.getUserName());
                    dto.setDeviceId(event.getDevice() != null ? event.getDevice().getDeviceId() : null);
                    dto.setIsPolicyViolation(event.getIsPolicyViolation());
                    dto.setIsSecurityEvent(event.getIsSecurityEvent());
                    dto.setSeverity(event.getSeverity());
                    return dto;
                });
    }

    /**
     * Get device user statistics for tenant dashboard.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getDeviceUserStats(String tenantId) {
        Map<String, Object> stats = new HashMap<>();

        // Total device users
        long totalDeviceUsers = deviceUserRepository.countByTenantId(tenantId);
        stats.put("totalDeviceUsers", totalDeviceUsers);

        // Active device users
        long activeDeviceUsers = deviceUserRepository.countByTenantIdAndStatus(tenantId, "ACTIVE");
        stats.put("activeDeviceUsers", activeDeviceUsers);

        // Blocked device users
        long blockedDeviceUsers = deviceUserRepository.countByTenantIdAndStatus(tenantId, "BLOCKED");
        stats.put("blockedDeviceUsers", blockedDeviceUsers);

        // Device users linked to portal
        long linkedToPortal = deviceUserRepository.countLinkedToPortal(tenantId);
        stats.put("linkedToPortal", linkedToPortal);

        // Extension-only users (not linked to portal)
        long extensionOnlyUsers = deviceUserRepository.countExtensionOnlyUsers(tenantId);
        stats.put("extensionOnlyUsers", extensionOnlyUsers);

        // Recently active (last 24 hours)
        LocalDateTime since24h = LocalDateTime.now().minusHours(24);
        List<DeviceUser> recentUsers = deviceUserRepository.findActiveUsersSince(tenantId, since24h);
        stats.put("activeInLast24Hours", recentUsers.size());

        // Recently active (last 7 days)
        LocalDateTime since7d = LocalDateTime.now().minusDays(7);
        List<DeviceUser> weeklyUsers = deviceUserRepository.findActiveUsersSince(tenantId, since7d);
        stats.put("activeInLast7Days", weeklyUsers.size());

        return stats;
    }

    /**
     * Enrich DeviceUserDTO with device and event counts.
     */
    private DeviceUserDTO enrichDeviceUserDTO(DeviceUser deviceUser) {
        DeviceUserDTO dto = DeviceUserDTO.fromEntity(deviceUser);

        String tenantId = deviceUser.getTenantId();
        String deviceUserId = deviceUser.getPkDeviceUserId();

        // Get device count
        long deviceCount = deviceRepository.countByTenantIdAndDeviceUser_PkDeviceUserId(tenantId, deviceUserId);
        dto.setDeviceCount(deviceCount);

        // Get event count
        long eventCount = eventRepository.countByTenant_TenantIDAndDeviceUser_PkDeviceUserId(tenantId, deviceUserId);
        dto.setEventCount(eventCount);

        // Get security event count
        long securityEventCount = eventRepository.countSecurityEventsByDeviceUserId(tenantId, deviceUserId);
        dto.setSecurityEventCount(securityEventCount);

        return dto;
    }

    // ==================== COMPREHENSIVE DEVICE USER DETAILS ====================

    /**
     * Get comprehensive device user details with all related information.
     *
     * Optimized to minimize database round-trips:
     * - Single consolidated query for all 9 event counts (replaces 9 separate queries)
     * - Single consolidated query for all 6 file operation counts (replaces 6 separate queries)
     * - Batch query for per-device event counts (eliminates N+1)
     * - Capped sub-collections: devices (50), extensions (100), locations (10)
     */
    @Transactional(readOnly = true)
    public DeviceUserDetailsDTO getDeviceUserDetails(String tenantId, String deviceUserId) {
        log.debug("Getting comprehensive device user details for deviceUserId={} tenant={}", deviceUserId, tenantId);

        // Get device user
        DeviceUser deviceUser = deviceUserRepository.findById(deviceUserId)
                .filter(du -> du.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Device user not found: " + deviceUserId));

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime last24h = now.minusHours(24);
        LocalDateTime last7d = now.minusDays(7);
        LocalDateTime last30d = now.minusDays(30);

        // Build the comprehensive DTO
        DeviceUserDetailsDTO.DeviceUserDetailsDTOBuilder builder = DeviceUserDetailsDTO.builder()
                // Basic info
                .deviceUserId(deviceUser.getPkDeviceUserId())
                .tenantId(deviceUser.getTenantId())
                .email(deviceUser.getEmail())
                .userName(deviceUser.getUserName())
                .displayName(deviceUser.getDisplayName())
                .portalUserId(deviceUser.getPortalUserId())
                .status(deviceUser.getStatus())
                .firstSeenAt(deviceUser.getFirstSeenAt())
                .lastSeenAt(deviceUser.getLastSeenAt())
                .createdAt(deviceUser.getCreatedAt())
                .updatedAt(deviceUser.getUpdatedAt())
                .linkedToPortal(deviceUser.getPortalUserId() != null);

        // Get devices and their info
        List<Device> devices = deviceRepository.findByTenantIdAndDeviceUser_PkDeviceUserIdOrderByLastSeenAtDesc(
                tenantId, deviceUserId);

        long activeDevices = devices.stream()
                .filter(d -> d.getStatus() == DeviceStatus.ACTIVE)
                .count();
        long inactiveDevices = devices.stream()
                .filter(d -> d.getStatus() == DeviceStatus.INACTIVE)
                .count();
        long blockedDevices = devices.stream()
                .filter(d -> d.getStatus() == DeviceStatus.BLOCKED)
                .count();

        builder.totalDevices(devices.size())
                .activeDevices(activeDevices)
                .inactiveDevices(inactiveDevices)
                .blockedDevices(blockedDevices);

        // Batch fetch per-device event counts (eliminates N+1 query)
        List<String> deviceIds = devices.stream().map(Device::getDeviceId).collect(Collectors.toList());
        Map<String, Long> deviceEventCounts = new HashMap<>();
        if (!deviceIds.isEmpty()) {
            List<Object[]> deviceCountRows = eventRepository.countEventsByDeviceIds(deviceIds);
            for (Object[] row : deviceCountRows) {
                deviceEventCounts.put(row[0].toString(), ((Number) row[1]).longValue());
            }
        }

        // Device list with details (capped at 50 to prevent oversized responses)
        List<DeviceUserDetailsDTO.DeviceInfo> deviceInfoList = devices.stream()
                .limit(50)
                .map(d -> DeviceUserDetailsDTO.DeviceInfo.builder()
                        .deviceId(d.getDeviceId())
                        .deviceName(d.getDeviceName())
                        .deviceType(d.getDeviceType())
                        .browserType(d.getBrowserType())
                        .osInfo(d.getOsInfo())
                        .extensionVersion(d.getExtensionVersion())
                        .status(d.getStatus() != null ? d.getStatus().name() : null)
                        .ipAddress(d.getIpAddress())
                        .location(d.getLocation())
                        .firstSeenAt(d.getFirstSeenAt())
                        .lastSeenAt(d.getLastSeenAt())
                        .eventCount(deviceEventCounts.getOrDefault(d.getDeviceId(), 0L))
                        .build())
                .collect(Collectors.toList());
        builder.devices(deviceInfoList);

        // Consolidated event counts - single query replaces 9 separate count queries
        List<Object[]> countRows = eventRepository.getComprehensiveCountsForDeviceUser(
                tenantId, deviceUserId, last24h, last7d, last30d);
        long totalEvents = 0, securityEvents = 0, policyViolations = 0, blockedOperations = 0;
        if (!countRows.isEmpty()) {
            Object[] counts = countRows.get(0);
            totalEvents = ((Number) counts[0]).longValue();
            builder.totalEvents(totalEvents)
                    .eventsLast24Hours(((Number) counts[1]).longValue())
                    .eventsLast7Days(((Number) counts[2]).longValue())
                    .eventsLast30Days(((Number) counts[3]).longValue());

            securityEvents = ((Number) counts[4]).longValue();
            builder.securityEvents(securityEvents)
                    .securityEventsLast30Days(((Number) counts[5]).longValue());

            policyViolations = ((Number) counts[6]).longValue();
            builder.policyViolations(policyViolations)
                    .policyViolationsLast30Days(((Number) counts[7]).longValue());

            blockedOperations = ((Number) counts[8]).longValue();
            builder.blockedOperations(blockedOperations);
        }

        // Activity breakdown (event types)
        List<Object[]> eventTypeStats = eventRepository.countEventsByTypeForDeviceUser(tenantId, deviceUserId);
        long totalForPercentage = totalEvents > 0 ? totalEvents : 1;
        List<DeviceUserDetailsDTO.EventTypeBreakdown> activityBreakdown = eventTypeStats.stream()
                .map(row -> DeviceUserDetailsDTO.EventTypeBreakdown.builder()
                        .eventType(row[0] != null ? row[0].toString() : "Unknown")
                        .count(((Number) row[1]).longValue())
                        .percentage(Math.round(((Number) row[1]).doubleValue() / totalForPercentage * 10000) / 100.0)
                        .build())
                .collect(Collectors.toList());
        builder.activityBreakdown(activityBreakdown);

        // Top visited domains
        List<Object[]> domainStats = eventRepository.getTopDomainsForDeviceUser(tenantId, deviceUserId, 10);
        List<DeviceUserDetailsDTO.DomainStats> topDomains = domainStats.stream()
                .map(row -> DeviceUserDetailsDTO.DomainStats.builder()
                        .domain(row[0] != null ? row[0].toString() : "Unknown")
                        .visitCount(((Number) row[1]).longValue())
                        .percentage(Math.round(((Number) row[1]).doubleValue() / totalForPercentage * 10000) / 100.0)
                        .build())
                .collect(Collectors.toList());
        builder.topVisitedDomains(topDomains);

        // Recent activity (last 20 events)
        List<Event> recentEvents = eventRepository.findTop20ByDeviceUser_PkDeviceUserIdOrderByTimeStampDesc(deviceUserId);
        List<DeviceUserDetailsDTO.RecentActivity> recentActivity = recentEvents.stream()
                .map(e -> DeviceUserDetailsDTO.RecentActivity.builder()
                        .eventId(e.getPkEventId())
                        .eventType(e.getEventType() != null ? e.getEventType().name() : null)
                        .description(buildEventDescription(e))
                        .url(e.getUrl())
                        .domain(e.getDomain())
                        .deviceName(e.getDevice() != null ? e.getDevice().getDeviceName() : null)
                        .timestamp(e.getTimeStamp())
                        .severity(e.getSeverity())
                        .isPolicyViolation(Boolean.TRUE.equals(e.getIsPolicyViolation()))
                        .isSecurityEvent(Boolean.TRUE.equals(e.getIsSecurityEvent()))
                        .build())
                .collect(Collectors.toList());
        builder.recentActivity(recentActivity);

        // Extensions (for all user's devices, deduplicated by extensionId)
        long highRiskExtensions = 0;
        if (!deviceIds.isEmpty()) {
            List<InstalledExtension> extensions = installedExtensionRepository.findByDeviceDeviceIdInOrderByExtensionNameAsc(deviceIds);

            // Deduplicate by extensionId: group all installs, pick latest version,
            // highest risk level, merge device names and permissions
            Map<String, List<InstalledExtension>> grouped = extensions.stream()
                    .collect(Collectors.groupingBy(InstalledExtension::getExtensionId, java.util.LinkedHashMap::new, Collectors.toList()));

            List<DeviceUserDetailsDTO.ExtensionInfo> extensionInfoList = new ArrayList<>();
            long dedupHighRisk = 0;
            long dedupBlocked = 0;

            for (Map.Entry<String, List<InstalledExtension>> entry : grouped.entrySet()) {
                List<InstalledExtension> group = entry.getValue();

                // Pick the representative: prefer ACTIVE over UNINSTALLED, then latest installedAt
                InstalledExtension representative = group.stream()
                        .sorted((a, b) -> {
                            // ACTIVE first
                            int statusCmp = Boolean.compare(
                                    "ACTIVE".equals(b.getStatus() != null ? b.getStatus().name() : ""),
                                    "ACTIVE".equals(a.getStatus() != null ? a.getStatus().name() : ""));
                            if (statusCmp != 0) return statusCmp;
                            // Then latest installedAt
                            if (a.getInstalledAt() == null && b.getInstalledAt() == null) return 0;
                            if (a.getInstalledAt() == null) return 1;
                            if (b.getInstalledAt() == null) return -1;
                            return b.getInstalledAt().compareTo(a.getInstalledAt());
                        })
                        .findFirst()
                        .orElse(group.get(0));

                // Merge device names from all installs (distinct, non-null)
                List<String> mergedDeviceNames = group.stream()
                        .map(ext -> ext.getDevice() != null ? ext.getDevice().getDeviceName() : null)
                        .filter(name -> name != null && !name.isBlank())
                        .distinct()
                        .collect(Collectors.toList());

                // Merge high-risk permissions from all installs (distinct)
                List<String> mergedPermissions = group.stream()
                        .filter(ext -> ext.getHighRiskPermissions() != null)
                        .flatMap(ext -> ext.getHighRiskPermissions().stream())
                        .distinct()
                        .collect(Collectors.toList());

                // Use the highest risk level across all installs
                String highestRisk = group.stream()
                        .map(InstalledExtension::getRiskLevel)
                        .filter(r -> r != null)
                        .max(java.util.Comparator.comparingInt(UserActivityService::riskLevelOrdinal))
                        .orElse(representative.getRiskLevel());

                // Use the strictest policy action across all installs
                String strictestPolicy = group.stream()
                        .map(InstalledExtension::getPolicyAction)
                        .filter(p -> p != null)
                        .max(java.util.Comparator.comparingInt(UserActivityService::policyActionOrdinal))
                        .orElse(representative.getPolicyAction());

                // Determine overall status: ACTIVE if any install is active
                String overallStatus = group.stream()
                        .anyMatch(ext -> ext.getStatus() != null && "ACTIVE".equals(ext.getStatus().name()))
                        ? "ACTIVE" : (representative.getStatus() != null ? representative.getStatus().name() : null);

                if ("HIGH".equals(highestRisk) || "CRITICAL".equals(highestRisk)) {
                    dedupHighRisk++;
                }
                if ("BLOCK".equals(strictestPolicy)) {
                    dedupBlocked++;
                }

                extensionInfoList.add(DeviceUserDetailsDTO.ExtensionInfo.builder()
                        .extensionId(representative.getExtensionId())
                        .extensionName(representative.getExtensionName())
                        .version(representative.getVersion())
                        .riskLevel(highestRisk)
                        .policyAction(strictestPolicy)
                        .status(overallStatus)
                        .deviceNames(mergedDeviceNames)
                        .installedAt(representative.getInstalledAt())
                        .highRiskPermissions(mergedPermissions.isEmpty() ? null : mergedPermissions)
                        .build());
            }

            highRiskExtensions = dedupHighRisk;
            builder.totalExtensions(extensionInfoList.size())
                    .highRiskExtensions(dedupHighRisk)
                    .blockedExtensions(dedupBlocked);

            // Cap at 100 in response
            if (extensionInfoList.size() > 100) {
                builder.extensions(extensionInfoList.subList(0, 100));
            } else {
                builder.extensions(extensionInfoList);
            }
        } else {
            builder.totalExtensions(0).highRiskExtensions(0).blockedExtensions(0);
        }

        // Consolidated file operation counts - single query replaces 6 separate queries
        List<Object[]> fileCountRows = eventRepository.getFileOperationCountsForDeviceUser(tenantId, deviceUserId);
        long downloads = 0, uploads = 0, blockedDownloads = 0, blockedUploads = 0, printOps = 0, clipboardOps = 0;
        if (!fileCountRows.isEmpty()) {
            Object[] fc = fileCountRows.get(0);
            downloads = ((Number) fc[0]).longValue();
            uploads = ((Number) fc[1]).longValue();
            blockedDownloads = ((Number) fc[2]).longValue();
            blockedUploads = ((Number) fc[3]).longValue();
            printOps = ((Number) fc[4]).longValue();
            clipboardOps = ((Number) fc[5]).longValue();
        }

        // Recent file operations
        List<Event> recentFileOps = eventRepository.findRecentFileOperationsByDeviceUserId(tenantId, deviceUserId, 10);
        List<DeviceUserDetailsDTO.RecentFileOperation> recentFileOperations = recentFileOps.stream()
                .map(e -> DeviceUserDetailsDTO.RecentFileOperation.builder()
                        .operationType(e.getFileOperationType() != null ? e.getFileOperationType().name() : null)
                        .fileName(e.getFileName())
                        .fileType(e.getFileType())
                        .fileSize(e.getFileSize())
                        .blocked(Boolean.TRUE.equals(e.getIsBlocked()))
                        .deviceName(e.getDevice() != null ? e.getDevice().getDeviceName() : null)
                        .timestamp(e.getTimeStamp())
                        .build())
                .collect(Collectors.toList());

        builder.fileOperations(DeviceUserDetailsDTO.FileOperationsSummary.builder()
                .totalDownloads(downloads)
                .totalUploads(uploads)
                .blockedDownloads(blockedDownloads)
                .blockedUploads(blockedUploads)
                .printOperations(printOps)
                .clipboardOperations(clipboardOps)
                .recentOperations(recentFileOperations)
                .build());

        // Risk assessment
        int riskScore = calculateRiskScore(highRiskExtensions, policyViolations, securityEvents, blockedOperations);
        String riskLevel = getRiskLevel(riskScore);
        List<String> riskFactors = buildRiskFactors(highRiskExtensions, policyViolations, securityEvents, blockedOperations);

        builder.riskAssessment(DeviceUserDetailsDTO.RiskAssessment.builder()
                .riskScore(riskScore)
                .riskLevel(riskLevel)
                .highRiskExtensions((int) highRiskExtensions)
                .policyViolationsCount((int) policyViolations)
                .securityEventsCount((int) securityEvents)
                .blockedOperationsCount((int) blockedOperations)
                .riskFactors(riskFactors)
                .assessedAt(now)
                .build());

        // Browser usage
        Map<String, Long> browserUsage = devices.stream()
                .filter(d -> d.getBrowserType() != null)
                .collect(Collectors.groupingBy(Device::getBrowserType, Collectors.counting()));
        builder.browserUsage(browserUsage);

        // Location info
        List<Object[]> locationStats = eventRepository.getLocationStatsForDeviceUser(tenantId, deviceUserId);
        List<DeviceUserDetailsDTO.LocationInfo> locations = locationStats.stream()
                .map(row -> DeviceUserDetailsDTO.LocationInfo.builder()
                        .ipAddress(row[0] != null ? row[0].toString() : null)
                        .location(row[1] != null ? row[1].toString() : null)
                        .accessCount(((Number) row[2]).longValue())
                        .build())
                .collect(Collectors.toList());
        builder.locations(locations);

        // Recent policy violations
        List<Event> recentViolations = eventRepository.findRecentPolicyViolationsByDeviceUserId(tenantId, deviceUserId, 10);
        List<DeviceUserDetailsDTO.PolicyViolationInfo> policyViolationInfos = recentViolations.stream()
                .map(e -> DeviceUserDetailsDTO.PolicyViolationInfo.builder()
                        .eventId(e.getPkEventId())
                        .policyName(e.getPolicyName())
                        .policyType(e.getPolicyType())
                        .url(e.getUrl())
                        .domain(e.getDomain())
                        .actionTaken(e.getActionTaken())
                        .severity(e.getSeverity())
                        .deviceName(e.getDevice() != null ? e.getDevice().getDeviceName() : null)
                        .timestamp(e.getTimeStamp())
                        .build())
                .collect(Collectors.toList());
        builder.recentPolicyViolations(policyViolationInfos);

        // Recent security events
        List<Event> recentSecEvents = eventRepository.findRecentSecurityEventsByDeviceUserId(tenantId, deviceUserId, 10);
        List<DeviceUserDetailsDTO.SecurityEventInfo> securityEventInfos = recentSecEvents.stream()
                .map(e -> DeviceUserDetailsDTO.SecurityEventInfo.builder()
                        .eventId(e.getPkEventId())
                        .threatType(e.getThreatType())
                        .severity(e.getSeverity())
                        .url(e.getUrl())
                        .domain(e.getDomain())
                        .actionTaken(e.getActionTaken())
                        .deviceName(e.getDevice() != null ? e.getDevice().getDeviceName() : null)
                        .timestamp(e.getTimeStamp())
                        .build())
                .collect(Collectors.toList());
        builder.recentSecurityEvents(securityEventInfos);

        return builder.build();
    }

    /**
     * Ordinal for risk level comparison (higher = riskier).
     */
    private static int riskLevelOrdinal(String riskLevel) {
        if (riskLevel == null) return 0;
        switch (riskLevel) {
            case "NONE": return 0;
            case "LOW": return 1;
            case "MEDIUM": return 2;
            case "HIGH": return 3;
            case "CRITICAL": return 4;
            default: return 0;
        }
    }

    /**
     * Ordinal for policy action comparison (higher = stricter).
     */
    private static int policyActionOrdinal(String policyAction) {
        if (policyAction == null) return 0;
        switch (policyAction) {
            case "ALLOW": return 0;
            case "WARN": return 1;
            case "BLOCK": return 2;
            default: return 0;
        }
    }

    /**
     * Build event description based on event type.
     */
    private String buildEventDescription(Event event) {
        if (event.getEventType() == null) return "Unknown activity";

        switch (event.getEventType()) {
            case WEBSITE_VISIT:
                return "Visited " + (event.getDomain() != null ? event.getDomain() : "website");
            case FILE_OPERATION:
            case FILE_DOWNLOAD:
            case FILE_UPLOAD:
            case FILE_PRINT:
            case FILE_CLIPBOARD_COPY:
            case FILE_CLIPBOARD_PASTE:
                return (event.getFileOperationType() != null ? event.getFileOperationType().name() : event.getEventType().name().replace("_", " ")) +
                       (event.getFileName() != null ? ": " + event.getFileName() : "");
            case POLICY_VIOLATION:
                return "Policy violation" + (event.getPolicyName() != null ? ": " + event.getPolicyName() : "");
            case SECURITY_THREAT:
                return "Security threat" + (event.getThreatType() != null ? ": " + event.getThreatType() : "");
            case TRACKING_ACTIVITY:
                return "Tracking detected" + (event.getDomain() != null ? " on " + event.getDomain() : "");
            case USER_BEHAVIOR:
                return "User behavior" + (event.getCategory() != null ? ": " + event.getCategory() : "")
                       + (event.getActionTaken() != null ? " (" + event.getActionTaken() + ")" : "");
            case SYSTEM_CONFIGURATION:
                return "Configuration change" + (event.getPolicyName() != null ? ": " + event.getPolicyName() : "")
                       + (event.getPolicyType() != null ? " [" + event.getPolicyType() + "]" : "");
            default:
                return event.getEventType().name().replace("_", " ");
        }
    }

    /**
     * Calculate risk score based on various factors.
     */
    private int calculateRiskScore(long highRiskExtensions, long policyViolations,
                                    long securityEvents, long blockedOperations) {
        int score = 0;

        // High risk extensions: +15 each (max 45)
        score += Math.min(highRiskExtensions * 15, 45);

        // Policy violations: +5 each (max 25)
        score += Math.min(policyViolations * 5, 25);

        // Security events: +10 each (max 30)
        score += Math.min(securityEvents * 10, 30);

        // Blocked operations: +2 each (max 10)
        score += Math.min(blockedOperations * 2, 10);

        return Math.min(score, 100);
    }

    /**
     * Get risk level string based on score.
     */
    private String getRiskLevel(int score) {
        if (score >= 75) return "Critical";
        if (score >= 50) return "High";
        if (score >= 25) return "Medium";
        return "Low";
    }

    /**
     * Build list of risk factors.
     */
    private List<String> buildRiskFactors(long highRiskExtensions, long policyViolations,
                                           long securityEvents, long blockedOperations) {
        List<String> factors = new java.util.ArrayList<>();

        if (highRiskExtensions > 0) {
            factors.add(highRiskExtensions + " high-risk extension(s) installed");
        }
        if (policyViolations > 5) {
            factors.add("Multiple policy violations (" + policyViolations + ")");
        } else if (policyViolations > 0) {
            factors.add(policyViolations + " policy violation(s)");
        }
        if (securityEvents > 0) {
            factors.add(securityEvents + " security event(s) detected");
        }
        if (blockedOperations > 10) {
            factors.add("High number of blocked operations (" + blockedOperations + ")");
        } else if (blockedOperations > 0) {
            factors.add(blockedOperations + " blocked operation(s)");
        }

        if (factors.isEmpty()) {
            factors.add("No significant risk factors detected");
        }

        return factors;
    }
}
