package com.secufusion.tenant.dto;

import lombok.Data;

@Data
public class AzureAssignmentDto {
    private String id;   // The Azure ID/Value
    private String name; // The Azure Display Name
    private String type; // "ROLE" or "GROUP"
    private String policyId; // Local BrowserPolicy ID
}