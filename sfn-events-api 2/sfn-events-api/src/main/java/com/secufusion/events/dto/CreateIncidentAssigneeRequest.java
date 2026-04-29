package com.secufusion.events.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateIncidentAssigneeRequest {

    // null means "ALL categories" (fallback)
    private String category;

    @NotBlank(message = "User ID is required")
    private String userId;

    @NotBlank(message = "User name is required")
    private String userName;

    private Boolean isActive;        // defaults to true if null
    private Integer assignmentOrder; // defaults to 0 if null
}
