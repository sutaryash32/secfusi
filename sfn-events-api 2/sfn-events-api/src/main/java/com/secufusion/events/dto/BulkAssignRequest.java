package com.secufusion.events.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkAssignRequest {

    @NotEmpty(message = "Incident IDs required")
    private List<String> incidentIds;

    @NotBlank(message = "Assigned to user ID required")
    private String assignedTo;

    @NotBlank(message = "Assigned to user name required")
    private String assignedToName;
}
