package com.secufusion.tenant.entity;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "landingpage")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LandingPage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "pk_landingpage_id", length = 36)
    private String pkLandingPageId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 250)
    private String description;

    @Column(name = "fk_tenant_id", nullable = false, length = 36)
    private String fkTenantId;

    @OneToMany(mappedBy = "landingPage", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    @JsonManagedReference
    private List<Shortcut> shortcuts = new ArrayList<>();

    public void addShortcut(Shortcut shortcut) {
        shortcuts.add(shortcut);
        shortcut.setLandingPage(this);
    }

    public void removeShortcut(Shortcut shortcut) {
        shortcuts.remove(shortcut);
        shortcut.setLandingPage(null);
    }

    public void clearShortcuts() {
        shortcuts.forEach(s -> s.setLandingPage(null));
        shortcuts.clear();
    }
}
