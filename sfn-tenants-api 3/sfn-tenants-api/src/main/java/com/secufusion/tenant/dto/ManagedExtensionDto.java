package com.secufusion.tenant.dto;

import lombok.Data;

import java.util.List;

@Data
public class ManagedExtensionDto {

    @com.fasterxml.jackson.annotation.JsonProperty("extensionPolicyType")
    @com.fasterxml.jackson.annotation.JsonAlias("extensionPolicyType")
    private String action;
    private String enforcementAction;
    private String warningMessage;

    private List<ExtensionDetailDto> extensions;
}

