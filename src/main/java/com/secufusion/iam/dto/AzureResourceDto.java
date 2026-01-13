package com.secufusion.iam.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AzureResourceDto {
    private String id;          // UUID (for Groups) or ID (for Roles)
    private String name;        // Display Name (e.g., "Manager")
    private String value;       // Value for mapping (e.g., "Manager" or "57660...")
    private String type;        // "ROLE" or "GROUP"
}