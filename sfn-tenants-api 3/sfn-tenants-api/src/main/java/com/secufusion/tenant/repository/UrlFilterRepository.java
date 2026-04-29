package com.secufusion.tenant.repository;

import com.secufusion.tenant.entity.UrlFilter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.io.Serializable;

public interface UrlFilterRepository extends JpaRepository<UrlFilter, Serializable> {
}
