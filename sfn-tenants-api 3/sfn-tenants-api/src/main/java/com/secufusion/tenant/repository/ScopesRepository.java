package com.secufusion.tenant.repository;


import com.secufusion.tenant.entity.Scopes;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

@Repository
public interface ScopesRepository extends JpaRepository<Scopes, Serializable> {

    @Query("""
        select distinct s
        from Scopes s
        join fetch s.tenantTypes tt
        where upper(tt.tenantTypeName) in :tenantTypes
    """)
    List<Scopes> findByUserTypes(@Param("tenantTypes") List<String> tenantTypes);

    List<Scopes> findAll();

}