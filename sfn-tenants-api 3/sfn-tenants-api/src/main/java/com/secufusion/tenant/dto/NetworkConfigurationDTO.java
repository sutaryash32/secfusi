package com.secufusion.tenant.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;

@Data
public class NetworkConfigurationDTO {

    private String proxyMode;
    private String pacUrl;
    private String proxyServers;
    private String bypassList;
    private Boolean useSecufusionIdpProxy;
    private String customIdpHostnames;
    private String identityProvider;   // okta | azure_ad | ping | custom
    private JsonNode hostnames;         // JSONB

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Set hostnames from a JSON string.
     * Parses the string into a JsonNode for structured access.
     *
     * @param hostnamesJson a valid JSON string (or null)
     * @throws IllegalArgumentException if the string is not valid JSON
     */
    public void setHostnames(String hostnamesJson) {
        if (hostnamesJson == null || hostnamesJson.isBlank()) {
            this.hostnames = null;
        } else {
            try {
                this.hostnames = MAPPER.readTree(hostnamesJson);
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid JSON for hostnames: " + hostnamesJson, e);
            }
        }
    }

    /**
     * Convenience getter – returns the parsed JsonNode.
     * To get the raw JSON string, use hostnames.toString().
     */
    public JsonNode getHostnames() {
        return hostnames;
    }
}