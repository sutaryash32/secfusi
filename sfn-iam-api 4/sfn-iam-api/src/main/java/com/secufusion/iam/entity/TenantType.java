package com.secufusion.iam.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "tenant_types")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TenantType {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_tenant_type_id")
    private String pkTenantTypeId;

    @Column(name = "tenant_type_name")
    private String tenantTypeName;

}
