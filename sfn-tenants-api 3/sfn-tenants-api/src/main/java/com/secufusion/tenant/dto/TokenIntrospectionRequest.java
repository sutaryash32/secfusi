package com.secufusion.tenant.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenIntrospectionRequest {

    @NotBlank(message = "Token is required")
    private String token;

    private String tokenTypeHint;
}
