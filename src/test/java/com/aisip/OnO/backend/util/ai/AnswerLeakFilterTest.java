package com.aisip.OnO.backend.util.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 정답 노출 문장 거르기. 운영 데이터 없이 규칙만 본다(OpenAIClientTest 가 연결과 메트릭을 본다).
 */
@DisplayName("정답 노출 문장 거르기")
class AnswerLeakFilterTest {

    private static ProblemAnalysisResult result(String solution) {
        return result(solution, "부호를 놓치기 쉽습니다.", "교과서를 복습하세요.", List.of("판별식"));
    }

    private static ProblemAnalysisResult result(String solution, String commonMistakes, String studyTips, List<String> keyPoints) {
        return ProblemAnalysisResult.builder()
                .subject("수학")
                .problemType("이차방정식")
                .keyPoints(keyPoints)
                .solution(solution)
                .commonMistakes(commonMistakes)
                .studyTips(studyTips)
                .build();
    }

    private static String solutionAfter(String solution, String finalAnswer) {
        return AnswerLeakFilter.sanitize(result(solution), finalAnswer).result().getSolution();
    }

    @Nested
    @DisplayName("답 표기")
    class AnswerTokens {

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"3", "③", "3번", " 3번. "})
        @DisplayName("선지 번호는 3, ③, 3번을 모두 같은 답으로 본다")
        void choiceNumberVariants(String answer) {
            assertThat(AnswerLeakFilter.answerTokens(answer)).contains("3", "③", "3번");
        }

        @Test
        @DisplayName("등식이면 오른쪽 값도 답으로 본다")
        void equationValue() {
            assertThat(AnswerLeakFilter.answerTokens("x = -2")).containsExactly("x = -2", "-2");
        }

        @Test
        @DisplayName("빈 답과 null 은 표기가 없다")
        void blankAnswer() {
            assertThat(AnswerLeakFilter.answerTokens(null)).isEmpty();
            assertThat(AnswerLeakFilter.answerTokens("  ")).isEmpty();
            assertThat(AnswerLeakFilter.answerTokens("。")).isEmpty();
        }
    }

    @Nested
    @DisplayName("지우는 문장")
    class Removes {

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {
                "따라서 정답은 ③번이다.",
                "정답: 3.",
                "답은 3번입니다.",
                "따라서 답 3 을 고르면 된다.",
                "최종 답은 3이다.",
                "구하는 값은 3입니다.",
                "The answer is 3."
        })
        @DisplayName("단정 표현과 숫자 답이 함께 있는 문장")
        void assertionWithShortAnswer(String sentence) {
            assertThat(solutionAfter("조건을 정리하세요. " + sentence + " 부호를 확인하세요.", "3"))
                    .isEqualTo("조건을 정리하세요. 부호를 확인하세요.");
        }

        @Test
        @DisplayName("3글자 이상 글자 답은 단정 표현 없이도 지운다")
        void longTextAnswerStandalone() {
            assertThat(solutionAfter("빛을 받는 곳을 보세요. 이 과정은 광합성이다. 그래프를 해석하세요.", "광합성"))
                    .isEqualTo("빛을 받는 곳을 보세요. 그래프를 해석하세요.");
        }

        @Test
        @DisplayName("답의 공백 차이는 같은 답으로 본다(x = 2 와 x=2)")
        void whitespaceInsensitive() {
            assertThat(solutionAfter("식을 정리하세요. 그러면 x=2 가 나온다.", "x = 2")).isEqualTo("식을 정리하세요.");
        }

        @Test
        @DisplayName("줄바꿈으로 나뉜 단계도 문장으로 보고 그 줄만 지운다")
        void lineBreakSentences() {
            assertThat(solutionAfter("1단계: 조건 정리\n2단계: 정답은 ③\n3단계: 검산", "3"))
                    .isEqualTo("1단계: 조건 정리\n3단계: 검산");
        }

        @Test
        @DisplayName("목록 번호(1. ) 는 문장 끝으로 보지 않아 번호만 남지 않는다")
        void listNumberStaysWithSentence() {
            assertThat(solutionAfter("1. 조건을 정리하세요. 2. 정답은 3번입니다. 3. 검산하세요.", "3"))
                    .isEqualTo("1. 조건을 정리하세요. 3. 검산하세요.");
        }

        @Test
        @DisplayName("소수 답은 소수점에서 문장을 자르지 않는다")
        void decimalAnswer() {
            assertThat(solutionAfter("평균을 구하세요. 따라서 답은 2.5 이다.", "2.5")).isEqualTo("평균을 구하세요.");
        }

        @Test
        @DisplayName("keyPoints 는 항목마다 거르고, 남은 항목은 순서를 지킨다")
        void keyPointsPerItem() {
            AnswerLeakFilter.Outcome outcome = AnswerLeakFilter.sanitize(
                    result("s", "c", "t", List.of("판별식", "정답은 3번", "근과 계수의 관계")), "3");

            assertThat(outcome.result().getKeyPoints()).containsExactly("판별식", "근과 계수의 관계");
            assertThat(outcome.leakedFields()).containsExactly("keyPoints");
        }

        @Test
        @DisplayName("모든 학생용 글을 거르고, 지운 필드 이름을 돌려준다")
        void reportsLeakedFields() {
            AnswerLeakFilter.Outcome outcome = AnswerLeakFilter.sanitize(
                    result("정답은 3번.", "답은 3 인데 4 로 착각한다.", "정답 3 을 외우지 말고 이해하세요.", List.of("정답: 3")), "3");

            assertThat(outcome.leakedFields()).containsExactlyInAnyOrder("solution", "commonMistakes", "studyTips", "keyPoints");
            assertThat(outcome.result().getSubject()).isEqualTo("수학");
            assertThat(outcome.result().getProblemType()).isEqualTo("이차방정식");
        }
    }

    @Nested
    @DisplayName("지우지 않는 문장(오탐 방지)")
    class Keeps {

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {
                "3개의 공 중 2개를 고른다.",
                "3단계로 나눠 접근하세요.",
                "선지 ③ 의 조건을 다시 읽어 보세요.",
                "x 에 3 을 대입해 보세요."
        })
        @DisplayName("한 글자 숫자 답은 단정 표현 없이 단독으로 나와도 지우지 않는다")
        void shortAnswerWithoutAssertion(String sentence) {
            assertThat(solutionAfter(sentence, "3")).isEqualTo(sentence);
        }

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {
                "정답은 13번 문항과 비교해 보세요.",
                "답은 3.5 단위로 나눠 생각하세요.",
                "해답 풀이의 23 쪽을 보세요."
        })
        @DisplayName("숫자 답은 앞뒤가 다른 숫자로 이어지면 같은 답이 아니다")
        void numberBoundary(String sentence) {
            assertThat(solutionAfter(sentence, "3")).isEqualTo(sentence);
        }

        @Test
        @DisplayName("단정 표현만 있고 답이 없는 문장은 지우지 않는다")
        void assertionWithoutAnswer() {
            String sentence = "정답을 바로 보지 말고 조건부터 정리하세요.";
            assertThat(solutionAfter(sentence, "3")).isEqualTo(sentence);
        }

        @Test
        @DisplayName("두 글자 이하 글자 답(예: 산소)은 단정 표현이 있을 때만 지운다")
        void shortTextAnswer() {
            assertThat(solutionAfter("산소가 어디서 나오는지 보세요.", "산소")).isEqualTo("산소가 어디서 나오는지 보세요.");
            assertThat(solutionAfter("조건을 보세요. 정답은 산소입니다.", "산소")).isEqualTo("조건을 보세요.");
        }

        @Test
        @DisplayName("영문 답은 다른 낱말 안에 들어 있으면 같은 답이 아니다")
        void letterBoundary() {
            String sentence = "The answer is about the main idea.";
            assertThat(solutionAfter(sentence, "a")).isEqualTo(sentence);
        }

        @Test
        @DisplayName("'대답은' 처럼 다른 낱말의 '답' 은 단정 표현이 아니다")
        void otherWordsEndingWithDap() {
            String sentence = "남자의 대답은 3 가지 중 하나입니다.";
            assertThat(solutionAfter(sentence, "3")).isEqualTo(sentence);
        }

        @Test
        @DisplayName("걸리는 문장이 없으면 같은 결과 객체를 그대로 돌려준다")
        void returnsSameResultWhenClean() {
            ProblemAnalysisResult original = result("조건을 정리하세요.");

            AnswerLeakFilter.Outcome outcome = AnswerLeakFilter.sanitize(original, "3");

            assertThat(outcome.result()).isSameAs(original);
            assertThat(outcome.leakedFields()).isEmpty();
        }

        @Test
        @DisplayName("답이 비었거나 결과가 null 이면 거르지 않는다")
        void noAnswer() {
            ProblemAnalysisResult original = result("정답은 3번입니다.");

            assertThat(AnswerLeakFilter.sanitize(original, null).result()).isSameAs(original);
            assertThat(AnswerLeakFilter.sanitize(original, "").result()).isSameAs(original);
            assertThat(AnswerLeakFilter.sanitize(null, "3").result()).isNull();
        }

        @Test
        @DisplayName("null 필드와 null keyPoints 는 그대로 둔다")
        void nullFields() {
            AnswerLeakFilter.Outcome outcome = AnswerLeakFilter.sanitize(result(null, null, "정답은 3.", null), "3");

            assertThat(outcome.result().getSolution()).isNull();
            assertThat(outcome.result().getCommonMistakes()).isNull();
            assertThat(outcome.result().getKeyPoints()).isNull();
            assertThat(outcome.result().getStudyTips()).isEqualTo(AnswerLeakFilter.FALLBACK_STUDY_TIPS);
        }
    }

    @Nested
    @DisplayName("문장 나누기")
    class SentenceSplit {

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', value = {
                "첫 문장. 둘째 문장! 셋째?|3",
                "값은 0.5 이다. 다음|2",
                "1. 조건을 정리한다|1",
                "줄1\\n줄2\\n\\n줄3|3",
                "끝 문장 없음|1"
        })
        @DisplayName("문장 끝 기호 뒤 공백, 줄바꿈에서 나누고 소수점과 목록 번호에서는 나누지 않는다")
        void splits(String text, int expected) {
            String actual = text.replace("\\n", "\n");
            List<String> sentences = AnswerLeakFilter.splitSentences(actual);

            assertThat(sentences).hasSize(expected);
            assertThat(String.join("", sentences)).as("나눈 조각을 이으면 원문이다").isEqualTo(actual);
        }
    }
}
