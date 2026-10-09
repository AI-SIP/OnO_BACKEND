package com.aisip.OnO.backend.learningreport.service;

import com.aisip.OnO.backend.folder.entity.Folder;
import com.aisip.OnO.backend.learningreport.dto.LearningOverviewPeriod;
import com.aisip.OnO.backend.learningreport.support.LearningReportTestSupport;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problemsolve.entity.AnswerStatus;
import com.aisip.OnO.backend.support.QueryCounter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 학습 보고서 개요는 캐시하지 않고 열 때마다 집계한다. 그래서 문제, 풀이, 폴더가 늘어도 쿼리 수가
 * 그대로인지를 {@code QueryCountRegressionTest} 와 같은 방식(3건과 30건 비교)으로 고정한다.
 */
@DisplayName("학습 보고서 개요 - 쿼리 수 회귀")
class LearningOverviewQueryCountTest extends LearningReportTestSupport {

    /**
     * 공부한 날 2(오답노트 작성, 풀이), 날짜별 채점 결과 1, 문제 수 1, 졸업 판정용 풀이 기록 1, 폴더별 집계 1.
     * 늘어나면 어느 칸이 쿼리를 더 쓰게 됐는지 확인한다.
     */
    private static final long EXPECTED_QUERY_COUNT = 6;

    @Autowired
    private QueryCounter queryCounter;

    private Long userId;
    private Folder root;

    @BeforeEach
    void setUpUser() {
        userId = fixtures.createUser("overview-count").getId();
        root = fixtures.createFolder(userId, "책장", null);
    }

    private void saveNotesWithSolves(int count) {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul")).withHour(9);
        for (int i = 0; i < count; i++) {
            Folder folder = fixtures.createFolder(userId, "폴더" + i, root);
            Problem problem = saveNoteInFolder(userId, folder, now.minusDays(40 + i));
            saveSolve(userId, problem, now.minusDays(i % 10), AnswerStatus.WRONG, null);
            saveSolve(userId, problem, now.minusDays(i % 10 + 1), AnswerStatus.CORRECT, null);
        }
    }

    @ParameterizedTest
    @EnumSource(LearningOverviewPeriod.class)
    @DisplayName("문제와 폴더가 열 배로 늘어도 쿼리 수는 그대로다")
    void queryCountDoesNotGrow(LearningOverviewPeriod period) {
        saveNotesWithSolves(3);
        long few = queryCounter.count(() -> learningOverviewService.getOverview(userId, period, null));

        saveNotesWithSolves(27);
        long many = queryCounter.count(() -> learningOverviewService.getOverview(userId, period, null));

        assertThat(many)
                .as("""
                        데이터가 늘어난 만큼 쿼리가 늘어나면 N+1 이다.
                        3건일 때 %d개, 30건일 때 %d개.""", few, many)
                .isEqualTo(few)
                .isEqualTo(EXPECTED_QUERY_COUNT);
    }
}
