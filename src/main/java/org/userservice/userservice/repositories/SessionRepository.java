package org.userservice.userservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.userservice.userservice.models.Session;


public interface SessionRepository extends JpaRepository<Session, Long> {
    Session save(Session session);
}