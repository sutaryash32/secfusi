package com.secufusion.events.dto.apikey;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for generating a JWT token using API key
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GenerateTokenRequest {

    @NotBlank(message = "API key is required")
    private String apiKey;

    @NotNull(message = "Device user details are required")
    @Valid
    private DeviceUserDetails deviceUserDetails;

    /**
     * Nested DTO for device user information
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DeviceUserDetails {

        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        private String email;

        private String userName;
        private String displayName;
        private String deviceId;
        private String deviceName;
        private String deviceOs;
        private String deviceHardwareId;
        private String portalUserId;

        @Builder.Default
        private String source = "EXTENSION";
    }
}
