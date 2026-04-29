package com.secufusion.tenant.dto;

import com.secufusion.tenant.entity.BrowserPolicy;
import com.secufusion.tenant.entity.ExtensionPolicy;
import com.secufusion.tenant.entity.LandingPage;
import com.secufusion.tenant.entity.NetworkPolicy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserEffectivePolicyResponse {

    private BrowserPolicy browserPolicy;
    private NetworkPolicy networkPolicy;
    private ExtensionPolicy extensionPolicy;

    /**
     * Resolved LandingPage entity (with shortcuts) when browserPolicy.landingPageId is set.
     * Null when the browser policy uses a direct landingPageUrl instead, or has no landing page.
     */
    private LandingPage landingPage;
}