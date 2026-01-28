package com.secufusion.iam.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

@Entity
@Table(name = "sso_configurations")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SsoConfiguration {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    private String alias;               // microsoft, google, saml-idp

    private String providerId;          // oidc, saml, github, google, etc.

    @Column(nullable = false)
    private String tenantId;

    private Boolean enabled = true;
    private Boolean trustEmail = true;
    private Boolean storeToken = false;
    private Boolean linkOnly = false;

    private String displayName;

    // --- Standard OIDC fields ---
    private String clientId;
    private String clientSecret;

    private String authorizationUrl;
    private String tokenUrl;
    private String userInfoUrl;
    private String issuer;
    private String logoutUrl;
    private String jwksUrl;
    private String scopes;

    // --- Optional redirects ---
    private String redirectUri;
    private Boolean setAsDefaultLogin = false;
    private String fkTenantId;
    private String active;
}
