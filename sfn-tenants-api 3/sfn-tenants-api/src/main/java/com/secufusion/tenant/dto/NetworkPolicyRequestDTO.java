package com.secufusion.tenant.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class NetworkPolicyRequestDTO {

    @NotBlank
    private String name;

    private String description;

    @NotNull
    private Boolean enabled;

    @Valid
    private List<UrlFilterDTO> urlFilters;

    @Valid
    private NetworkConfigurationDTO networkConfiguration;
}
