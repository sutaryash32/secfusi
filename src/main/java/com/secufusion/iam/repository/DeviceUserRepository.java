package com.secufusion.iam.repository;

import com.secufusion.iam.entity.DeviceUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceUserRepository extends JpaRepository<DeviceUser, String> {

    /**
     * Find device user by ID and tenant ID
     */
    @Query("SELECT du FROM DeviceUser du WHERE du.pkDeviceUserId = :deviceUserId AND du.tenantId = :tenantId")
    Optional<DeviceUser> findByIdAndTenantId(
            @Param("deviceUserId") String deviceUserId,
            @Param("tenantId") String tenantId
    );
}
