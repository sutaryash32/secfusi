package com.secufusion.tenant.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "homepage")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Homepage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "pk_homepage_id", length = 36)
    private String pkHomepageId;

    @Column(length = 255)
    private String title;

    @Column(length = 200)
    private String url;

    @Column(name = "disable_addressbar")
    private boolean disableAddressBar;

}
