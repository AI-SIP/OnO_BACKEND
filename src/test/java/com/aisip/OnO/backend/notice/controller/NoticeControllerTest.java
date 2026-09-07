package com.aisip.OnO.backend.notice.controller;

import com.aisip.OnO.backend.notice.dto.NoticeCreateRequestDto;
import com.aisip.OnO.backend.notice.dto.NoticeResponseDto;
import com.aisip.OnO.backend.notice.entity.NoticeType;
import com.aisip.OnO.backend.notice.service.NoticeService;
import com.aisip.OnO.backend.support.IntegrationTestSupport;
import com.aisip.OnO.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("서비스 공지 API")
class NoticeControllerTest extends IntegrationTestSupport {

    private static final String ACTIVE_CACHE_KEY = "NOTICE:ACTIVE";

    @Autowired
    private NoticeService noticeService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private User user;

    @BeforeEach
    void setUp() {
        redisTemplate.delete(ACTIVE_CACHE_KEY);
        user = fixtures.createUser();
    }

    private NoticeResponseDto registerNotice() {
        return noticeService.registerNotice(
                new NoticeCreateRequestDto("점검 안내", "오늘 밤 점검이 있습니다.", NoticeType.WARNING, 24));
    }

    @Nested
    @DisplayName("활성 공지 조회")
    class GetActiveNotice {

        @Test
        @DisplayName("공지가 있으면 내용을 내려준다")
        void returnsActiveNotice() throws Exception {
            NoticeResponseDto notice = registerNotice();
            authenticateAs(user.getId());

            mockMvc.perform(get("/api/notices/active"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.noticeId").value(notice.noticeId()))
                    .andExpect(jsonPath("$.data.title").value("점검 안내"))
                    .andExpect(jsonPath("$.data.type").value("WARNING"))
                    .andExpect(jsonPath("$.data.expiresAt").exists());
        }

        @Test
        @DisplayName("공지가 없으면 data 없이 성공만 내려준다")
        void returnsNoDataWhenNoticeAbsent() throws Exception {
            authenticateAs(user.getId());

            mockMvc.perform(get("/api/notices/active"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").doesNotExist())
                    .andExpect(jsonPath("$.errorCode").doesNotExist());
        }

        @Test
        @DisplayName("인증하지 않으면 접근할 수 없다")
        void rejectsUnauthenticatedRequest() throws Exception {
            registerNotice();
            clearAuthentication();

            mockMvc.perform(get("/api/notices/active"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("그만 보기")
    class DismissNotice {

        @Test
        @DisplayName("그만 보기를 누르면 다음 조회에서 안 나온다")
        void hidesNoticeAfterDismiss() throws Exception {
            NoticeResponseDto notice = registerNotice();
            authenticateAs(user.getId());

            mockMvc.perform(post("/api/notices/{noticeId}/dismiss", notice.noticeId()))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/notices/active"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").doesNotExist());
        }

        @Test
        @DisplayName("없는 공지를 그만 보기 하면 404 를 낸다")
        void returnsNotFoundForUnknownNotice() throws Exception {
            authenticateAs(user.getId());

            mockMvc.perform(post("/api/notices/{noticeId}/dismiss", 999_999L))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value(14001));
        }
    }
}
