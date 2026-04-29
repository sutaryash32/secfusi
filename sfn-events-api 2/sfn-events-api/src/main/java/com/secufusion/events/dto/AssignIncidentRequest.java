package com.secufusion.events.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssignIncidentRequest {

    @NotBlank(message = "Assigned user ID is required")
    private String assignedTo;

    @NotBlank(message = "Assigned user name is required")
    private String assignedToName;
}
