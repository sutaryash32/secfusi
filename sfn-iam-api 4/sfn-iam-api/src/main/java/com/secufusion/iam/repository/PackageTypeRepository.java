package com.secufusion.iam.repository;

import com.secufusion.iam.entity.PackageType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.io.Serializable;

@Repository
public interface PackageTypeRepository extends JpaRepository<PackageType, Serializable> {
}
