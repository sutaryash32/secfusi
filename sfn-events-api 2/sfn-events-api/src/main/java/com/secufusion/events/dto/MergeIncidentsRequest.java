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
public class MergeIncidentsRequest {

    @NotEmpty(message = "At least one child incident ID required")
    private List<String> childIncidentIds;

    private String mergeNotes;
}
