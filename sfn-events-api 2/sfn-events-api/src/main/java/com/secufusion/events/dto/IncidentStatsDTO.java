package com.secufusion.events.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentStatsDTO {

    private long totalIncidents;
    private long openCount;
    private long investigatingCount;
    private long resolvedCount;
    private long closedCount;
    private long falsePositiveCount;
    private Map<String, Long> byPriority;
    private Map<String, Long> byCategory;
    private Double meanTimeToResolveHours;
}
