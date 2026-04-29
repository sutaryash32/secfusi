package com.secufusion.events.dto;

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
public class BulkCloseRequest {

    @NotEmpty(message = "Incident IDs required")
    private List<String> incidentIds;

    private String resolutionNotes;
}
