package com.secufusion.tenant.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "TenantTypes")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TenantType {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String pkTenantTypeId;

    private String tenantTypeName;

}
