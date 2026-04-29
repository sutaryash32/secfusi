package com.secufusion.events.repository;

import com.secufusion.events.entity.TenantApiMappingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TenantApiMappingRepository extends JpaRepository<TenantApiMappingEntity, Long> {
    List<TenantApiMappingEntity> findAll();
}
