package com.aisip.OnO.backend.common.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 사용자 identifier 를 DB 에 저장할 때 쓰는 대칭키 암복호화.
 *
 * <p>identifier 는 로그인 때마다 암호문으로 조회되므로, 같은 평문이 항상 같은 암호문이 돼야 한다.
 * 이 성질이 깨지면 기존 사용자가 전부 로그인 불가가 된다.
 */
@DisplayName("암복호화 서비스")
class CryptoServiceTest {

    /** application-test.yml 과 같은 32바이트(AES-256) 키 */
    private static final String SECRET_KEY = "Mff9sez/IyXQh2Iv4cG+3h9/7CgxebyWR+zFbOP9GUk=";

    private CryptoService cryptoService;

    @BeforeEach
    void setUp() {
        cryptoService = new CryptoService();
        ReflectionTestUtils.setField(cryptoService, "secretKey", SECRET_KEY);
    }

    @Nested
    @DisplayName("암복호화 왕복")
    class RoundTrip {

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {
                "google-oauth2|1234567890",
                "한글 식별자",
                "이모지 포함 🙂🚀",
                "공백 포함 문자열",
                "!@#$%^&*()_+-=[]{}|;':\",./<>?",
                ""
        })
        @DisplayName("암호화한 값을 복호화하면 원문이 그대로 돌아온다")
        void restoresOriginalText(String plainText) throws Exception {
            String encrypted = cryptoService.encrypt(plainText);

            assertThat(cryptoService.decrypt(encrypted))
                    .as("유니코드/특수문자/빈 문자열이 모두 손실 없이 복원돼야 한다")
                    .isEqualTo(plainText);
        }

        @Test
        @DisplayName("긴 문자열도 왕복한다")
        void restoresLongText() throws Exception {
            String plainText = "가".repeat(2000);

            assertThat(cryptoService.decrypt(cryptoService.encrypt(plainText))).isEqualTo(plainText);
        }

        @Test
        @DisplayName("암호문은 평문을 포함하지 않는다")
        void doesNotLeakPlainText() throws Exception {
            String plainText = "sensitive-identifier";

            assertThat(cryptoService.encrypt(plainText)).doesNotContain(plainText);
        }

        @Test
        @DisplayName("암호문은 Base64 로 디코딩 가능하다")
        void producesBase64Cipher() throws Exception {
            String encrypted = cryptoService.encrypt("identifier");

            assertThatCode(() -> Base64.getDecoder().decode(encrypted)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("결정성")
    class Determinism {

        @Test
        @DisplayName("같은 평문은 항상 같은 암호문이 된다 - identifier 로 사용자를 찾는 전제")
        void producesSameCipherForSamePlainText() throws Exception {
            String first = cryptoService.encrypt("google-identifier");
            String second = cryptoService.encrypt("google-identifier");

            assertThat(first)
                    .as("암호문이 매번 달라지면 identifier 로 기존 사용자를 찾지 못한다")
                    .isEqualTo(second);
        }

        @Test
        @DisplayName("다른 평문은 다른 암호문이 된다")
        void producesDifferentCipherForDifferentPlainText() throws Exception {
            assertThat(cryptoService.encrypt("user-a")).isNotEqualTo(cryptoService.encrypt("user-b"));
        }
    }

    @Nested
    @DisplayName("잘못된 입력")
    class InvalidInput {

        @Test
        @DisplayName("null 평문 암호화는 실패한다 - 호출부가 null 을 걸러야 한다")
        void failsOnNullPlainText() {
            assertThatThrownBy(() -> cryptoService.encrypt(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("Base64 가 아닌 문자열 복호화는 실패한다")
        void failsOnNonBase64Cipher() {
            assertThatThrownBy(() -> cryptoService.decrypt("not-base64-!!!"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("다른 키로 만든 암호문은 복호화되지 않는다")
        void failsOnCipherFromAnotherKey() throws Exception {
            CryptoService otherKeyService = new CryptoService();
            ReflectionTestUtils.setField(otherKeyService, "secretKey",
                    Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes()));
            String encrypted = otherKeyService.encrypt("identifier");

            assertThatThrownBy(() -> cryptoService.decrypt(encrypted))
                    .as("키가 다르면 패딩 검증에서 걸려야 한다")
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("블록 크기에 맞지 않는 암호문은 복호화되지 않는다")
        void failsOnCorruptedCipher() {
            String corrupted = Base64.getEncoder().encodeToString("깨진 암호문".getBytes());

            assertThatThrownBy(() -> cryptoService.decrypt(corrupted))
                    .isInstanceOf(Exception.class);
        }
    }
}
