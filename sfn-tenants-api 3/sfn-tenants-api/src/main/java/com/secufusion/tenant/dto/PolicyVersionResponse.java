package com.secufusion.tenant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicyVersionResponse {

    private PolicyVersionInfo browserPolicy;
    private PolicyVersionInfo networkPolicy;
    private PolicyVersionInfo extensionPolicy;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PolicyVersionInfo {
        private String policyId;
        private String policyKey;
        private String version;
        private boolean globalDefault;
    }
}
