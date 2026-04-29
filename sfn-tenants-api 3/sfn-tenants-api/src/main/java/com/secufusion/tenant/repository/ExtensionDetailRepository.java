package com.secufusion.tenant.repository;

import com.secufusion.tenant.entity.ExtensionDetail;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExtensionDetailRepository
        extends JpaRepository<ExtensionDetail, String> {
}
