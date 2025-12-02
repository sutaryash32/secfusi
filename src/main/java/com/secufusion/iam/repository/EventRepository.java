package com.secufusion.iam.repository;

import com.secufusion.iam.entity.Event;
import com.secufusion.iam.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;


@Repository
public interface EventRepository extends JpaRepository<Event, Long> {
    Optional<List<Event>> findByUser(User user);
}
