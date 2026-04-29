package com.secufusion.tenant.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "billing_cycle")
public class BillingCycle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_billing_cycle_id")
    private Long pkBillingCycleId;

    @Column(name = "cycle_name", nullable = false, unique = true)
    private String cycleName;

    @Column(name = "cycle_code", nullable = false, unique = true)
    private String cycleCode;

    @Column(name = "duration_months")
    private Integer durationMonths;

    @Column(name = "description")
    private String description;

    @Column(name = "is_active")
    private Boolean isActive = true;
}
