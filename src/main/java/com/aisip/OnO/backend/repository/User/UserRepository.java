package com.aisip.OnO.backend.repository.User;

import com.aisip.OnO.backend.entity.User.User;
import com.aisip.OnO.backend.entity.User.UserType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    Optional<User> findByIdentifier(String identifier);

    Optional<User> findByName(String name);

    Long countUserByType(UserType type);

    Page<User> findAll(Pageable pageable);
}
