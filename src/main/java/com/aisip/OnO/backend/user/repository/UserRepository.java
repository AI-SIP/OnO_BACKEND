package com.aisip.OnO.backend.user.repository;

import com.aisip.OnO.backend.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    Optional<User> findByIdentifier(String identifier);

    Optional<User> findByName(String name);

    Page<User> findAll(Pageable pageable);

    List<User> findAllByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime startDateTime, LocalDateTime endDateTime);

    @Query("""
            SELECT FUNCTION('DATE', u.createdAt), COUNT(u)
            FROM User u
            WHERE u.createdAt BETWEEN :startDateTime AND :endDateTime
            GROUP BY FUNCTION('DATE', u.createdAt)
            """)
    List<Object[]> countDailyNewUsers(
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime
    );
}
