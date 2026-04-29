package com.secufusion.tenant.dto;

import lombok.Data;

import java.util.List;

@Data
public class LandingPageResponse {

    private String name;
    private String description;
    private List<ShortcutResponse> shortcuts;
}
