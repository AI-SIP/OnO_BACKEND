package com.aisip.OnO.backend.repository;

import com.aisip.OnO.backend.entity.User.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    User findByEmail(String email);

    Optional<User> findByIdentifier(String identifier);

    Optional<User> findByName(String name);

    List<User> findAll();
}
