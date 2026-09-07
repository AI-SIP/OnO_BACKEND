package com.aisip.OnO.backend.notice.service;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.notice.dto.NoticeCreateRequestDto;
import com.aisip.OnO.backend.notice.dto.NoticeResponseDto;
import com.aisip.OnO.backend.notice.entity.NoticeType;
import com.aisip.OnO.backend.notice.entity.ServiceNotice;
import com.aisip.OnO.backend.notice.exception.NoticeErrorCase;
import com.aisip.OnO.backend.notice.repository.ServiceNoticeRepository;
import com.aisip.OnO.backend.support.IntegrationTestSupport;
import com.aisip.OnO.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 실제 MySQL 과 Redis 컨테이너 위에서 검증하는 서비스 공지.
 *
 * <p>공지 조회는 캐시를 거치기 때문에 "DB 는 바뀌었는데 캐시가 옛 상태를 들고 있는" 경우가
 * 이 기능의 유일한 위험 지점이다. 등록과 제거 직후 조회가 바로 바뀌는지를 중점으로 본다.
 */
@DisplayName("서비스 공지")
class NoticeServiceTest extends IntegrationTestSupport {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String ACTIVE_CACHE_KEY = "NOTICE:ACTIVE";

    @Autowired
    private NoticeService noticeService;

    @Autowired
    private ServiceNoticeRepository serviceNoticeRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private User user;
    private User other;

    @BeforeEach
    void setUp() {
        // 활성 공지 캐시는 키가 하나뿐이라 앞선 테스트가 남긴 값이 그대로 넘어온다.
        redisTemplate.delete(ACTIVE_CACHE_KEY);
        user = fixtures.createUser();
        other = fixtures.createOtherUser();
    }

    private NoticeResponseDto registerNotice(String title) {
        return noticeService.registerNotice(
                new NoticeCreateRequestDto(title, "본문", NoticeType.INFO, 24));
    }

    private void assertNoticeError(NoticeErrorCase expected, Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(ApplicationException.class)
                .extracting(thrown -> ((ApplicationException) thrown).getErrorCase())
                .isEqualTo(expected);
    }

    @Nested
    @DisplayName("공지 등록")
    class RegisterNotice {

        @Test
        @DisplayName("등록하면 유저 조회에 바로 나온다")
        void registeredNoticeIsVisible() {
            NoticeResponseDto registered = registerNotice("점검 안내");

            NoticeResponseDto found = noticeService.findActiveNoticeForUser(user.getId());

            assertThat(found).isNotNull();
            assertThat(found.noticeId()).isEqualTo(registered.noticeId());
            assertThat(found.title()).isEqualTo("점검 안내");
            assertThat(found.type()).isEqualTo(NoticeType.INFO);
        }

        @Test
        @DisplayName("공지가 없다고 캐싱된 뒤에 등록해도 곧바로 반영된다")
        void registrationInvalidatesEmptyCache() {
            // 공지 없음이 캐싱되는 상황을 먼저 만든다
            assertThat(noticeService.findActiveNoticeForUser(user.getId())).isNull();
            assertThat(redisTemplate.hasKey(ACTIVE_CACHE_KEY)).isTrue();

            registerNotice("긴급 공지");

            assertThat(noticeService.findActiveNoticeForUser(user.getId())).isNotNull();
        }

        @Test
        @DisplayName("새로 등록하면 걸려 있던 공지는 내려간다")
        void newNoticeReplacesPreviousOne() {
            NoticeResponseDto first = registerNotice("첫 공지");
            NoticeResponseDto second = registerNotice("두 번째 공지");

            NoticeResponseDto found = noticeService.findActiveNoticeForUser(user.getId());

            assertThat(found.noticeId()).isEqualTo(second.noticeId());
            assertThat(serviceNoticeRepository.findById(first.noticeId()))
                    .as("이전 공지는 soft delete 되어 조회되지 않는다")
                    .isEmpty();
        }

        @Test
        @DisplayName("노출 시간을 비우면 24시간으로 잡힌다")
        void defaultsToTwentyFourHours() {
            LocalDateTime before = LocalDateTime.now(KST);

            NoticeResponseDto notice = noticeService.registerNotice(
                    new NoticeCreateRequestDto("공지", "본문", NoticeType.EVENT, null));

            assertThat(notice.expiresAt())
                    .isAfterOrEqualTo(before.plusHours(24).minusMinutes(1))
                    .isBeforeOrEqualTo(before.plusHours(24).plusMinutes(1));
        }
    }

    @Nested
    @DisplayName("등록 값 검증")
    class Validation {

        @Test
        @DisplayName("제목이 비면 거절한다")
        void rejectsBlankTitle() {
            assertNoticeError(NoticeErrorCase.NOTICE_TITLE_INVALID, () ->
                    noticeService.registerNotice(
                            new NoticeCreateRequestDto("  ", "본문", NoticeType.INFO, 24)));
        }

        @Test
        @DisplayName("내용이 500자를 넘으면 거절한다")
        void rejectsTooLongContent() {
            String tooLong = "가".repeat(501);

            assertNoticeError(NoticeErrorCase.NOTICE_CONTENT_INVALID, () ->
                    noticeService.registerNotice(
                            new NoticeCreateRequestDto("제목", tooLong, NoticeType.INFO, 24)));
        }

        @Test
        @DisplayName("유형을 비우면 거절한다")
        void rejectsNullType() {
            assertNoticeError(NoticeErrorCase.NOTICE_TYPE_REQUIRED, () ->
                    noticeService.registerNotice(
                            new NoticeCreateRequestDto("제목", "본문", null, 24)));
        }

        @Test
        @DisplayName("노출 시간이 범위를 벗어나면 거절한다")
        void rejectsOutOfRangeDuration() {
            assertNoticeError(NoticeErrorCase.NOTICE_DURATION_INVALID, () ->
                    noticeService.registerNotice(
                            new NoticeCreateRequestDto("제목", "본문", NoticeType.INFO, 0)));
            assertNoticeError(NoticeErrorCase.NOTICE_DURATION_INVALID, () ->
                    noticeService.registerNotice(
                            new NoticeCreateRequestDto("제목", "본문", NoticeType.INFO, 169)));
        }
    }

    @Nested
    @DisplayName("그만 보기")
    class Dismiss {

        @Test
        @DisplayName("누른 유저에게만 안 보이고 다른 유저는 계속 본다")
        void hidesOnlyForDismissingUser() {
            NoticeResponseDto notice = registerNotice("공지");

            noticeService.dismissNotice(notice.noticeId(), user.getId());

            assertThat(noticeService.findActiveNoticeForUser(user.getId())).isNull();
            assertThat(noticeService.findActiveNoticeForUser(other.getId())).isNotNull();
        }

        @Test
        @DisplayName("공지를 새로 올리면 그만 보기를 눌렀던 유저도 다시 본다")
        void newNoticeIsVisibleAgainAfterDismiss() {
            NoticeResponseDto first = registerNotice("첫 공지");
            noticeService.dismissNotice(first.noticeId(), user.getId());

            NoticeResponseDto second = registerNotice("두 번째 공지");

            NoticeResponseDto found = noticeService.findActiveNoticeForUser(user.getId());
            assertThat(found).isNotNull();
            assertThat(found.noticeId()).isEqualTo(second.noticeId());
        }

        @Test
        @DisplayName("없는 공지를 그만 보기 하면 거절한다")
        void rejectsUnknownNotice() {
            assertNoticeError(NoticeErrorCase.NOTICE_NOT_FOUND, () ->
                    noticeService.dismissNotice(999_999L, user.getId()));
        }
    }

    @Nested
    @DisplayName("공지 제거")
    class RemoveNotice {

        @Test
        @DisplayName("내리면 조회에서 바로 사라진다")
        void removedNoticeDisappearsImmediately() {
            NoticeResponseDto notice = registerNotice("공지");
            // 캐시에 올려 둔 상태에서 내려야 캐시가 실제로 비워지는지 확인할 수 있다
            assertThat(noticeService.findActiveNoticeForUser(user.getId())).isNotNull();

            noticeService.removeNotice(notice.noticeId());

            assertThat(noticeService.findActiveNoticeForUser(user.getId())).isNull();
        }

        @Test
        @DisplayName("없는 공지를 내리면 거절한다")
        void rejectsUnknownNotice() {
            assertNoticeError(NoticeErrorCase.NOTICE_NOT_FOUND, () ->
                    noticeService.removeNotice(999_999L));
        }
    }

    @Nested
    @DisplayName("노출 기간")
    class ActivePeriod {

        @Test
        @DisplayName("기간이 지난 공지는 나오지 않는다")
        void expiredNoticeIsNotReturned() {
            LocalDateTime now = LocalDateTime.now(KST);
            serviceNoticeRepository.save(ServiceNotice.of(
                    "지난 공지", "본문", NoticeType.INFO, now.minusHours(2), now.minusHours(1)));

            assertThat(noticeService.findActiveNoticeForUser(user.getId())).isNull();
        }

        @Test
        @DisplayName("시작 전인 공지는 나오지 않는다")
        void notYetStartedNoticeIsNotReturned() {
            LocalDateTime now = LocalDateTime.now(KST);
            serviceNoticeRepository.save(ServiceNotice.of(
                    "예약 공지", "본문", NoticeType.INFO, now.plusHours(1), now.plusHours(2)));

            assertThat(noticeService.findActiveNoticeForUser(user.getId())).isNull();
        }
    }
}
