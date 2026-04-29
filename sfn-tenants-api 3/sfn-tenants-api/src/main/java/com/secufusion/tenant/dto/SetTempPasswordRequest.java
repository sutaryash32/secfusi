package com.secufusion.tenant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for MSSP admin setting a temporary password on a sub-tenant's admin user.
 * Used when the Enterprise tenant does not have email configured (e.g. no M365 subscription)
 * and the MSSP needs to hand-deliver the first login credentials out-of-band.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SetTempPasswordRequest {

    @NotBlank(message = "temporaryPassword is required")
    @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
    private String temporaryPassword;
}
