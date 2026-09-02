package com.aisip.OnO.backend.common.emoji;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 앱이 보내는 이모지 키 화이트리스트 검증.
 *
 * <p>허용 목록에 없는 키가 통과하면 앱이 렌더링할 에셋을 찾지 못하므로 서버에서 막는다.
 */
@DisplayName("커스텀 이모지 검증")
class CustomEmojiValidatorTest {

    private final CustomEmojiValidator validator = new CustomEmojiValidator();

    @Nested
    @DisplayName("허용된 키")
    class AllowedKey {

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"happy_tears", "studying_together", "trophy_celebration", "wearing_scarf"})
        @DisplayName("화이트리스트에 있으면 통과한다")
        void passesWhitelistedKey(String emojiKey) {
            assertThat(validator.isAllowed(emojiKey)).isTrue();
            assertThatCode(() -> validator.validate(emojiKey)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("허용되지 않은 키")
    class DisallowedKey {

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {"unknown_emoji", "HAPPY_TEARS", " happy_tears", "happy_tears ", "😀", "<script>"})
        @DisplayName("목록에 없으면 400 으로 거절한다")
        void rejectsUnknownKey(String emojiKey) {
            assertThat(validator.isAllowed(emojiKey))
                    .as("대소문자/공백까지 정확히 일치해야 한다")
                    .isFalse();
            assertThatThrownBy(() -> validator.validate(emojiKey))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(exception -> ((ApplicationException) exception).getErrorCase())
                    .isEqualTo(CustomEmojiErrorCase.INVALID_EMOJI_KEY);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("null 과 빈 문자열도 거절한다")
        void rejectsNullAndEmpty(String emojiKey) {
            assertThat(validator.isAllowed(emojiKey)).isFalse();
            assertThatThrownBy(() -> validator.validate(emojiKey))
                    .isInstanceOf(ApplicationException.class);
        }
    }

    @Nested
    @DisplayName("선택 입력 검증")
    class NullableValidation {

        @Test
        @DisplayName("null 은 미입력으로 보고 통과시킨다")
        void allowsNull() {
            assertThatCode(() -> validator.validateNullable(null)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("값이 있으면 화이트리스트 검증을 그대로 적용한다")
        void validatesNonNullValue() {
            assertThatCode(() -> validator.validateNullable("cool_sunglasses")).doesNotThrowAnyException();
            assertThatThrownBy(() -> validator.validateNullable("not_an_emoji"))
                    .isInstanceOf(ApplicationException.class);
        }

        @Test
        @DisplayName("빈 문자열은 미입력이 아니라 잘못된 값으로 본다")
        void rejectsEmptyString() {
            assertThatThrownBy(() -> validator.validateNullable(""))
                    .isInstanceOf(ApplicationException.class);
        }
    }
}
