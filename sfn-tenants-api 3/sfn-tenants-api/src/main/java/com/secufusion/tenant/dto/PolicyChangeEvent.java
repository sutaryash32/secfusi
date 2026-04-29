package com.secufusion.tenant.dto;

import com.secufusion.tenant.entity.BrowserPolicy;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PolicyChangeEvent <T> {

    private String tenantId;
    private String policyId;
    private String eventType;     // CREATED | UPDATED | DELETED
    private T policy; // FULL ENTITY
    private Instant timestamp;
}
