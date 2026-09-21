package com.aisip.OnO.backend.problem.repository;

import com.aisip.OnO.backend.admin.dto.AdminProblemResponseDto;
import com.aisip.OnO.backend.problem.dto.ReviewDueResponseDto;
import com.aisip.OnO.backend.problem.entity.AnalysisStatus;
import com.aisip.OnO.backend.problem.entity.Problem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ProblemRepositoryCustom {

    Optional<Problem> findProblemWithImageData(Long problemId);

    List<Problem> findAllByUserId(Long userId);

    List<Problem> findAllByFolderId(Long folderId);

    List<Problem> findAll();

    /**
     * 복습 예정 문제를 DTO 로 바로 조회한다.
     * Problem 엔티티로 읽으면 @OneToOne(mappedBy) problemAnalysis 때문에
     * 행마다 problem_analysis 조회가 한 번씩 더 나간다 (Sentry JAVA-SPRING-BOOT-59).
     */
    List<ReviewDueResponseDto.ReviewDueProblemDto> findReviewDueProblemDtos(Long userId, LocalDate today);

    Page<AdminProblemResponseDto> findAdminProblems(Pageable pageable);

    Map<LocalDate, Long> countDailyProblems(LocalDate startDate, LocalDate endDate);

    long countProblemAnalysesForActiveProblems();

    Map<AnalysisStatus, Long> countProblemAnalysesByStatusForActiveProblems();

    Map<AnalysisStatus, Long> countProblemAnalysesByStatusForActiveProblems(LocalDate startDate, LocalDate endDate);

    List<Problem> findAllProblemsByPracticeId(Long practiceId);

    /**
     * 커서 기반 폴더의 문제 조회
     * @param folderId 폴더 ID
     * @param cursor 마지막으로 조회한 문제 ID (null이면 처음부터)
     * @param size 조회할 개수
     * @return 문제 리스트 (size+1개 조회하여 hasNext 판단)
     */
    List<Problem> findProblemsByFolderWithCursor(Long folderId, Long cursor, int size);

    /**
     * 커서 기반 태그의 문제 조회
     * @param tagId 태그 ID
     * @param userId 유저 ID
     * @param cursor 마지막으로 조회한 문제 ID (null이면 처음부터)
     * @param size 조회할 개수
     * @return 문제 리스트 (size+1개 조회하여 hasNext 판단)
     */
    List<Problem> findProblemsByTagWithCursor(Long tagId, Long userId, Long cursor, int size);

    /**
     * 커서 기반 제목(contains) 문제 조회
     * @param titleQuery 제목 검색어 (contains)
     * @param userId 유저 ID
     * @param cursor 마지막으로 조회한 문제 ID (null이면 처음부터)
     * @param size 조회할 개수
     * @return 문제 리스트 (size+1개 조회하여 hasNext 판단)
     */
    List<Problem> findProblemsByTitleWithCursor(String titleQuery, Long userId, Long cursor, int size);
}
