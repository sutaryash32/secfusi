package com.secufusion.iam.repository;

import com.secufusion.iam.entity.Package;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.io.Serializable;

@Repository
public interface PackageRepository extends JpaRepository<Package, Serializable> {
    boolean existsByPackageNameIgnoreCase(String packageName);

}
