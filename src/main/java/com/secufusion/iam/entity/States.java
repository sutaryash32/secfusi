package com.secufusion.iam.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "states")
@AllArgsConstructor
@NoArgsConstructor
@Data
public class States {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long pkStateId;

    private String stateName;
    private Long countryId;
}
