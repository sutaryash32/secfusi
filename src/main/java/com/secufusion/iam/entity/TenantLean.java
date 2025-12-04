package com.secufusion.iam.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Subselect;

@Data
@Entity
@Immutable
@AllArgsConstructor
@Subselect("SELECT t.tenantid as pkTenantId, t.tenant_name FROM tenant t")
public class TenantLean {

    @Id
    private String  pkTenantId;
    private String tenantName;
}
