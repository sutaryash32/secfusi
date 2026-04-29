package com.secufusion.tenant.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "dlp")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Dlp {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "pk_dlp_id", length = 36)
    private String pkDlpId;

    @Column(name = "disable_copy")
    private boolean disableCopy = false;

    @Column(name = "disable_paste")
    private boolean disablePaste = false;

    @Column(name = "disable_download")
    private boolean disableDownload = false;

    @Column(name = "block_printing")
    private boolean blockPrinting = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode clipboard;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pii_detection", columnDefinition = "jsonb")
    private JsonNode piiDetection;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "file_operations", columnDefinition = "jsonb")
    private JsonNode fileOperations;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "form_controls", columnDefinition = "jsonb")
    private JsonNode formControls;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "communication_platforms", columnDefinition = "jsonb")
    private JsonNode communicationPlatforms;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "security_policies", columnDefinition = "jsonb")
    private JsonNode securityPolicies;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "behavior_monitoring", columnDefinition = "jsonb")
    private JsonNode behaviorMonitoring;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "policy_enforcement", columnDefinition = "jsonb")
    private JsonNode policyEnforcement;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "fk_watermarking_id")
    private Watermarking watermarking;

}
