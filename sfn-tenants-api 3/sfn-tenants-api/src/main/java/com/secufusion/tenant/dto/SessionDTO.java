package com.secufusion.tenant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionDTO {

    private String sessionId;

    private String userId;

    private String username;

    private String ipAddress;

    private LocalDateTime startTime;

    private LocalDateTime lastAccess;

    private boolean rememberMe;

    private Map<String, String> clients;

    // Enhanced fields
    private String userAgent;

    private String deviceType;

    private String location;

    private String country;

    private String city;

    private Long sessionDurationSeconds;

    private String sessionDurationFormatted;

    private boolean isCurrentSession;

    public static SessionDTO fromKeycloakSession(org.keycloak.representations.idm.UserSessionRepresentation session) {
        LocalDateTime startTime = session.getStart() > 0
                ? LocalDateTime.ofInstant(Instant.ofEpochMilli(session.getStart()), ZoneId.systemDefault())
                : null;
        LocalDateTime lastAccess = session.getLastAccess() > 0
                ? LocalDateTime.ofInstant(Instant.ofEpochMilli(session.getLastAccess()), ZoneId.systemDefault())
                : null;

        Long durationSeconds = null;
        String durationFormatted = null;
        if (startTime != null) {
            Duration duration = Duration.between(startTime, LocalDateTime.now());
            durationSeconds = duration.getSeconds();
            durationFormatted = formatDuration(duration);
        }

        return SessionDTO.builder()
                .sessionId(session.getId())
                .userId(session.getUserId())
                .username(session.getUsername())
                .ipAddress(session.getIpAddress())
                .startTime(startTime)
                .lastAccess(lastAccess)
                .rememberMe(session.isRememberMe())
                .clients(session.getClients())
                .sessionDurationSeconds(durationSeconds)
                .sessionDurationFormatted(durationFormatted)
                .build();
    }

    public static SessionDTO fromKeycloakSession(org.keycloak.representations.idm.UserSessionRepresentation session,
                                                  String currentSessionId) {
        SessionDTO dto = fromKeycloakSession(session);
        dto.setCurrentSession(session.getId() != null && session.getId().equals(currentSessionId));
        return dto;
    }

    public static SessionDTO fromKeycloakSessionWithDetails(
            org.keycloak.representations.idm.UserSessionRepresentation session,
            String userAgent,
            String location,
            String country,
            String city) {
        SessionDTO dto = fromKeycloakSession(session);
        dto.setUserAgent(userAgent);
        dto.setLocation(location);
        dto.setCountry(country);
        dto.setCity(city);
        dto.setDeviceType(parseDeviceType(userAgent));
        return dto;
    }

    private static String formatDuration(Duration duration) {
        long days = duration.toDays();
        long hours = duration.toHours() % 24;
        long minutes = duration.toMinutes() % 60;

        if (days > 0) {
            return String.format("%dd %dh %dm", days, hours, minutes);
        } else if (hours > 0) {
            return String.format("%dh %dm", hours, minutes);
        } else {
            return String.format("%dm", minutes);
        }
    }

    private static String parseDeviceType(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "Unknown";
        }
        String ua = userAgent.toLowerCase();
        if (ua.contains("mobile") || ua.contains("android") || ua.contains("iphone")) {
            return "Mobile";
        } else if (ua.contains("tablet") || ua.contains("ipad")) {
            return "Tablet";
        } else if (ua.contains("windows") || ua.contains("macintosh") || ua.contains("linux")) {
            return "Desktop";
        }
        return "Unknown";
    }
}
