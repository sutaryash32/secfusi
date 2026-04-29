package com.secufusion.iam.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "auth_provider_config")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthProviderConfig {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(
            name = "UUID",
            strategy = "org.hibernate.id.UUIDGenerator"
    )
    @Column(name = "auth_id", updatable = false, nullable = false)
    private UUID authId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_tenant_id", referencedColumnName = "tenantid", nullable = false)
    @JsonBackReference
    private Tenant tenant;

    @Column(name = "sso_type", nullable = false)
    private String ssoType ;   // KEYCLOAK / OKTA / AUTH0 / CUSTOM etc.

    @Column(name = "issuer_uri")
    private String issuerUri;

    @Column(name = "auth_server_url")
    private String authServerUrl;

    @Column(name = "token_endpoint")
    private String tokenEndpoint;

    @Column(name = "jwk_uri")
    private String jwkUri;

    @Column(name = "client_id")
    private String clientId;

    @Column(name = "client_secret")
    private String clientSecret;

    @Column(name = "redirect_uri")
    private String redirectUri;

    @Column(name = "login_url")
    private String loginUrl;

    @Column(name = "scopes")
    private String scopes;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
