package com.secufusion.iam.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "industry")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Industry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "industryid")
    private Long industryId;

    @Column(name = "industryname", nullable = false, length = 150)
    private String industryName;

    @Column(name = "industrycode", nullable = false, length = 50, unique = true)
    private String industryCode;

    @ManyToOne
    @JoinColumn(name = "industryparentid")
    private Industry parentIndustry;

    @Column(name = "isactive", nullable = false)
    private Boolean isActive;

    @Column(name = "createdby")
    private UUID createdBy;

    @Column(name = "createdtimestamp", nullable = false)
    private LocalDateTime createdTimestamp;

    @Column(name = "lastmodifiedby")
    private UUID lastModifiedBy;

    @Column(name = "lastmodifiedtimestamp", nullable = false)
    private LocalDateTime lastModifiedTimestamp;
}
