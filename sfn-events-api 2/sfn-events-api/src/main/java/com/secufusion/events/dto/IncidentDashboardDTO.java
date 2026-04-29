package com.secufusion.events.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentDashboardDTO {

    private IncidentStatsDTO stats;
    private List<IncidentDTO> recentIncidents;
    private LocalDateTime generatedAt;
}
