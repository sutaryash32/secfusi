package com.secufusion.iam.dto;

import lombok.Data;

@Data
public class CreateIdentityProviderRequest {

    private String alias;           // microsoft, google, saml-idp
    private String providerId;      // oidc, saml, github, google, etc.
    private String tenantId;

    private Boolean enabled = true;
    private Boolean trustEmail = true;
    private Boolean storeToken = false;
    private Boolean linkOnly = false;
    private String displayName;

    // Standard OIDC fields
    private String clientId;
    private String clientSecret;
    private String authorizationUrl;
    private String tokenUrl;
    private String userInfoUrl;
    private String issuer;

    // Optional redirects
    private String redirectUri;
    private Boolean setAsDefaultLogin = false;
}
