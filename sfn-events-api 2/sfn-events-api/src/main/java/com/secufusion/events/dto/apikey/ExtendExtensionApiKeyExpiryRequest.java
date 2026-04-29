package com.secufusion.events.dto.apikey;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Request DTO for extending API key expiry
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExtendExtensionApiKeyExpiryRequest {

    @NotNull(message = "New expiry date is required")
    @Future(message = "New expiry date must be in the future")
    private LocalDateTime newExpiryDate;
}
