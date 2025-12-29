package com.secufusion.iam.dto;

import com.secufusion.iam.entity.SsoConfiguration;
import lombok.Data;

@Data
public class SsoConfigurationResponse {

    private String id;
    private String alias;
    private String providerId;
    private String active;
    private boolean defaultLogin;

    public static SsoConfigurationResponse from(SsoConfiguration e) {
        SsoConfigurationResponse r = new SsoConfigurationResponse();
        r.setId(e.getId());
        r.setAlias(e.getAlias());
        r.setProviderId(e.getProviderId());
        r.setActive(e.getActive());
        r.setDefaultLogin(e.getSetAsDefaultLogin());
        return r;
    }
}
