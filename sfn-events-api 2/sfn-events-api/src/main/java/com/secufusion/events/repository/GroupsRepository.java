package com.secufusion.events.repository;

import com.secufusion.events.entity.Groups;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GroupsRepository extends JpaRepository<Groups, String> {

    /**
     * Find default groups for a tenant (isDefault = 'Y')
     * Used for auto-assigning users who have no explicit group mappings.
     */
    List<Groups> findByTenantIdAndIsDefault(String tenantId, Character isDefault);
}
