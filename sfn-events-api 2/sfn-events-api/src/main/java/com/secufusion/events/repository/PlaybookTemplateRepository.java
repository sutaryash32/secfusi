package com.secufusion.events.repository;

import com.secufusion.events.entity.IncidentCategory;
import com.secufusion.events.entity.PlaybookTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlaybookTemplateRepository extends JpaRepository<PlaybookTemplate, String> {

    List<PlaybookTemplate> findByTenant_TenantIDAndIsActiveTrueOrderByNameAsc(String tenantId);

    List<PlaybookTemplate> findByTenant_TenantIDAndCategoryAndIsActiveTrueOrderByNameAsc(
            String tenantId, IncidentCategory category);

    Optional<PlaybookTemplate> findByPkPlaybookTemplateIdAndTenant_TenantID(
            String templateId, String tenantId);

    boolean existsByTenant_TenantIDAndName(String tenantId, String name);
}
