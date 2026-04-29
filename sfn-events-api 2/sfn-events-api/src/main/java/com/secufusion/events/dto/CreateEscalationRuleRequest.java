package com.secufusion.events.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateEscalationRuleRequest {

    @NotBlank(message = "Name is required")
    private String name;

    private String description;

    private String triggerPriority;

    private Integer unassignedMinutes;

    private Integer unresolvedHours;

    private String escalateToPriority;

    private String notifyRole;
}
