package com.secufusion.iam.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTenantPackageRequest {

    @NotNull(message = "Package ID is required")
    private Long packageId;
}
