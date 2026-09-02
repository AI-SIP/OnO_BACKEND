package com.aisip.OnO.backend.support;

import com.aisip.OnO.backend.config.rabbitmq.producer.ProblemAnalysisProducer;
import com.aisip.OnO.backend.util.ai.OpenAIClient;
import com.aisip.OnO.backend.util.fcm.service.FcmService;
import com.aisip.OnO.backend.util.fileupload.service.FileUploadService;
import com.aisip.OnO.backend.util.webhook.DiscordWebhookNotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

/**
 * 통합 테스트 공통 베이스.
 *
 * <p>기존 스위트는 38개 클래스 중 30개가 각자 다른 조합으로 {@code @SpringBootTest}를 선언해
 * 스프링 컨텍스트를 30개 가까이 띄웠고, 그 결과 전체 실행이 OOM으로 중단됐다.
 * 모든 통합 테스트가 이 클래스를 상속해 <b>동일한 애노테이션 조합</b>을 쓰면
 * 컨텍스트 캐시가 재사용되어 컨텍스트가 하나로 수렴한다.
 *
 * <p>외부 연동(FCM 실발송, S3 업로드, OpenAI 호출, Discord 웹훅)은 여기서 일괄 목 처리한다.
 * 개별 테스트가 제각각 {@code @MockBean}을 추가하면 그 순간 컨텍스트가 새로 뜨므로,
 * 목 대상을 늘려야 하면 이 클래스에 추가한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTestSupport {

    // 컨테이너 접속 정보는 TestContainersContextCustomizerFactory 가 모든 테스트에 주입한다.
    // 여기서 @DynamicPropertySource 를 따로 두면 이 베이스를 상속한 테스트와 그렇지 않은 테스트의
    // 컨텍스트 캐시 키가 갈라져 스프링 컨텍스트가 불필요하게 두 번 뜬다.

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected TestFixtures fixtures;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    /** FCM은 실사용자 푸시 발송 경로다. 테스트에서 절대 실제 호출되면 안 된다. */
    @MockBean
    protected FcmService fcmService;

    @MockBean
    protected FileUploadService fileUploadService;

    @MockBean
    protected OpenAIClient openAIClient;

    @MockBean
    protected DiscordWebhookNotificationService discordWebhookNotificationService;

    @MockBean
    protected ProblemAnalysisProducer problemAnalysisProducer;

    @BeforeEach
    void resetDatabaseAndSecurityContext() {
        databaseCleaner.clean();
        TestSecurityContextHolder.clearContext();
        SecurityContextHolder.clearContext();
    }

    /**
     * 실제 DB에 존재하는 userId로 인증 컨텍스트를 세운다.
     *
     * <p>기존 {@code @WithMockCustomUser}는 userId를 1로 고정해, DB에 없는 사용자로
     * 요청을 보내면서도 통과하는 테스트를 만들었다. 소유권 검증을 제대로 확인하려면
     * 픽스처가 만든 실제 사용자로 인증해야 한다.
     */
    protected void authenticateAs(Long userId) {
        authenticateAs(userId, "ROLE_MEMBER");
    }

    /**
     * 인증되지 않은 요청을 검증할 때 쓴다.
     *
     * <p>{@code SecurityContextHolder} 만 지우면 안 된다. MockMvc 요청은
     * {@code TestSecurityContextHolder} 에 담긴 값을 읽으므로 그쪽이 남아 있으면
     * 인증된 상태로 요청이 나간다.
     */
    protected void clearAuthentication() {
        TestSecurityContextHolder.clearContext();
        SecurityContextHolder.clearContext();
    }

    protected void authenticateAs(Long userId, String role) {
        // SecurityContextHolder 를 직접 세우면 MockMvc 요청에는 반영되지 않는다.
        // 시큐리티 필터 체인이 요청마다 SecurityContextRepository 에서 컨텍스트를 새로 읽어
        // 스레드에 올려둔 값을 덮어쓰기 때문이다. 실제로 이 때문에 인증이 필요한 요청이
        // 전부 401 로 떨어졌다.
        //
        // TestSecurityContextHolder 에 넣으면 spring-security-test 의 리포지토리가
        // 요청 시점에 이 값을 꺼내 쓰고, 동시에 SecurityContextHolder 에도 반영되므로
        // MockMvc 를 타지 않는 서비스 단위 테스트에서도 그대로 동작한다.
        TestSecurityContextHolder.setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        userId,
                        null,
                        List.of(new SimpleGrantedAuthority(role))
                )
        );
    }
}
