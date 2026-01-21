package com.secufusion.iam.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "retention_period")
public class RetentionPeriod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_retention_period_id")
    private Long pkRetentionPeriodId;

    @Column(name = "period_name", nullable = false, unique = true, length = 50)
    private String periodName;

    @Column(name = "period_code", nullable = false, unique = true, length = 30)
    private String periodCode;

    @Column(name = "period_days")
    private Integer periodDays;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "is_active")
    private Boolean isActive = true;
}
