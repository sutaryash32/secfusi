package com.secufusion.iam.repository;

import com.secufusion.iam.entity.Scopes;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.io.Serializable;
import java.util.List;

@Repository
public interface ScopesRepository extends JpaRepository<Scopes, Serializable> {

    List<Scopes> findByUserType(String userType);

    List<Scopes> findByUserTypeIn(List<String> userTypes);

}
