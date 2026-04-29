package com.secufusion.events.dto;

import com.secufusion.events.entity.DeviceStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for updating device status.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceStatusUpdateRequest {

    /**
     * New status for the device.
     */
    @NotNull(message = "Status is required")
    private DeviceStatus status;

    /**
     * Optional reason for the status change.
     */
    private String reason;
}
