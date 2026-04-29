package com.secufusion.tenant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class NotificationPreferenceDTO {

    private String preferenceId;
    private String category;
    private boolean emailEnabled;
    private boolean websocketEnabled;
    private boolean inAppEnabled;
    private String minSeverity;
}
