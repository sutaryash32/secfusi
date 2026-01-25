package com.secufusion.iam.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "sso_provider_url_config")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SsoProviderUrlConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    @Column(name = "provider_id", nullable = false, unique = true)
    private String providerId;  // azure, okta, google, auth0, etc.

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "authorization_url", nullable = false)
    private String authorizationUrl;

    @Column(name = "token_url", nullable = false)
    private String tokenUrl;

    @Column(name = "logout_url")
    private String logoutUrl;

    @Column(name = "user_info_url")
    private String userInfoUrl;

    @Column(name = "jwks_url")
    private String jwksUrl;

    @Column(name = "issuer")
    private String issuer;

    @Column(name = "default_scopes")
    private String defaultScopes;

    @Column(name = "enabled")
    private Boolean enabled = true;
}
