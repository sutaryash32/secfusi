package com.secufusion.tenant.repository;

import com.secufusion.tenant.entity.Groups;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

@Repository
public interface GroupsRepository extends JpaRepository<Groups, Serializable> {
    Optional<Groups> findByNameAndTenantId(String name, String tenantId);

    void deleteByTenantId(String tenantID);

    Optional<Groups> findByTenantIdAndIsAdminAndIsDefault(String tenantId, char y, char y1);

    @Query("""
        select count(r)
        from Groups g
        join g.mappedRoles r
        where g.pkGroupId = :groupId
          and r.pkRoleId = :roleId
    """)
    long countRoleMapping(
            @Param("groupId") String groupId,
            @Param("roleId") String roleId
    );


    /**
     * Direct SQL insert into group_role_map, bypassing JPA collection management.
     * Use this instead of group.getMappedRoles().add(role) to avoid Hibernate
     * duplicate insert issues when multiple roles are assigned in the same session.
     */
    @Modifying
    @Transactional
    @Query(value = "INSERT INTO group_role_map (fk_group_id, fk_role_id) VALUES (:groupId, :roleId) ON CONFLICT DO NOTHING",
            nativeQuery = true)
    void insertRoleMappingIfAbsent(@Param("groupId") String groupId, @Param("roleId") String roleId);

    // Dashboard statistics methods
    long countByTenantId(String tenantId);

    void deleteByTenantIdIn(List<String> targetIds);

}

