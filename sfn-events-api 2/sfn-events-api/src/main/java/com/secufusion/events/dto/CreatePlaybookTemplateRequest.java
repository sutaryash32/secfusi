package com.secufusion.events.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
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
public class CreatePlaybookTemplateRequest {

    @NotBlank(message = "Name is required")
    private String name;

    private String description;

    private String category;

    @NotEmpty(message = "At least one step is required")
    private List<Map<String, Object>> steps;
}
