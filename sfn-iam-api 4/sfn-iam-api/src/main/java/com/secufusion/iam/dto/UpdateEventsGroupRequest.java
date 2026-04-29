package com.secufusion.iam.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for updating an events group
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateEventsGroupRequest {

    @Size(min = 3, max = 100, message = "Group name must be 3-100 characters")
    private String name;

    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;
}
