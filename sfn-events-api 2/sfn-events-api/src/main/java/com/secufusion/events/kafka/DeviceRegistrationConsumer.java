package com.secufusion.events.kafka;

import com.secufusion.events.dto.DeviceRegistrationEvent;
import com.secufusion.events.entity.Device;
import com.secufusion.events.entity.DeviceStatus;
import com.secufusion.events.repository.DeviceRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * Kafka consumer for device registration events from IAM API.
 * Syncs device data from IAM logins to Events API devices table.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceRegistrationConsumer {

    public static final String DEVICE_REGISTRATION_TOPIC = "device-registration";
    public static final String DEVICE_CONSUMER_GROUP = "device-registration-consumer-group";

    private final DeviceRepository deviceRepository;

    @PostConstruct
    public void init() {
        log.info("DeviceRegistrationConsumer initialized - listening to topic: {}", DEVICE_REGISTRATION_TOPIC);
    }

    @KafkaListener(
            topics = DEVICE_REGISTRATION_TOPIC,
            groupId = DEVICE_CONSUMER_GROUP,
            containerFactory = "deviceRegistrationKafkaListenerContainerFactory"
    )
    @Transactional
    public void consume(List<DeviceRegistrationEvent> events, Acknowledgment ack) {
        log.info("[KAFKA] Received device registration batch size={}", events.size());

        try {
            int processed = 0;
            int skipped = 0;

            for (DeviceRegistrationEvent event : events) {
                if (event == null || event.getDeviceFingerprint() == null || event.getTenantId() == null) {
                    log.warn("Skipping invalid device registration event: {}", event);
                    skipped++;
                    continue;
                }

                processDeviceRegistration(event);
                processed++;
            }

            ack.acknowledge();
            log.info("[KAFKA] Device registration batch completed: processed={} skipped={}", processed, skipped);

        } catch (Exception ex) {
            log.error("[KAFKA] Failed to process device registration batch", ex);
            throw ex; // Kafka will retry
        }
    }

    private void processDeviceRegistration(DeviceRegistrationEvent event) {
        String fingerprint = event.getDeviceFingerprint();
        String tenantId = event.getTenantId();

        // Check if device already exists by fingerprint + tenant
        Optional<Device> existingDevice = deviceRepository.findByDeviceFingerprintAndTenantId(fingerprint, tenantId);

        Device device;
        if (existingDevice.isPresent()) {
            // Update existing device
            device = existingDevice.get();
            device.setLastSeenAt(LocalDateTime.now());
            device.setUserName(event.getUserName());

            if (event.getUserAgent() != null) {
                device.setUserAgent(event.getUserAgent());
                device.setDeviceType(Device.parseDeviceType(event.getUserAgent()));
                device.setBrowserType(Device.parseBrowserType(event.getUserAgent()));
            }
            if (event.getBrowserType() != null) {
                device.setBrowserType(event.getBrowserType());
            }
            if (event.getOsInfo() != null) {
                device.setOsInfo(event.getOsInfo());
            }
            if (event.getIpAddress() != null) {
                device.setIpAddress(event.getIpAddress());
            }
            if (event.getLocation() != null) {
                device.setLocation(event.getLocation());
            }
            if (event.getDeviceName() != null && !event.getDeviceName().isBlank()) {
                device.setDeviceName(Device.sanitizeDeviceName(
                        event.getDeviceName(), device.getBrowserType(),
                        device.getOsInfo(), device.getDeviceType(), device.getUserName()));
            }
            if (event.getExtensionVersion() != null) {
                device.setExtensionVersion(event.getExtensionVersion());
            }

            // Reactivate if inactive
            if (device.getStatus() == DeviceStatus.INACTIVE) {
                device.setStatus(DeviceStatus.ACTIVE);
            }

            log.debug("Updated existing device: deviceId={} fingerprint={} tenant={}",
                    device.getDeviceId(), fingerprint, tenantId);
        } else {
            // Create new device
            LocalDateTime loginTime = event.getLoginTimestamp() > 0
                    ? LocalDateTime.ofInstant(Instant.ofEpochMilli(event.getLoginTimestamp()), ZoneId.systemDefault())
                    : LocalDateTime.now();

            String browserType = event.getBrowserType() != null ? event.getBrowserType()
                    : Device.parseBrowserType(event.getUserAgent());
            String deviceType = Device.parseDeviceType(event.getUserAgent());
            device = Device.builder()
                    .tenantId(tenantId)
                    .userName(event.getUserName())
                    .deviceFingerprint(fingerprint)
                    .deviceName(Device.sanitizeDeviceName(
                            event.getDeviceName(), browserType,
                            event.getOsInfo(), deviceType, event.getUserName()))
                    .browserType(browserType)
                    .deviceType(deviceType)
                    .osInfo(event.getOsInfo())
                    .userAgent(event.getUserAgent())
                    .ipAddress(event.getIpAddress())
                    .location(event.getLocation())
                    .extensionVersion(event.getExtensionVersion())
                    .status(DeviceStatus.ACTIVE)
                    .firstSeenAt(loginTime)
                    .lastSeenAt(loginTime)
                    .build();

            log.debug("Created new device from IAM: fingerprint={} tenant={} user={}",
                    fingerprint, tenantId, event.getUserName());
        }

        deviceRepository.save(device);
    }
}
