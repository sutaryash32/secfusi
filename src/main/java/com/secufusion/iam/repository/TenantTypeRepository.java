package com.secufusion.iam.repository;

import com.secufusion.iam.entity.TenantType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

@Repository
public interface TenantTypeRepository extends JpaRepository<TenantType, Serializable> {

//    Optional<TenantType> findByNameIgnoreCase(String name);

    List<TenantType> findByTenantTypeNameIgnoreCaseIn(List<String> names);
}
