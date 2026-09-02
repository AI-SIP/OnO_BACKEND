package com.aisip.OnO.backend.tag;

import com.aisip.OnO.backend.problem.dto.ProblemRegisterDto;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problem.repository.ProblemRepository;
import com.aisip.OnO.backend.support.IntegrationTestSupport;
import com.aisip.OnO.backend.tag.entity.ProblemTagMapping;
import com.aisip.OnO.backend.tag.entity.Tag;
import com.aisip.OnO.backend.tag.repository.ProblemTagMappingRepository;
import com.aisip.OnO.backend.tag.repository.TagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 태그 도메인 테스트 공통 준비물.
 *
 * <p>{@link IntegrationTestSupport} 와 같은 애노테이션 조합을 그대로 상속하므로
 * 스프링 컨텍스트는 여전히 하나로 유지된다. 여기에는 빈을 새로 등록하지 않고
 * 기존 빈을 조합한 헬퍼만 둔다.
 */
public abstract class TagTestSupport extends IntegrationTestSupport {

    @Autowired
    protected TagRepository tagRepository;

    @Autowired
    protected ProblemTagMappingRepository problemTagMappingRepository;

    @Autowired
    protected ProblemRepository problemRepository;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    /**
     * DatabaseCleaner 는 MySQL 만 비운다. 태그 목록 캐시는 Redis 에 남고,
     * TRUNCATE 로 auto_increment 가 초기화돼 userId 까지 재사용되므로
     * 비우지 않으면 앞 테스트의 캐시가 다음 테스트에 그대로 보인다.
     */
    @BeforeEach
    void flushRedis() {
        redisConnectionFactory.getConnection().serverCommands().flushDb();
    }


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

    protected Tag saveTag(Long userId, String name) {
        return tagRepository.save(Tag.from(userId, name, name.toLowerCase()));
    }

    protected Problem saveProblem(Long userId) {
        return problemRepository.save(
                Problem.from(new ProblemRegisterDto(null, "메모", null, null, null), userId));
    }

    protected ProblemTagMapping mapTag(Problem problem, Tag tag) {
        return problemTagMappingRepository.save(ProblemTagMapping.from(problem, tag));
    }

    /** 생성 시각이 같은 밀리초에 몰려 순서 검증이 흔들리는 것을 막는다. */
    protected void forceCreatedAt(ProblemTagMapping mapping, LocalDateTime createdAt) {
        jdbcTemplate.update("UPDATE problem_tag_mapping SET created_at = ? WHERE id = ?",
                createdAt, mapping.getId());
    }

    protected List<String> tagNames(Long userId) {
        return tagRepository.findAllByUserIdOrderByNameAsc(userId).stream()
                .map(Tag::getName)
                .toList();
    }

    protected int countRowsIncludingDeleted(Long userId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tag WHERE user_id = ?", Integer.class, userId);
        return count == null ? 0 : count;
    }
}
