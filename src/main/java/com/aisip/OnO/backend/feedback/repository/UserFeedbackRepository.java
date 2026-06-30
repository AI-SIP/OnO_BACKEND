package com.aisip.OnO.backend.feedback.repository;

import com.aisip.OnO.backend.feedback.entity.UserFeedback;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.OptionalDouble;

public interface UserFeedbackRepository extends JpaRepository<UserFeedback, Long> {

    Page<UserFeedback> findAllByOrderBySubmittedAtDesc(Pageable pageable);

    @Query("SELECT AVG(f.npsScore) FROM UserFeedback f WHERE f.npsScore IS NOT NULL")
    Double findAverageNpsScore();
}
