package com.secufusion.events.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaybookTemplateDTO {

    private String templateId;
    private String name;
    private String description;
    private String category;
    private List<Map<String, Object>> steps;
    private Boolean isActive;
    private String createdAt;
    private String updatedAt;
}
