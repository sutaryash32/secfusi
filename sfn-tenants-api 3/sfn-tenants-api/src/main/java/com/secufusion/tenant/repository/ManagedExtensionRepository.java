package com.secufusion.tenant.repository;

import com.secufusion.tenant.entity.ManagedExtension;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManagedExtensionRepository
        extends JpaRepository<ManagedExtension, String> {
}
