package com.secufusion.tenant.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

@Data
public class ExtensionPolicyRequestDto {

    private String name;
    private String description;
    private String landingPageUrl;

//    // Managed Extension
//    private String enforcementAction; // ALLOW / BLOCK
//    private String warningMessage;
//
//    private List<ExtensionDetailDto> extensions;
    @JsonAlias("managedExtensions")
    private ManagedExtensionDto managedExtension;
}
