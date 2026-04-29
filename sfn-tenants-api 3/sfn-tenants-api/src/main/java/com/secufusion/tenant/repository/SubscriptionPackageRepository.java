package com.secufusion.tenant.repository;

import com.secufusion.tenant.entity.SubscriptionPackage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SubscriptionPackageRepository extends JpaRepository<SubscriptionPackage, Long> {

    Optional<SubscriptionPackage> findByPackageName(String packageName);

    boolean existsByPackageName(String packageName);
}
