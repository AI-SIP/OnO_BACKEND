package com.aisip.OnO.backend.concurrency;

import com.aisip.OnO.backend.admin.support.AdminTestSupport;
import com.aisip.OnO.backend.user.entity.User;
import com.github.gavlyukovskiy.boot.jdbc.decorator.DecoratedDataSource;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 관리자 통계 화면이 요청 하나에 커넥션을 하나만 쓰는지 본다. (#405)
 *
 * <p>운영에서 같은 초에 들어온 통계 요청 10건이 모두 30초를 기다리다 실패했다(Sentry JAVA-SPRING-BOOT-9W,
 * {@code total=10, active=10, waiting=6}). 통계 요청은 원래 수백 ms 에 끝난다.
 * 원인은 요청 하나가 커넥션을 두 개 쓰는 구조였다.
 * <ul>
 *   <li>컨트롤러가 {@code AdminStatsService} 의 읽기 전용 트랜잭션 여러 개를 차례로 부른다.
 *       open-in-view 가 켜져 있으면 첫 트랜잭션에서 받은 커넥션을 요청이 끝날 때까지 쥐고 있다.</li>
 *   <li>그 뒤 {@code topProblemWriters}, {@code topSolvers} 를 트랜잭션 밖에서 {@code JdbcTemplate} 으로 부른다.
 *       트랜잭션에 묶인 커넥션이 없으니 풀에서 <b>두 번째 커넥션</b>을 새로 받는다.</li>
 * </ul>
 * 그래서 풀 크기만큼 통계 요청이 겹치면 모두 하나씩 쥔 채 두 번째를 기다리며 서로를 막는다.
 * Sentry 스택도 10건 모두 {@code AdminAnalysisController} 의 {@code topProblemWriters} 에서 멈췄다.
 */
@DisplayName("동시성 - 관리자 통계 화면과 커넥션 풀 (#405)")
class AdminAnalysisConnectionPoolTest extends AdminTestSupport {

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("풀 크기만큼 통계 요청이 동시에 들어와도 커넥션을 기다리다 멈추지 않는다")
    void concurrentAnalysisRequestsDoNotExhaustPool() {
        User admin = createAdminUser();
        fixtures.createUser();
        var adminAuthentication = new UsernamePasswordAuthenticationToken(
                admin.getId(), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        LocalDate today = LocalDate.now();
        int poolSize = hikariDataSource().getMaximumPoolSize();

        ConcurrentRunner.Outcome outcome = ConcurrentRunner.runConcurrently(poolSize, index -> {
            try {
                mockMvc.perform(get("/admin/analysis")
                                .param("startDate", today.minusDays(29).toString())
                                .param("endDate", today.toString())
                                .with(authentication(adminAuthentication)))
                        .andExpect(status().isOk());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });

        assertThat(outcome.failures())
                .as("커넥션 대기로 실패한 통계 요청이 있으면 안 된다")
                .isEmpty();
    }

    /** p6spy 가 DataSource 를 감싸고 있어서 원본을 한 겹 벗겨야 Hikari 가 나온다. */
    private HikariDataSource hikariDataSource() {
        DataSource candidate = dataSource instanceof DecoratedDataSource decorated
                ? decorated.getRealDataSource()
                : dataSource;
        if (candidate instanceof HikariDataSource hikari) {
            return hikari;
        }
        throw new IllegalStateException("HikariDataSource 를 꺼내지 못했다: " + dataSource.getClass());
    }
}
