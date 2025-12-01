package com.secufusion.iam.repository;

import com.secufusion.iam.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, Serializable> {


    Optional<Tenant> findByTenantID(String id);
    Optional<Tenant> findByRealmName(String realmName);

    Optional<Tenant> findByTenantName(String tenantName);

    Optional<Tenant> findByDomain(String host);

    boolean existsByTenantName(String tenantName);

    boolean existsByDomain(String domainName);

    boolean existsByPhoneNo(String domainName);

    boolean existsByEmail(String email);


    Optional<Tenant> findByEmail(String email);

    List<Tenant> findByParentTenantId(String parentTenantId);
}
