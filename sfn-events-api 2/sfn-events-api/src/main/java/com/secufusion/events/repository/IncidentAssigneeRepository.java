package com.secufusion.events.repository;

import com.secufusion.events.entity.IncidentAssignee;
import com.secufusion.events.entity.IncidentCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IncidentAssigneeRepository extends JpaRepository<IncidentAssignee, String> {

    List<IncidentAssignee> findByTenant_TenantIDOrderByAssignmentOrderAsc(String tenantId);

    List<IncidentAssignee> findByTenant_TenantIDAndCategoryAndIsActiveTrueOrderByAssignmentOrderAsc(
            String tenantId, IncidentCategory category);

    List<IncidentAssignee> findByTenant_TenantIDAndCategoryIsNullAndIsActiveTrueOrderByAssignmentOrderAsc(
            String tenantId);

    Optional<IncidentAssignee> findByPkIncidentAssigneeIdAndTenant_TenantID(
            String assigneeId, String tenantId);

    boolean existsByTenant_TenantIDAndCategoryAndUserId(
            String tenantId, IncidentCategory category, String userId);

    boolean existsByTenant_TenantIDAndCategoryIsNullAndUserId(
            String tenantId, String userId);
}
