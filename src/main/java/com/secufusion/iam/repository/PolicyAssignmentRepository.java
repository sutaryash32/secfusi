package com.secufusion.iam.repository;

import com.secufusion.iam.entity.PolicyAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PolicyAssignmentRepository extends JpaRepository<PolicyAssignment, String> {

    @Modifying
    @Query("DELETE FROM PolicyAssignment p " +
           "WHERE p.azureResourceId = :groupId " +
           "AND p.assignmentType = 'APIKEY_GROUP'")
    void deleteByEventsGroupId(@Param("groupId") String groupId);

}