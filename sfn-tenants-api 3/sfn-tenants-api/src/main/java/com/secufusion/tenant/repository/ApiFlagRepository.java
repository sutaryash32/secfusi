package com.secufusion.tenant.repository;


import com.secufusion.tenant.dto.PathIdProjection;
import com.secufusion.tenant.entity.ApiFlagEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApiFlagRepository extends JpaRepository<ApiFlagEntity, Long> {
    Optional<ApiFlagEntity> findByPath(String path);
    @Query("SELECT a.id AS id, a.path AS path FROM ApiFlagEntity a")
    List<PathIdProjection> findAllPathAndId();

    Optional<ApiFlagEntity> findByApiKey(String path);
}
