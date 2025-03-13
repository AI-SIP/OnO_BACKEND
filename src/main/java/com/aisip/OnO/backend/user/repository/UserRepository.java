package com.aisip.OnO.backend.user.repository;

import com.aisip.OnO.backend.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    Optional<User> findByIdentifier(String identifier);

    Optional<User> findByName(String name);

    Page<User> findAll(Pageable pageable);
}
