package com.secufusion.tenant.dto;

import lombok.Data;

@Data
public class ExtensionAuthResponse {

    private String accessToken;
    private String tokenType;
    private Long expiresIn;
}
