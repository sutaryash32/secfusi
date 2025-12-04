package com.secufusion.iam.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "scopes")
public class Scopes {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String pkScopeId;
    private String scopeName;
    private String displayName;
    private String description;
    private String userType;

    @Transient
    private Boolean enabled;

}
