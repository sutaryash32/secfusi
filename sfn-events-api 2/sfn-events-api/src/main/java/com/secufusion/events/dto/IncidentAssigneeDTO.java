package com.secufusion.events.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentAssigneeDTO {

    private String assigneeConfigId;
    private String category;          // null displayed as "ALL" on frontend
    private String userId;
    private String userName;
    private Boolean isActive;
    private Integer assignmentOrder;
    private Long activeIncidentCount; // current OPEN+INVESTIGATING load
    private String createdAt;
    private String updatedAt;
}
