package com.secufusion.iam.repository;

import com.secufusion.iam.entity.AccessLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccessLevelRepository extends JpaRepository<AccessLevel, Long> {

    Optional<AccessLevel> findByLevelCode(String levelCode);

    Optional<AccessLevel> findByLevelName(String levelName);

    List<AccessLevel> findByIsActiveTrueOrderByLevelValueAsc();

    List<AccessLevel> findAllByOrderByLevelValueAsc();
}
