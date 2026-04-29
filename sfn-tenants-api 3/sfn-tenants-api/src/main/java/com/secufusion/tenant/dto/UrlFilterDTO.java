package com.secufusion.tenant.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UrlFilterDTO {

    @NotBlank
    private String filterType;

    @NotBlank
    private String patternType;

    @NotBlank
    private String pattern;

    private String description;
}
