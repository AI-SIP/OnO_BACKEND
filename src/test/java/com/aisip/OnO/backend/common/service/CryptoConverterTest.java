package com.aisip.OnO.backend.common.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JPA 속성 변환기. User.identifier 저장/조회 경로에 그대로 끼어든다.
 */
@DisplayName("암호화 컨버터")
class CryptoConverterTest {

    private static final String SECRET_KEY = "Mff9sez/IyXQh2Iv4cG+3h9/7CgxebyWR+zFbOP9GUk=";

    private CryptoConverter converter;
    private CryptoService cryptoService;

    @BeforeEach
    void setUp() {
        cryptoService = new CryptoService();
        ReflectionTestUtils.setField(cryptoService, "secretKey", SECRET_KEY);
        converter = new CryptoConverter(cryptoService);
    }

    @Nested
    @DisplayName("정상 변환")
    class Conversion {

        @Test
        @DisplayName("엔티티 값을 암호화해 컬럼에 넣고, 컬럼 값을 복호화해 되돌린다")
        void convertsBothDirections() {
            String identifier = "google-identifier-1";

            String column = converter.convertToDatabaseColumn(identifier);

            assertThat(column).isNotEqualTo(identifier);
            assertThat(converter.convertToEntityAttribute(column)).isEqualTo(identifier);
        }

        @Test
        @DisplayName("한글 identifier 도 손실 없이 왕복한다")
        void convertsUnicode() {
            String identifier = "애플-로그인-식별자";

            assertThat(converter.convertToEntityAttribute(converter.convertToDatabaseColumn(identifier)))
                    .isEqualTo(identifier);
        }

        @Test
        @DisplayName("빈 문자열도 왕복한다")
        void convertsEmptyString() {
            assertThat(converter.convertToEntityAttribute(converter.convertToDatabaseColumn("")))
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("null 처리")
    class NullHandling {

        @Test
        @DisplayName("null 속성은 암호화하지 않고 null 로 저장한다")
        void passesNullAttributeThrough() {
            assertThat(converter.convertToDatabaseColumn(null))
                    .as("AttributeConverter 계약상 null 은 그대로 통과해야 한다")
                    .isNull();
        }

        @Test
        @DisplayName("null 컬럼은 복호화하지 않고 null 로 읽는다")
        void passesNullColumnThrough() {
            assertThat(converter.convertToEntityAttribute(null)).isNull();
        }
    }

    @Nested
    @DisplayName("복호화 실패")
    class DecryptionFailure {

        @Test
        @DisplayName("암호문이 아닌 값이 컬럼에 있으면 복호화 오류로 감싸 던진다")
        void wrapsDecryptionFailure() {
            assertThatThrownBy(() -> converter.convertToEntityAttribute("평문이 그대로 들어간 값"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("복호화 오류");
        }
    }
}
