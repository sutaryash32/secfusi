package com.secufusion.tenant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "extension-policy.defaults")
public class ExtensionPolicyDefaults {

    private String enforcementAction = "WARN_USER";
    private String warningMessage = "A prohibited extension has been detected. Please uninstall it to comply with company policy.";

}
