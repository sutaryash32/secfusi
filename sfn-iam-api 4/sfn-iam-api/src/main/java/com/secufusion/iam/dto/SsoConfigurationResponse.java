package com.secufusion.iam.dto;

import com.secufusion.iam.entity.SsoConfiguration;
import lombok.Data;

@Data
public class SsoConfigurationResponse {

    private String id;
    private String alias;
    private String providerId;
    private String tenantId;
    private Boolean enabled;
    private Boolean trustEmail;
    private Boolean storeToken;
    private Boolean linkOnly;
    private String displayName;
    private String clientId;
    private String authorizationUrl;
    private String tokenUrl;
    private String userInfoUrl;
    private String issuer;
    private String redirectUri;
    private Boolean setAsDefaultLogin;
    private String fkTenantId;
    private String active;

    public static SsoConfigurationResponse from(SsoConfiguration e) {
        SsoConfigurationResponse r = new SsoConfigurationResponse();
        r.setId(e.getId());
        r.setAlias(e.getAlias());
        r.setProviderId(e.getProviderId());
        r.setTenantId(e.getTenantId());
        r.setEnabled(e.getEnabled());
        r.setTrustEmail(e.getTrustEmail());
        r.setStoreToken(e.getStoreToken());
        r.setLinkOnly(e.getLinkOnly());
        r.setDisplayName(e.getDisplayName());
        r.setClientId(e.getClientId());
        r.setAuthorizationUrl(e.getAuthorizationUrl());
        r.setTokenUrl(e.getTokenUrl());
        r.setUserInfoUrl(e.getUserInfoUrl());
        r.setIssuer(e.getIssuer());
        r.setRedirectUri(e.getRedirectUri());
        r.setSetAsDefaultLogin(e.getSetAsDefaultLogin());
        r.setFkTenantId(e.getFkTenantId());
        r.setActive(e.getActive());
        return r;
    }
}
