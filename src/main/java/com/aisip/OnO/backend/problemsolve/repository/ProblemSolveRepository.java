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

    /**
     * 훈장 판정용. 한 사용자의 복습 기록을 복습 시각 순서로 한 번에 훑는다.
     *
     * <p>훈장 여섯 개(집념·불사조·새벽반·올빼미·무결점·회고왕)가 전부 이 표를 본다. 조건마다 따로 세면
     * 훈장 화면을 열 때마다 같은 표를 여섯 번 훑게 되는데, 한 번 읽어 자바에서 여섯 값을 함께 만들면
     * 쿼리가 하나다. 무결점(연속 정답)과 불사조(오답 뒤 정답)는 애초에 순서를 봐야 해서
     * 집계 함수로는 안 되고, 그 둘이 이미 순서대로 훑기를 요구하므로 나머지도 같은 훑기에 얹는다.
     *
     * <p>복습 시각이 같은 기록이 있을 수 있어 id 로 한 번 더 정렬한다. 기준이 없으면 연속 판정이
     * 같은 데이터에서도 호출마다 달라진다.
     *
     * <p>{@code idx_problem_solve_user_practiced_at (user_id, practiced_at)} 이 그대로 쓰인다.
     */
    @Query("""
            SELECT new com.aisip.OnO.backend.problemsolve.repository.ProblemSolveMark(
                pr.problem.id, pr.practicedAt, pr.answerStatus, LENGTH(TRIM(pr.reflection)))
            FROM ProblemSolve pr
            WHERE pr.userId = :userId
            ORDER BY pr.practicedAt ASC, pr.id ASC
            """)
    List<ProblemSolveMark> findAllMarksByUserId(@Param("userId") Long userId);

    void deleteAllByProblemId(Long problemId);
}
