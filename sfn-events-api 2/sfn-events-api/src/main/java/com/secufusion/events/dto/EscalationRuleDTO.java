package com.secufusion.events.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EscalationRuleDTO {

    private String ruleId;
    private String name;
    private String description;
    private String triggerPriority;
    private Integer unassignedMinutes;
    private Integer unresolvedHours;
    private String escalateToPriority;
    private String notifyRole;
    private Boolean isActive;
    private String createdAt;
    private String updatedAt;
}
