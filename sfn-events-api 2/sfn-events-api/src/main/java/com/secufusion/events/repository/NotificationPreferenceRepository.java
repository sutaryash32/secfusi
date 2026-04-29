package com.secufusion.events.repository;

import com.secufusion.events.entity.NotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, String> {

    // Bulk: fetch all default preferences for a tenant (category IS NULL)
    List<NotificationPreference> findByFkTenantIdAndCategoryIsNull(String tenantId);

    // Bulk: fetch all preferences for a tenant + specific category
    List<NotificationPreference> findByFkTenantIdAndCategory(String tenantId, String category);
}
