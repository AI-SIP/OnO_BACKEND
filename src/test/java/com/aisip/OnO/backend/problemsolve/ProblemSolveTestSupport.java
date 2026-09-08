package com.aisip.OnO.backend.problemsolve;

import com.aisip.OnO.backend.problem.dto.ProblemRegisterDto;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problem.repository.ProblemRepository;
import com.aisip.OnO.backend.problemsolve.entity.AnswerStatus;
import com.aisip.OnO.backend.problemsolve.entity.ProblemSolve;
import com.aisip.OnO.backend.problemsolve.entity.ProblemSolveImageData;
import com.aisip.OnO.backend.problemsolve.repository.ProblemSolveImageDataRepository;
import com.aisip.OnO.backend.problemsolve.repository.ProblemSolveRepository;
import com.aisip.OnO.backend.support.IntegrationTestSupport;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 복습 기록 도메인 테스트 공통 준비물.
 *
 * <p>{@link IntegrationTestSupport} 의 애노테이션 조합을 그대로 물려받아
 * 스프링 컨텍스트를 공유한다. 새 빈은 등록하지 않는다.
 */
public abstract class ProblemSolveTestSupport extends IntegrationTestSupport {

    protected static final LocalDateTime PRACTICED_AT = LocalDateTime.of(2026, 1, 10, 9, 30);

    @Autowired
    protected ProblemSolveRepository problemSolveRepository;

    @Autowired
    protected ProblemSolveImageDataRepository problemSolveImageDataRepository;

    @Autowired
    protected ProblemRepository problemRepository;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    /**
     * MockMvc 요청에 인증 주체를 싣는다.
     *
     * <p>{@code authenticateAs(userId)} 는 SecurityContextHolder 만 채우므로
     * MockMvc 필터 체인까지 전달되지 않는다(실측 401). 요청 단위 post-processor 로
     * 넣어야 실제 시큐리티 필터를 거친 인증 상태가 된다.
     */
    protected RequestPostProcessor asUser(Long userId) {
        return asUser(userId, "ROLE_MEMBER");
    }

    protected RequestPostProcessor asUser(Long userId, String role) {
        return authentication(new UsernamePasswordAuthenticationToken(
                userId, null, List.of(new SimpleGrantedAuthority(role))));
    }

    protected Problem saveProblem(Long userId) {
        return problemRepository.save(
                Problem.from(new ProblemRegisterDto(null, "메모", null, null, null), userId));
    }

    /** 서비스를 거치지 않고 복습 기록을 직접 만든다. 조회/삭제 시나리오의 사전 데이터용. */
    protected ProblemSolve saveSolve(Problem problem, Long userId, LocalDateTime practicedAt) {
        return saveSolve(problem, userId, practicedAt, AnswerStatus.WRONG);
    }

    protected ProblemSolve saveSolve(Problem problem, Long userId, LocalDateTime practicedAt, AnswerStatus status) {
        return problemSolveRepository.save(
                ProblemSolve.create(problem, userId, practicedAt, status, "회고", null, 120, null));
    }

    protected ProblemSolveImageData saveImage(ProblemSolve problemSolve, String imageUrl, int order) {
        ProblemSolveImageData image = ProblemSolveImageData.create(imageUrl, order);
        image.updateProblemSolve(problemSolve);
        return problemSolveImageDataRepository.save(image);
    }

    protected int countRowsIncludingDeleted(Long problemId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM problem_solve WHERE problem_id = ?", Integer.class, problemId);
        return count == null ? 0 : count;
    }
}
