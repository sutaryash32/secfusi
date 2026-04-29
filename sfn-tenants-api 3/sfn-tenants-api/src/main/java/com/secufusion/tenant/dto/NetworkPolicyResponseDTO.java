package com.secufusion.tenant.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class NetworkPolicyResponseDTO {

    private String networkPolicyId;
    private String name;
    private String description;
    private boolean enabled;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private boolean globalDefault;
    private boolean tenantDefault;

    private List<UrlFilterDTO> urlFilters;
    private NetworkConfigurationDTO networkConfiguration;
}
