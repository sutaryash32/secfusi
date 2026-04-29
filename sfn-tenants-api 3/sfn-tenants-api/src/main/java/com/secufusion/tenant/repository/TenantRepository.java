package com.secufusion.tenant.repository;


import com.secufusion.tenant.entity.Tenant;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, Serializable> {

    Optional<Tenant> findByTenantID(String id);

    Optional<Tenant> findByTenantName(String tenantName);

   @Query("SELECT DISTINCT t FROM Tenant t LEFT JOIN FETCH t.users WHERE t.tenantName = :name")
    Optional<Tenant> findByTenantNameWithUsers(@Param("name") String name);

    @Query("SELECT DISTINCT t FROM Tenant t LEFT JOIN FETCH t.users WHERE t.tenantID = :id")
    Optional<Tenant> findByTenantIDWithUsers(@Param("id") String id);

    /**
     * Update tenant status without merging the entire entity graph.
     * This prevents EntityNotFoundException when the tenant has references to uncommitted entities.
     */
    @Modifying
    @Query("UPDATE Tenant t SET t.status = :status, t.loginUrl = :loginUrl, t.tenantCode = :tenantCode WHERE t.tenantID = :tenantId")
    int updateTenantStatus(@Param("tenantId") String tenantId, @Param("status") String status, @Param("loginUrl") String loginUrl, @Param("tenantCode") String tenantCode);

    /**
     * Update provision steps bitmask without merging the entire entity graph.
     * This prevents EntityNotFoundException when the tenant has dirty cascaded associations
     * (e.g. selfManaged tenants with extra role-group mappings from ensureAdminUser).
     */
    @Modifying
    @Query("UPDATE Tenant t SET t.provisionStepsCompleted = :steps WHERE t.tenantID = :tenantId")
    int updateProvisionSteps(@Param("tenantId") String tenantId, @Param("steps") int steps);

    @Modifying
    @Query("DELETE FROM Tenant t WHERE t.tenantID = :tenantId")
    void deleteByTenantIdDirect(@Param("tenantId") String tenantId);

    @Query("SELECT COUNT(t) > 0 FROM Tenant t WHERE LOWER(t.tenantName) = LOWER(:tenantName) AND t.tenantID <> :tenantId")
    boolean existsByTenantNameAndTenantIDNot(@Param("tenantName") String tenantName, @Param("tenantId") String tenantId);

    @Query("SELECT COUNT(t) > 0 FROM Tenant t WHERE LOWER(t.tenantName) = LOWER(:tenantName)")
    boolean existsByTenantName(@Param("tenantName") String tenantName);

    boolean existsByDomainAndTenantIDNot(String domain, String tenantId);

    boolean existsByDomain(String domainName);

    boolean existsByPhoneNoAndTenantIDNot(String phoneNo, String tenantId);

    boolean existsByPhoneNo(String domainName);

    boolean existsByEmailAndTenantIDNot(String email, String tenantId);

    boolean existsByEmail(String email);

    @Query("""
        SELECT COUNT(t) > 0
        FROM Tenant t
        WHERE LOWER(
            SUBSTRING(t.email, LOCATE('@', t.email) + 1)
        ) = LOWER(:domain)
    """)
    boolean existsTenantByEmailDomain(@Param("domain") String domain);


    // Tenant Code methods for MSI deployment identification

    /**
     * Find tenant by unique tenant code.
     * Used by browser extension to identify tenant without requiring login.
     */
    Optional<Tenant> findByTenantCode(String tenantCode);

    /**
     * Check if tenant code already exists.
     */
    boolean existsByTenantCode(String tenantCode);

    /**
     * Check if tenant code exists for a different tenant (for updates).
     */
    boolean existsByTenantCodeAndTenantIDNot(String tenantCode, String tenantId);

    List<Tenant> findByParentTenantId(String parentTenantId);

    /**
     * Batch query: find all tenants whose parent is in the given set.
     * Used by collectChildren to avoid N+1 queries per hierarchy level.
     */
    List<Tenant> findByParentTenantIdIn(List<String> parentTenantIds);

    Optional<Tenant> findByDomain(String host);

    // Dashboard query methods

    /**
     * Count tenants by type.
     */
    long countByTenantType(String tenantType);

    /**
     * Count tenants by type and status.
     */
    long countByTenantTypeAndStatus(String tenantType, String status);

    /**
     * Count child tenants (enterprises) under an MSSP.
     */
    long countByParentTenantId(String parentTenantId);

    /**
     * Count child tenants with specific status under an MSSP.
     */
    long countByParentTenantIdAndStatus(String parentTenantId, String status);

    /**
     * Find all child tenants with specific status.
     */
    List<Tenant> findByParentTenantIdAndStatus(String parentTenantId, String status);

    /**
     * Get all tenants of a specific type.
     */
    List<Tenant> findByTenantType(String tenantType);

    /**
     * Get all MSSPs (for Master MSSP dashboard).
     */
    @Query("SELECT t FROM Tenant t WHERE t.tenantType = 'MSSP'")
    List<Tenant> findAllMssps();

    /**
     * Get all Enterprises (for Master MSSP dashboard).
     */
    @Query("SELECT t FROM Tenant t WHERE t.tenantType = 'ENTERPRISE'")
    List<Tenant> findAllEnterprises();

    /**
     * Count all enterprises across all MSSPs.
     */
    @Query("SELECT COUNT(t) FROM Tenant t WHERE t.tenantType = 'ENTERPRISE'")
    long countAllEnterprises();

    /**
     * Count active enterprises across all MSSPs.
     */
    @Query("SELECT COUNT(t) FROM Tenant t WHERE t.tenantType = 'ENTERPRISE' AND t.status = 'ACTIVE'")
    long countActiveEnterprises();

    /**
     * Count all MSSPs.
     */
    @Query("SELECT COUNT(t) FROM Tenant t WHERE t.tenantType = 'MSSP'")
    long countAllMssps();

    /**
     * Count active MSSPs.
     */
    @Query("SELECT COUNT(t) FROM Tenant t WHERE t.tenantType = 'MSSP' AND t.status = 'ACTIVE'")
    long countActiveMssps();

    // Scoped MASTER_MSSP counts (only tenants within this MASTER_MSSP's tree)

    /** Count MSSPs that are direct children of the given MASTER_MSSP. */
    @Query("SELECT COUNT(t) FROM Tenant t WHERE t.parentTenantId = :masterMsspId AND t.tenantType = 'MSSP'")
    long countMsspsByParentTenantId(@Param("masterMsspId") String masterMsspId);

    /** Count active MSSPs that are direct children of the given MASTER_MSSP. */
    @Query("SELECT COUNT(t) FROM Tenant t WHERE t.parentTenantId = :masterMsspId AND t.tenantType = 'MSSP' AND t.status = 'ACTIVE'")
    long countActiveMsspsByParentTenantId(@Param("masterMsspId") String masterMsspId);

    /** Count enterprises under a MASTER_MSSP (enterprises whose MSSP parent is under this MASTER_MSSP). */
    @Query("SELECT COUNT(t) FROM Tenant t WHERE t.tenantType = 'ENTERPRISE' AND t.parentTenantId IN (SELECT m.tenantID FROM Tenant m WHERE m.parentTenantId = :masterMsspId)")
    long countEnterprisesUnderMasterMssp(@Param("masterMsspId") String masterMsspId);

    /** Count active enterprises under a MASTER_MSSP. */
    @Query("SELECT COUNT(t) FROM Tenant t WHERE t.tenantType = 'ENTERPRISE' AND t.status = 'ACTIVE' AND t.parentTenantId IN (SELECT m.tenantID FROM Tenant m WHERE m.parentTenantId = :masterMsspId)")
    long countActiveEnterprisesUnderMasterMssp(@Param("masterMsspId") String masterMsspId);

    /** Count pending enterprises under a MASTER_MSSP. */
    @Query("SELECT COUNT(t) FROM Tenant t WHERE t.tenantType = 'ENTERPRISE' AND t.status = 'PENDING' AND t.parentTenantId IN (SELECT m.tenantID FROM Tenant m WHERE m.parentTenantId = :masterMsspId)")
    long countPendingEnterprisesUnderMasterMssp(@Param("masterMsspId") String masterMsspId);

    /** Count suspended tenants (MSSPs + Enterprises) under a MASTER_MSSP. */
    @Query("""
        SELECT COUNT(t) FROM Tenant t
        WHERE t.status = 'SUSPENDED'
        AND (t.parentTenantId = :masterMsspId
             OR t.parentTenantId IN (SELECT m.tenantID FROM Tenant m WHERE m.parentTenantId = :masterMsspId))
        """)
    long countSuspendedTenantsUnderMasterMssp(@Param("masterMsspId") String masterMsspId);

    /** Return the tenantID strings of MSSPs under a MASTER_MSSP (used for 2-level user aggregation). */
    @Query("SELECT t.tenantID FROM Tenant t WHERE t.parentTenantId = :masterMsspId AND t.tenantType = 'MSSP'")
    List<String> findMsspIdsByMasterMsspId(@Param("masterMsspId") String masterMsspId);

    /**
     * Find tenants by email domain pattern.
     * Searches for tenants where the email contains the given domain.
     */
    @Query("SELECT t FROM Tenant t WHERE t.email LIKE CONCAT('%', :domain, '%')")
    List<Tenant> findByEmailDomainContaining(@Param("domain") String domain);

    /**
     * Find tenants by domain pattern.
     * Searches for tenants where the domain contains the given pattern.
     * Input must be sanitized via sanitizeLikeInput() before calling.
     */
    @Query("SELECT t FROM Tenant t WHERE t.domain LIKE CONCAT('%', :domain, '%')")
    List<Tenant> findByDomainContaining(@Param("domain") String domain);

    /**
     * Find tenants by tenant name pattern.
     * Searches for tenants where the tenant name contains the given pattern.
     * Input must be sanitized via sanitizeLikeInput() before calling.
     */
    @Query("SELECT t FROM Tenant t WHERE t.tenantName LIKE CONCAT('%', :name, '%')")
    List<Tenant> findByTenantNameContaining(@Param("name") String name);

    /**
     * Check if any tenant exists with email containing the given subdomain pattern.
     * Used to ensure email subdomain uniqueness (e.g., @magellanic-cloud should be unique).
     */
    @Query("""
        SELECT COUNT(t) > 0
        FROM Tenant t
        WHERE t.email LIKE CONCAT('%@', :subdomain)
    """)
    boolean existsByEmailSubdomain(@Param("subdomain") String subdomain);

    /**
     * Check if any tenant (other than the given one) exists with email containing the given subdomain pattern.
     * Used to ensure email subdomain uniqueness during updates.
     */
    @Query("""
        SELECT COUNT(t) > 0
        FROM Tenant t
        WHERE t.email LIKE CONCAT('%@', :subdomain)
          AND t.tenantID <> :tenantId
    """)
    boolean existsByEmailSubdomainAndTenantIDNot(
            @Param("subdomain") String subdomain,
            @Param("tenantId") String tenantId
    );

    @Query("SELECT t FROM Tenant t WHERE t.parentTenantId IS NOT NULL")
    List<Tenant> findChildTenantsPaged(Pageable pageable);

    /**
     * Find the maximum tenant code sequence number for a given prefix.
     * Returns the numeric suffix (e.g. for codes like "CENE-003" with prefix "CENE-", returns "003").
     * Used to generate the next sequence without looping.
     */
    @Query("SELECT MAX(t.tenantCode) FROM Tenant t WHERE t.tenantCode LIKE CONCAT(:prefix, '%')")
    Optional<String> findMaxTenantCodeByPrefix(@Param("prefix") String prefix);

    /**
     * Find tenant by Keycloak realm name.
     * Used for SSO repair operations.
     */
    @Query("SELECT t FROM Tenant t WHERE LOWER(t.realmName) = LOWER(:realmName)")
    Optional<Tenant> findByRealmName(@Param("realmName") String realmName);

    /**
     * Find all tenants with a given status.
     */
    List<Tenant> findByStatus(String status);

    /**
     * Find tenants stuck in provisioning (not ACTIVE/SUSPENDED) and older than the given cutoff.
     * Used by the scheduled retry to resume failed or interrupted provisioning.
     */
    @Query("""
        SELECT DISTINCT t FROM Tenant t LEFT JOIN FETCH t.users
        WHERE t.status IN ('CREATING', 'CREATED_LOCAL', 'REALM_CREATED', 'CLIENT_CREATED', 'USER_CREATED', 'FAILED')
          AND t.createdAt < :cutoff
    """)
    List<Tenant> findStuckProvisioningTenants(@Param("cutoff") java.time.Instant cutoff);

}
