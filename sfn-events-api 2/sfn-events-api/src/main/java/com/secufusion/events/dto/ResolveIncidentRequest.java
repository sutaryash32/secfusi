package com.secufusion.events.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResolveIncidentRequest {

    private String resolutionNotes;

    private String rootCause;
}
