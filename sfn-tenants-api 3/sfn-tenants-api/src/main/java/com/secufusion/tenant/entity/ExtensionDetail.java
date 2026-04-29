package com.secufusion.tenant.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@Table(name = "extension_detail")
@AllArgsConstructor
@NoArgsConstructor
public class ExtensionDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String pkExtensionId;

    private String extensionId;

    private String extensionName;

    private String publisher;

}
