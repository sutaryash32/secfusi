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
public class BulkStatusRequest {

    @NotEmpty(message = "Incident IDs required")
    private List<String> incidentIds;

    @NotBlank(message = "New status required")
    private String newStatus;
}
