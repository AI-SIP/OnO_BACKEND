package com.aisip.OnO.backend.user.repository;

import com.aisip.OnO.backend.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long>, UserRepositoryCustom {
    Optional<User> findByEmail(String email);

    Optional<User> findByIdentifier(String identifier);

    Optional<User> findByName(String name);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :userId")
    Optional<User> findByIdForUpdate(@Param("userId") Long userId);

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

    @Transactional
    @Modifying
    @Query("UPDATE User u SET u.lastNotifiedAt = :date WHERE u.id IN :userIds")
    void bulkUpdateLastNotifiedAt(@Param("userIds") List<Long> userIds, @Param("date") LocalDate date);

    // 7일 ~ 30일 미접속 유저 (5일 간격)
    @Query("""
            SELECT u FROM User u
            WHERE u.lastActiveAt < :reengagementCutoff
              AND u.lastActiveAt >= :inactiveCutoff
              AND (u.lastNotifiedAt IS NULL OR u.lastNotifiedAt < :notificationCutoff)
            """)
    List<User> findUsersForReengagement(
            @Param("reengagementCutoff") LocalDateTime reengagementCutoff,
            @Param("inactiveCutoff") LocalDateTime inactiveCutoff,
            @Param("notificationCutoff") LocalDate notificationCutoff
    );

    // 30일 초과 미접속 유저 (30일 간격)
    @Query("""
            SELECT u FROM User u
            WHERE u.lastActiveAt < :inactiveCutoff
              AND (u.lastNotifiedAt IS NULL OR u.lastNotifiedAt < :notificationCutoff)
            """)
    List<User> findUsersForLongInactiveReengagement(
            @Param("inactiveCutoff") LocalDateTime inactiveCutoff,
            @Param("notificationCutoff") LocalDate notificationCutoff
    );
}
