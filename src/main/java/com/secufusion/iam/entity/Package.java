package com.secufusion.iam.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "package")
public class Package {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long pkPackageId;

    private String packageName;

    @ManyToOne
    @JoinColumn(name = "packageTypeId")
    private PackageType packageType;


    private String description;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "package_feature_map",
            joinColumns = @JoinColumn(name = "fk_package_id", referencedColumnName = "pkPackageId"),
            inverseJoinColumns = @JoinColumn(name = "fk_feature_id", referencedColumnName = "pkFeatureId")
    )
    private Set<Feature> features = new HashSet<>();

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;
    private Long updatedBy;

}
