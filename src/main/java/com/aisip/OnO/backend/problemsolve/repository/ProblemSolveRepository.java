package com.aisip.OnO.backend.problemsolve.repository;

import com.aisip.OnO.backend.problemsolve.entity.ProblemSolve;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProblemSolveRepository extends JpaRepository<ProblemSolve, Long> {

    @Query("SELECT pr FROM ProblemSolve pr " +
            "LEFT JOIN FETCH pr.images " +
            "WHERE pr.id = :practiceRecordId")
    Optional<ProblemSolve> findByIdWithImages(@Param("practiceRecordId") Long practiceRecordId);

    @Query("SELECT pr FROM ProblemSolve pr " +
            "LEFT JOIN FETCH pr.images " +
            "WHERE pr.problem.id = :problemId " +
            "ORDER BY pr.practicedAt DESC")
    List<ProblemSolve> findAllByProblemIdWithImages(@Param("problemId") Long problemId);

    @Query("SELECT pr FROM ProblemSolve pr " +
            "WHERE pr.userId = :userId " +
            "ORDER BY pr.practicedAt DESC")
    List<ProblemSolve> findAllByUserId(@Param("userId") Long userId);

    @Query("SELECT pr FROM ProblemSolve pr " +
            "WHERE pr.problem.id = :problemId " +
            "ORDER BY pr.practicedAt DESC")
    List<ProblemSolve> findAllByProblemId(@Param("problemId") Long problemId);

    @Query("SELECT COUNT(pr) FROM ProblemSolve pr " +
            "WHERE pr.userId = :userId")
    Long countByUserId(@Param("userId") Long userId);

    @Query("SELECT COUNT(pr) FROM ProblemSolve pr " +
            "WHERE pr.problem.id = :problemId")
    Long countByProblemId(@Param("problemId") Long problemId);

    void deleteAllByProblemId(Long problemId);
}