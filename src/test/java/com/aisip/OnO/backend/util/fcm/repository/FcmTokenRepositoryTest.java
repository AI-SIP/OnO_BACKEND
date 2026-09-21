package com.aisip.OnO.backend.util.fcm.repository;

import com.aisip.OnO.backend.support.IntegrationTestSupport;
import com.aisip.OnO.backend.util.fcm.dto.FcmTokenRequestDto;
import com.aisip.OnO.backend.util.fcm.entity.FcmToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FCM 토큰 저장소 - 실제 MySQL 컨테이너 위에서 검증한다.
 *
 * <p>토큰 조회가 사용자 경계를 넘으면 남의 기기로 푸시가 나간다.
 * 유니크 제약(user_id, token)은 중복 등록 방지의 마지막 방어선이라 DB 레벨에서 확인한다.
 */
@DisplayName("FCM 토큰 저장소")
class FcmTokenRepositoryTest extends IntegrationTestSupport {

    @Autowired
    private FcmTokenRepository fcmTokenRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * FcmToken 은 IDENTITY 가 아니라 테이블 기반 식별자 생성기({@code fcm_token_seq})를 쓴다.
     * 테스트 격리용 전체 테이블 비우기가 이 시퀀스 테이블의 초기 행까지 날려
     * "could not read a hi value" 로 저장이 실패하므로, 여기서 초기 행을 되살린다.
     */
    @BeforeEach
    void restoreIdentifierSequenceRow() {
        Integer rows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM fcm_token_seq", Integer.class);
        if (rows == null || rows == 0) {
            jdbcTemplate.update("INSERT INTO fcm_token_seq (next_val) VALUES (1)");
        }
    }

    private FcmToken save(Long userId, String token) {
        return fcmTokenRepository.save(FcmToken.From(new FcmTokenRequestDto(token), userId));
    }

    @Nested
    @DisplayName("사용자별 조회")
    class FindByUser {

        @Test
        @DisplayName("본인 기기 토큰만 조회된다")
        void findsOnlyOwnTokens() {
            save(1L, "my-phone");
            save(1L, "my-tablet");
            save(2L, "other-phone");

            List<FcmToken> tokens = fcmTokenRepository.findAllByUserId(1L);

            assertThat(tokens)
                    .extracting(FcmToken::getToken)
                    .as("다른 사용자의 토큰이 섞이면 엉뚱한 사람에게 알림이 간다")
                    .containsExactlyInAnyOrder("my-phone", "my-tablet");
        }

        @Test
        @DisplayName("등록한 기기가 없으면 빈 목록이다")
        void returnsEmptyListForUserWithoutDevice() {
            save(2L, "other-phone");

            assertThat(fcmTokenRepository.findAllByUserId(1L)).isEmpty();
        }

        @Test
        @DisplayName("토큰 값으로 단건 조회한다")
        void findsByTokenValue() {
            save(1L, "my-phone");

            Optional<FcmToken> found = fcmTokenRepository.findByToken("my-phone");

            assertThat(found).isPresent();
            assertThat(found.get().getUserId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("없는 토큰 조회는 빈 Optional 이다")
        void returnsEmptyForUnknownToken() {
            assertThat(fcmTokenRepository.findByToken("unknown-token")).isEmpty();
        }
    }

    @Nested
    @DisplayName("존재 여부 확인")
    class Exists {

        @Test
        @DisplayName("사용자와 토큰이 모두 일치해야 존재로 본다")
        void requiresBothUserAndToken() {
            save(1L, "my-phone");

            assertThat(fcmTokenRepository.existsByUserIdAndToken(1L, "my-phone")).isTrue();
            assertThat(fcmTokenRepository.existsByUserIdAndToken(2L, "my-phone"))
                    .as("같은 토큰이라도 사용자가 다르면 새로 등록해야 한다")
                    .isFalse();
            assertThat(fcmTokenRepository.existsByUserIdAndToken(1L, "other-token")).isFalse();
        }

        @Test
        @DisplayName("토큰 값만으로도 존재 여부를 확인할 수 있다")
        void checksByTokenOnly() {
            save(1L, "my-phone");

            assertThat(fcmTokenRepository.existsByToken("my-phone")).isTrue();
            assertThat(fcmTokenRepository.existsByToken("unknown")).isFalse();
        }
    }

    @Nested
    @DisplayName("유니크 제약")
    class UniqueConstraint {

        @Test
        @DisplayName("같은 사용자가 같은 토큰을 두 번 저장하면 DB 가 막는다")
        void rejectsDuplicatedUserAndToken() {
            save(1L, "my-phone");

            assertThatThrownBy(() -> fcmTokenRepository.saveAndFlush(
                    FcmToken.From(new FcmTokenRequestDto("my-phone"), 1L)))
                    .as("중복 토큰이 쌓이면 같은 기기에 알림이 여러 번 간다")
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("사용자가 다르면 같은 토큰이라도 저장된다 - 기기 인계 상황")
        void allowsSameTokenForDifferentUsers() {
            save(1L, "shared-device");

            assertThatCode(() -> fcmTokenRepository.saveAndFlush(
                    FcmToken.From(new FcmTokenRequestDto("shared-device"), 2L)))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("삭제")
    class Delete {

        @Test
        @DisplayName("토큰 값으로 삭제한다")
        void deletesByToken() {
            save(1L, "my-phone");
            save(1L, "my-tablet");

            fcmTokenRepository.deleteByToken("my-phone");

            assertThat(fcmTokenRepository.findAllByUserId(1L))
                    .extracting(FcmToken::getToken)
                    .containsExactly("my-tablet");
        }

        @Test
        @DisplayName("토큰 값 기준 삭제는 그 토큰을 가진 모든 사용자 행을 지운다")
        void deletesTokenAcrossUsers() {
            save(1L, "shared-device");
            save(2L, "shared-device");

            fcmTokenRepository.deleteByToken("shared-device");

            assertThat(fcmTokenRepository.findAllByUserId(1L)).isEmpty();
            assertThat(fcmTokenRepository.findAllByUserId(2L))
                    .as("기기가 다른 계정으로 넘어가면 이전 등록은 함께 정리된다")
                    .isEmpty();
        }

        @Test
        @DisplayName("없는 토큰을 삭제해도 예외가 나지 않는다")
        void deletingUnknownTokenIsSafe() {
            assertThatCode(() -> fcmTokenRepository.deleteByToken("unknown-token"))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("공통 감사 필드")
    class Auditing {

        @Test
        @DisplayName("저장 시 생성/수정 시각이 자동으로 채워진다")
        void fillsAuditingColumns() {
            FcmToken saved = save(1L, "my-phone");

            assertThat(saved.getCreatedAt()).as("BaseEntity 감사 필드").isNotNull();
            assertThat(saved.getUpdatedAt()).isNotNull();
            assertThat(saved.getDeletedAt()).as("생성 직후에는 삭제 시각이 없다").isNull();
        }
    }
}
