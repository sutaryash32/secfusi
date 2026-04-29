package com.secufusion.events.dto;

import com.secufusion.events.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for user with their devices and activity summary.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserWithDevicesDto {

    // User info
    private String userId;
    private String userName;
    private String email;
    private String firstName;
    private String lastName;
    private String phoneNo;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime lastActivityAt;

    // Device summary
    private Integer totalDevices;
    private Integer activeDevices;
    private List<DeviceResponse> devices;

    // Extension summary
    private Integer totalExtensions;
    private Integer highRiskExtensions;
    private Integer blockedExtensions;

    // Activity summary
    private Long totalEvents;
    private Long securityEvents;
    private Long policyViolations;

    /**
     * Create from User entity (without devices - use setDevices separately)
     */
    public static UserWithDevicesDto fromEntity(User user) {
        if (user == null) {
            return null;
        }
        return UserWithDevicesDto.builder()
                .userId(user.getPkUserId())
                .userName(user.getUserName())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phoneNo(user.getPhoneNo())
                .status(user.getStatus())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
