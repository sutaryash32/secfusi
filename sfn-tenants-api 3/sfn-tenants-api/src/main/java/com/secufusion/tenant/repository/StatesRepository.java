package com.secufusion.tenant.repository;


import com.secufusion.tenant.entity.States;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.io.Serializable;
import java.util.List;

@Repository
public interface StatesRepository extends JpaRepository<States, Serializable> {

}
