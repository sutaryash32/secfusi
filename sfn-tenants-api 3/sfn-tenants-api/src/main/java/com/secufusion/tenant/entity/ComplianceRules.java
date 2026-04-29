package com.secufusion.tenant.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "compliancerules")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceRules {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "pk_compliancerules_id", length = 36)
    private String pkComplianceRulesId;

    @Column(name = "antivirus_check")
    private boolean antivirusCheck = false;

    @Column(name = "disk_encryption_check")
    private boolean diskEncryptionCheck = false;

    @Column(name = "firewall_check")
    private boolean firewallCheck = false;

    @Column(nullable = false)
    private boolean geolocation = false;

}
