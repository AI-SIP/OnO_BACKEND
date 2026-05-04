package com.aisip.OnO.backend.problemsolve.repository;

import com.aisip.OnO.backend.problemsolve.entity.ProblemSolve;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProblemSolveRepository extends JpaRepository<ProblemSolve, Long> {

    @Query("SELECT pr FROM ProblemSolve pr " +
            "LEFT JOIN FETCH pr.images " +
            "WHERE pr.id = :problemSolveId")
    Optional<ProblemSolve> findByIdWithImages(@Param("problemSolveId") Long problemSolveId);

    @Query("SELECT DISTINCT pr FROM ProblemSolve pr " +
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

    @Query("SELECT MAX(pr.practicedAt) FROM ProblemSolve pr " +
            "WHERE pr.problem.id = :problemId")
    LocalDateTime findLastSolvedAtByProblemId(@Param("problemId") Long problemId);

    @Query("SELECT pr.problem.id as problemId, COUNT(pr) as solveCount, MAX(pr.practicedAt) as lastSolvedAt FROM ProblemSolve pr " +
            "WHERE pr.problem.id IN :problemIds " +
            "GROUP BY pr.problem.id")
    List<ProblemSolveSummary> findSolveSummariesByProblemIds(@Param("problemIds") Collection<Long> problemIds);

    void deleteAllByProblemId(Long problemId);
}
