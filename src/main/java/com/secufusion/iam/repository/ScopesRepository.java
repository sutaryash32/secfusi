package com.secufusion.iam.repository;

import com.secufusion.iam.entity.Scopes;
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
        join s.tenantTypes tt
        where upper(tt.tenantTypeName) in :tenantTypes
    """)
    List<Scopes> findByUserTypes(@Param("tenantTypes") List<String> tenantTypes);

    List<Scopes> findAll();

    Optional<Scopes> findByPkScopeId(String pkScopeId);

    List<Scopes> findByMenuNameIgnoreCase(String menuName);

    List<Scopes> findByMenuNameIgnoreCaseAndSubMenuIgnoreCase(
            String menuName,
            String subMenu
    );

}
