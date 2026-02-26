package com.secufusion.iam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating an events group
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateEventsGroupRequest {

    @NotBlank(message = "Group name is required")
    @Size(min = 3, max = 100, message = "Group name must be 3-100 characters")
    private String name;

    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;
}
