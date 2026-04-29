package com.secufusion.tenant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UnreadCountDTO {

    private long totalUnread;
    private Map<String, Long> byType;
    private Map<String, Long> bySeverity;
}
