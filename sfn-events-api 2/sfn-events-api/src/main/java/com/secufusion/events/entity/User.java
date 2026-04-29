package com.secufusion.events.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.springdoc.core.properties.SpringDocConfigProperties;

import java.time.LocalDateTime;
import java.util.Set;

@Entity
@Table(name = "Users")
@Data
@ToString(exclude = {"tenant", "mappedGroups"})
@EqualsAndHashCode(exclude = {"tenant", "mappedGroups"})
@AllArgsConstructor
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String pkUserId;

    private String firstName;
    private String lastName;

    @Column(nullable = false, unique = true)
    private String userName;

    @Column(nullable = false, unique = true)
    private String email;

    private String password;
    private String phoneNo;
    private String status;

    private String keycloakUserId;

    /** Newly added field */
    private boolean defaultUser;

    private String createdBy;

    private String lastUpdatedBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_tenant_id")
    @JsonIgnore
    private Tenant tenant;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_group_map",
            joinColumns = @JoinColumn(name = "fk_user_id"),
            inverseJoinColumns = @JoinColumn(name = "fk_group_id")
    )
    @JsonIgnoreProperties({"mappedUsers"})    // ### prevent loops
    private Set<Groups> mappedGroups;
}
