package org.userservice.userservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.userservice.userservice.models.User;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User,Long> {

    User save(User user);
    Optional<User> findByEmail(String email);
}
