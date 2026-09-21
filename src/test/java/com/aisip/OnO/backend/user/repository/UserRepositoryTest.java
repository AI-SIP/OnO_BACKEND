package com.aisip.OnO.backend.user.repository;

import com.aisip.OnO.backend.support.IntegrationTestSupport;
import com.aisip.OnO.backend.user.dto.UserRegisterDto;
import com.aisip.OnO.backend.user.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * user 테이블의 DB 제약을 실제 MySQL 위에서 확인한다.
 *
 * <p>identifier 는 {@code CryptoConverter} 로 암호화돼 저장되고 유니크 인덱스가 걸려 있다.
 * 암호문 길이·소프트 삭제·유니크 충돌은 H2 나 목으로는 재현되지 않는 영역이다.
 */
@DisplayName("UserRepository")
class UserRepositoryTest extends IntegrationTestSupport {

    @Autowired
    private UserRepository userRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private static User userWith(String identifier) {
        return User.from(UserRegisterDto.builder()
                .identifier(identifier)
                .email(identifier + "@test.ono")
                .name("사용자")
                .platform("GOOGLE")
                .build());
    }

    private String rawIdentifierColumn(Long userId) {
        return (String) entityManager
                .createNativeQuery("SELECT identifier FROM user WHERE id = :id")
                .setParameter("id", userId)
                .getSingleResult();
    }

    private long rawRowCount(Long userId) {
        Number count = (Number) entityManager
                .createNativeQuery("SELECT COUNT(*) FROM user WHERE id = :id")
                .setParameter("id", userId)
                .getSingleResult();
        return count.longValue();
    }

    private int identifierColumnLength() {
        Number length = (Number) entityManager.createNativeQuery("""
                        SELECT character_maximum_length
                        FROM information_schema.columns
                        WHERE table_schema = DATABASE()
                          AND table_name = 'user'
                          AND column_name = 'identifier'
                        """)
                .getSingleResult();
        return length.intValue();
    }

    @Nested
    @DisplayName("identifier 암호화")
    class IdentifierEncryption {

        @Test
        @DisplayName("DB 에는 암호문으로 저장되고 엔티티로 읽으면 평문으로 복호화된다")
        @Transactional
        void storesCiphertextAndReadsBackPlaintext() {
            User saved = userRepository.saveAndFlush(userWith("google-sub-1234567890"));
            entityManager.clear();

            assertThat(rawIdentifierColumn(saved.getId()))
                    .as("평문이 그대로 보이면 암호화가 동작하지 않은 것이다")
                    .isNotEqualTo("google-sub-1234567890");
            assertThat(userRepository.findById(saved.getId()).orElseThrow().getIdentifier())
                    .isEqualTo("google-sub-1234567890");
        }

        @Test
        @DisplayName("암호화된 컬럼이어도 평문 identifier 로 조회된다")
        @Transactional
        void findsByPlaintextIdentifier() {
            userRepository.saveAndFlush(userWith("apple-sub-000123.abcdef"));
            entityManager.clear();

            assertThat(userRepository.findByIdentifier("apple-sub-000123.abcdef"))
                    .as("소셜 재로그인은 이 조회 하나로 기존 계정을 찾는다")
                    .isPresent();
            assertThat(userRepository.findByIdentifier("apple-sub-000123.abcdeg")).isEmpty();
        }

        @Test
        @DisplayName("실제 소셜 로그인에서 오는 길이의 identifier 는 컬럼 안에 들어간다")
        @Transactional
        void fitsRealWorldSocialIdentifiers() {
            String googleSub = "1".repeat(21);
            String appleSub = "000123.4d1f8b3c9a2e4f6b8d0c1e3a5f7b9d1c.0123";

            assertThatCode(() -> {
                userRepository.saveAndFlush(userWith(googleSub));
                userRepository.saveAndFlush(userWith(appleSub));
            }).doesNotThrowAnyException();
        }

        /**
         * AES 암호문을 Base64 로 담기 때문에 평문 길이의 약 1.4배가 저장된다.
         * identifier 컬럼이 varchar(255) 인 한, 평문 176자부터는 암호문이 256자가 되어 저장에 실패한다.
         * 실제 소셜 identifier 는 64자 이하라 아직 닿지 않지만 한계는 명시해 둔다.
         */
        @Test
        @DisplayName("암호문이 컬럼 길이를 넘는 identifier 는 저장 시점에 실패한다")
        @Transactional
        void failsWhenCiphertextExceedsColumnLength() {
            assertThat(identifierColumnLength()).isEqualTo(255);

            assertThatCode(() -> userRepository.saveAndFlush(userWith("a".repeat(175))))
                    .as("평문 175자까지는 암호문이 236자라 들어간다")
                    .doesNotThrowAnyException();

            assertThatThrownBy(() -> userRepository.saveAndFlush(userWith("b".repeat(176))))
                    .as("평문 176자부터 암호문이 256자가 되어 컬럼을 넘는다")
                    .isInstanceOf(Exception.class);
        }
    }

    @Nested
    @DisplayName("identifier 유니크 인덱스")
    class IdentifierUniqueness {

        @Test
        @DisplayName("같은 identifier 로 두 번 저장하면 유니크 제약에 걸린다")
        @Transactional
        void rejectsDuplicateIdentifier() {
            userRepository.saveAndFlush(userWith("duplicate-sub"));

            assertThatThrownBy(() -> userRepository.saveAndFlush(userWith("duplicate-sub")))
                    .as("한 소셜 계정이 두 개의 오답노트 계정을 갖게 되면 데이터가 갈린다")
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("같은 평문은 항상 같은 암호문이 되어 유니크 인덱스와 조회가 성립한다")
        @Transactional
        void encryptsDeterministically() {
            User first = userRepository.saveAndFlush(userWith("deterministic-sub"));
            String firstCiphertext = rawIdentifierColumn(first.getId());

            first.maskIdentifierForDeletion("deleted:" + first.getId() + ":token");
            userRepository.saveAndFlush(first);
            userRepository.delete(first);
            userRepository.flush();
            entityManager.clear();

            User second = userRepository.saveAndFlush(userWith("deterministic-sub"));

            assertThat(rawIdentifierColumn(second.getId()))
                    .as("암호문이 매번 달라지면 findByIdentifier 도 유니크 인덱스도 무력화된다")
                    .isEqualTo(firstCiphertext);
        }
    }

    @Nested
    @DisplayName("소프트 삭제")
    class SoftDelete {

        @Test
        @DisplayName("삭제된 사용자는 조회 결과에서 빠지지만 행 자체는 남는다")
        @Transactional
        void hidesDeletedUserFromQueries() {
            User user = userRepository.saveAndFlush(userWith("soft-delete-sub"));
            Long userId = user.getId();

            userRepository.delete(user);
            userRepository.flush();
            entityManager.clear();

            assertThat(userRepository.findById(userId)).isEmpty();
            assertThat(userRepository.findByIdentifier("soft-delete-sub")).isEmpty();
            assertThat(userRepository.findAll()).noneMatch(u -> u.getId().equals(userId));
            assertThat(rawRowCount(userId))
                    .as("소프트 삭제이므로 감사·통계를 위해 행은 남아 있어야 한다")
                    .isEqualTo(1L);
        }

        @Test
        @DisplayName("탈퇴해도 identifier 를 비우지 않으면 같은 소셜 계정으로 재가입할 수 없다")
        @Transactional
        void keepsUniqueIndexAfterSoftDelete() {
            User user = userRepository.saveAndFlush(userWith("rejoin-sub"));
            userRepository.delete(user);
            userRepository.flush();
            entityManager.clear();

            assertThatThrownBy(() -> userRepository.saveAndFlush(userWith("rejoin-sub")))
                    .as("UserService 가 탈퇴 시 identifier 를 마스킹하는 이유가 이것이다")
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("탈퇴 시 identifier 를 마스킹하면 같은 소셜 계정으로 재가입할 수 있다")
        @Transactional
        void allowsRejoinAfterIdentifierMasking() {
            User user = userRepository.saveAndFlush(userWith("masked-sub"));
            user.maskIdentifierForDeletion("deleted:" + user.getId() + ":token");
            userRepository.saveAndFlush(user);
            userRepository.delete(user);
            userRepository.flush();
            entityManager.clear();

            assertThatCode(() -> userRepository.saveAndFlush(userWith("masked-sub")))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("미접속 사용자 조회")
    class InactiveUsers {

        private User savedUserWithActivity(String identifier, LocalDateTime lastActiveAt, LocalDate lastNotifiedAt) {
            User user = userWith(identifier);
            User saved = userRepository.saveAndFlush(user);
            entityManager.createNativeQuery(
                            "UPDATE user SET last_active_at = :active, last_notified_at = :notified WHERE id = :id")
                    .setParameter("active", lastActiveAt)
                    .setParameter("notified", lastNotifiedAt)
                    .setParameter("id", saved.getId())
                    .executeUpdate();
            entityManager.clear();
            return saved;
        }

        @Test
        @DisplayName("7~30일 미접속 사용자만 재참여 대상으로 뽑는다")
        @Transactional
        void selectsReengagementTargets() {
            LocalDateTime now = LocalDateTime.now();
            User target = savedUserWithActivity("reengage-target", now.minusDays(10), null);
            savedUserWithActivity("reengage-active", now.minusDays(2), null);
            savedUserWithActivity("reengage-long-inactive", now.minusDays(60), null);
            savedUserWithActivity("reengage-recently-notified", now.minusDays(10), LocalDate.now());

            List<User> found = userRepository.findUsersForReengagement(
                    now.minusDays(7), now.minusDays(30), LocalDate.now().minusDays(5));

            assertThat(found)
                    .extracting(User::getId)
                    .as("최근 접속자·장기 미접속자·최근 알림 대상자는 제외돼야 한다")
                    .containsExactly(target.getId());
        }

        @Test
        @DisplayName("30일을 넘겨 미접속한 사용자는 장기 미접속 대상으로 뽑는다")
        @Transactional
        void selectsLongInactiveTargets() {
            LocalDateTime now = LocalDateTime.now();
            User target = savedUserWithActivity("long-inactive-target", now.minusDays(45), null);
            savedUserWithActivity("long-inactive-recent", now.minusDays(10), null);

            List<User> found = userRepository.findUsersForLongInactiveReengagement(
                    now.minusDays(30), LocalDate.now().minusDays(30));

            assertThat(found).extracting(User::getId).containsExactly(target.getId());
        }

        @Test
        @DisplayName("알림 발송일은 벌크 업데이트로 한 번에 기록한다")
        @Transactional
        void bulkUpdatesNotifiedDate() {
            User first = userRepository.saveAndFlush(userWith("bulk-1"));
            User second = userRepository.saveAndFlush(userWith("bulk-2"));
            LocalDate today = LocalDate.now();

            userRepository.bulkUpdateLastNotifiedAt(List.of(first.getId(), second.getId()), today);
            entityManager.clear();

            assertThat(userRepository.findById(first.getId()).orElseThrow().getLastNotifiedAt()).isEqualTo(today);
            assertThat(userRepository.findById(second.getId()).orElseThrow().getLastNotifiedAt()).isEqualTo(today);
        }
    }
}
