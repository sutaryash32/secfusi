package com.secufusion.events.repository;

import com.secufusion.events.dto.PathIdProjection;
import com.secufusion.events.entity.ApiFlagEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApiFlagRepository extends JpaRepository<ApiFlagEntity, Long> {
}
