package com.secufusion.events.dto;

import com.secufusion.events.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for user list view (lightweight, without full device details).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserListDto {

    private String userId;
    private String userName;
    private String email;
    private String firstName;
    private String lastName;
    private String status;
    private LocalDateTime createdAt;

    // Summary counts (populated by service)
    private Integer deviceCount;
    private Integer extensionCount;
    private Integer highRiskExtensionCount;
    private LocalDateTime lastActivityAt;
    private Long eventCount;
    private Long securityEventCount;

    /**
     * Create from User entity
     */
    public static UserListDto fromEntity(User user) {
        if (user == null) {
            return null;
        }
        return UserListDto.builder()
                .userId(user.getPkUserId())
                .userName(user.getUserName())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .status(user.getStatus())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
