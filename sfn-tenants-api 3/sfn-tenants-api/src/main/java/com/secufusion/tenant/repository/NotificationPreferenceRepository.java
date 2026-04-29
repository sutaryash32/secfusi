package com.secufusion.tenant.repository;

import com.secufusion.tenant.entity.NotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, String> {

    List<NotificationPreference> findByFkTenantIdAndFkUserId(String tenantId, String userId);

    Optional<NotificationPreference> findByFkTenantIdAndFkUserIdAndCategory(
            String tenantId, String userId, String category);

    Optional<NotificationPreference> findByFkTenantIdAndFkUserIdAndCategoryIsNull(
            String tenantId, String userId);

    // Bulk: fetch all preferences for a tenant + category (for batch dispatch)
    List<NotificationPreference> findByFkTenantIdAndCategory(String tenantId, String category);

    List<NotificationPreference> findByFkTenantIdAndCategoryIsNull(String tenantId);
}
