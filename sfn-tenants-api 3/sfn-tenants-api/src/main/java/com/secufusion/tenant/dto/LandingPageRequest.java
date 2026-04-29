package com.secufusion.tenant.dto;

import lombok.Data;

import java.util.List;

@Data
public class LandingPageRequest {

    private String name;
    private String description;
    private List<ShortcutRequest> shortcuts;
}
