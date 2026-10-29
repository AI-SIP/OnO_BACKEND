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
        @DisplayName("개념을 묻는 글자 답은 단정 표현이 없어도 그 개념을 짚는 문장을 지운다 (2026-10-29 정책 변경)")
        void conceptAnswerHintIsRemoved() {
            // 예전에는 개념을 짚는 문장을 힌트로 남겼지만, dev 실호출에서 "대동법은 광해군 때 처음 시행되었다" 처럼
            // 답을 그대로 보여 주는 문장이 남아 정답을 보여 주지 않는다는 원칙이 깨졌다. 글이 짧아지는 것보다 노출이 더 문제다
            String hint = "빛을 받는 곳을 보세요. 이 과정이 광합성과 어떻게 이어지는지 떠올려 보세요. 그래프를 해석하세요.";
            assertThat(solutionAfter(hint, "광합성")).isEqualTo("빛을 받는 곳을 보세요. 그래프를 해석하세요.");
        }

        @Test
        @DisplayName("글자 답도 단정 표현과 함께 나오면 지운다")
        void conceptAnswerWithAssertionIsRemoved() {
            assertThat(solutionAfter("빛을 받는 곳을 보세요. 따라서 답은 광합성이다. 그래프를 해석하세요.", "광합성"))
                    .isEqualTo("빛을 받는 곳을 보세요. 그래프를 해석하세요.");
        }

        @Test
        @DisplayName("답의 공백 차이는 같은 답으로 본다(x = 2 와 x=2)")
        void whitespaceInsensitive() {
            assertThat(solutionAfter("식을 정리하세요. 따라서 답은 x=2 이다.", "x = 2")).isEqualTo("식을 정리하세요.");
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
        @DisplayName("두 글자 글자 답(예: 산소)도 나오면 지운다. 한 글자 답만 선지 표기로 보고 남긴다")
        void shortTextAnswer() {
            assertThat(solutionAfter("조건을 보세요. 산소가 어디서 나오는지 보세요.", "산소")).isEqualTo("조건을 보세요.");
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
    @DisplayName("검증 지적 반영 - 단정 표현과 답 비교")
    class ReviewFollowUps {

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {
                "정답을 찾기 위해 3단계로 나눠 생각하세요.",
                "정답을 고르기 전에 선지 ③ 의 조건을 확인하세요.",
                "해답을 보기 전에 3가지 조건을 먼저 정리하세요.",
                "해답 풀이를 보며 3단계 전략을 비교하세요."
        })
        @DisplayName("C: 조사나 기호 없이 '정답', '해답' 낱말만 나오는 문장은 지우지 않는다")
        void bareAnswerNounIsNotAssertion(String sentence) {
            assertThat(solutionAfter(sentence, "3")).isEqualTo(sentence);
        }

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {
                "정답이 3번이다.",
                "③이 정답입니다.",
                "따라서 ③이 정답.",
                "정답 ③ 을 고르면 된다.",
                "해답은 3이다.",
                "해답: ③.",
                "정답으로 3번을 고른다.",
                "정답인 ③ 을 떠올리세요."
        })
        @DisplayName("C: 조사나 기호가 붙은 단정 형태, 낱말 바로 뒤의 답은 지운다")
        void assertedAnswerNounIsRemoved(String sentence) {
            assertThat(solutionAfter("조건을 정리하세요. " + sentence + " 부호를 확인하세요.", "3"))
                    .isEqualTo("조건을 정리하세요. 부호를 확인하세요.");
        }

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {
                "The answer is a noun phrase in the first sentence.",
                "The answer is about a change in the main idea.",
                "Answer: a key word appears in line 3."
        })
        @DisplayName("D: 영문 한 글자 답(A)은 대소문자를 구분해 관사 a 와 일치하지 않는다")
        void singleLetterAnswerIsCaseSensitive(String sentence) {
            assertThat(solutionAfter(sentence, "A")).isEqualTo(sentence);
        }

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {"The answer is A.", "정답은 (A) 이다.", "Answer: A."})
        @DisplayName("D: 영문 한 글자 답도 같은 대소문자로 단정되면 지운다")
        void singleLetterAnswerIsRemoved(String sentence) {
            assertThat(solutionAfter("Read the passage. " + sentence + " Check the tone.", "A"))
                    .isEqualTo("Read the passage. Check the tone.");
        }

        @Test
        @DisplayName("D: 두 글자 이상 영문 답은 지금처럼 대소문자를 구분하지 않는다")
        void multiLetterAnswerIsCaseInsensitive() {
            assertThat(solutionAfter("Read it. The answer is BECAUSE.", "because")).isEqualTo("Read it.");
        }

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {
                "따라서 옳은 것은 ③이다.",
                "옳지 않은 것은 3번이다.",
                "적절한 것은 ③.",
                "적절하지 않은 것은 ③입니다.",
                "알맞은 것은 3번이다.",
                "바른 것은 ③이다.",
                "올바른 것은 ③이다.",
                "맞는 것이 ③번이다.",
                "옳은 것: ③."
        })
        @DisplayName("E: 객관식 해설의 결론 표현과 답이 함께 있으면 지운다")
        void choiceConclusionIsRemoved(String sentence) {
            assertThat(solutionAfter("선지를 하나씩 보세요. " + sentence + " 근거를 확인하세요.", "3"))
                    .isEqualTo("선지를 하나씩 보세요. 근거를 확인하세요.");
        }

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {
                "옳은 것을 고르기 전에 선지 ③ 의 조건부터 확인하세요.",
                "적절한 것을 찾으려면 3가지 기준을 세우세요.",
                "값은 3 이상이어야 한다는 조건을 확인하세요.",
                "x 의 값은 3 보다 큰지 따져 보세요."
        })
        @DisplayName("E: 결론 표현이 아닌 문장, '값은' 단독은 답이 있어도 지우지 않는다")
        void nonConclusionIsKept(String sentence) {
            assertThat(solutionAfter(sentence, "3")).isEqualTo(sentence);
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource({"10,⑩", "12,⑫", "15번,⑮", "⑳,⑳", "⑪,⑪"})
        @DisplayName("F: 10 이상 선지 번호도 숫자, 원 숫자, 번 표기를 서로 바꿔 본다")
        void choiceNumbersUpToTwenty(String answer, String circled) {
            int number = circled.charAt(0) - '①' + 1;
            assertThat(AnswerLeakFilter.answerTokens(answer)).contains(circled, number + "번", String.valueOf(number));
        }

        @Test
        @DisplayName("F: 21 이상은 선지 번호로 보지 않아 원 숫자를 만들지 않는다")
        void choiceNumbersAboveTwenty() {
            assertThat(AnswerLeakFilter.answerTokens("21")).containsExactly("21");
            assertThat(AnswerLeakFilter.answerTokens("0")).containsExactly("0");
        }

        @Test
        @DisplayName("F: 답이 12 이면 '정답은 ⑫' 는 지우고 '정답은 ②' 나 '112쪽' 은 남긴다")
        void twoDigitChoiceRemoval() {
            assertThat(solutionAfter("조건을 보세요. 정답은 ⑫이다.", "12")).isEqualTo("조건을 보세요.");
            assertThat(solutionAfter("조건을 보세요. 정답은 12번이다.", "⑫")).isEqualTo("조건을 보세요.");
            String kept = "정답은 ②와 비교해 보세요. 해답은 112쪽에 있습니다.";
            assertThat(solutionAfter(kept, "12")).isEqualTo(kept);
        }
    }

    @Nested
    @DisplayName("#406 dev 실호출 반영 - 한글 한 글자 답, 결론 부사, 질문형 결론 표현")
    class DevCallFollowUps {

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {
                "가, 나 중에서 옳은 것은 무엇인지 조건과 비교해 봅니다.",
                "다음 중 옳은 것은 무엇인지 하나씩 따져 봅니다.",
                "따라서 다른 선지와 비교해 옳은 것을 고릅니다.",
                "옳은 것은 어느 선지인지 근거를 먼저 세웁니다."
        })
        @DisplayName("한글 한 글자 답(다)은 다른 낱말 안의 글자(봅니다, 다음, 다른)와 일치하지 않는다")
        void singleKoreanAnswerIsNotPartOfWord(String sentence) {
            assertThat(solutionAfter(sentence, "다")).isEqualTo(sentence);
        }

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {
                "따라서 옳은 것은 다이다.",
                "옳은 것은 다입니다.",
                "정답은 다.",
                "정답 다 를 고르면 된다.",
                "옳은 것은 (다) 이다."
        })
        @DisplayName("한글 한 글자 답도 단정 표현과 함께 단독으로 나오거나 조사가 붙으면 지운다")
        void singleKoreanAnswerWithAssertionIsRemoved(String sentence) {
            assertThat(solutionAfter("보기를 하나씩 보세요. " + sentence + " 근거를 확인하세요.", "다"))
                    .isEqualTo("보기를 하나씩 보세요. 근거를 확인하세요.");
        }

        @Test
        @DisplayName("자음 선지(ㄷ)도 같은 경계 규칙을 쓴다")
        void consonantChoice() {
            assertThat(solutionAfter("보기를 보세요. 옳은 것은 ㄷ이다.", "ㄷ")).isEqualTo("보기를 보세요.");
            String kept = "ㄱ, ㄴ 의 조건을 먼저 비교해 봅니다.";
            assertThat(solutionAfter(kept, "ㄷ")).isEqualTo(kept);
        }

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {
                "최종적으로 경우의 수는 150이다.",
                "결론적으로 150가지가 된다."
        })
        @DisplayName("'최종적으로', '결론적으로' 와 답이 함께 있으면 지운다")
        void concludingAdverbIsAssertion(String sentence) {
            assertThat(solutionAfter("식을 세우세요. " + sentence + " 검산하세요.", "150"))
                    .isEqualTo("식을 세우세요. 검산하세요.");
        }

        @ParameterizedTest(name = "\"{0}\" / {1}")
        @CsvSource(delimiter = '|', value = {
                "최종적으로 3가지 조건을 모두 만족하는지 확인해 보세요.|3",
                "결론적으로 2단계에서 세운 식을 정리해 보세요.|2",
                "결론적으로 가로 길이와 세로 길이를 비교해 보세요.|가",
                "최종적으로 두 값 사이의 관계를 확인하세요.|사",
                "최종적으로 조건에 따라 다를 수 있음을 확인하세요.|다"
        })
        @DisplayName("검증 지적: '최종적으로', '결론적으로' 뒤가 결론이 아닌 안내 문장이면 지우지 않는다")
        void concludingAdverbGuidanceIsKept(String sentence, String answer) {
            assertThat(solutionAfter(sentence, answer)).isEqualTo(sentence);
        }

        @ParameterizedTest(name = "\"{0}\" / {1}")
        @CsvSource(delimiter = '|', value = {
                "정답이 다인 이유를 생각해 보세요.|다",
                "옳은 것은 다에 해당한다.|다",
                "따라서 정답은 다라고 할 수 있다.|다",
                "옳은 것은 ㄱ만 해당한다.|ㄱ",
                "옳은 것은 ㄱ뿐이다.|ㄱ"
        })
        @DisplayName("검증 지적: 한글 한 글자 답 뒤의 인, 에, 라, 만(자음), 뿐도 조사로 본다")
        void moreKoreanParticles(String sentence, String answer) {
            assertThat(solutionAfter("보기를 보세요. " + sentence, answer)).isEqualTo("보기를 보세요.");
        }

        @Test
        @DisplayName("검증 지적: 음절 답 '다' 는 '다만' 과 일치하지 않는다")
        void daManIsKept() {
            String sentence = "옳은 것은 다만 조건을 다시 읽어야 보입니다.";
            assertThat(solutionAfter(sentence, "다")).isEqualTo(sentence);
        }

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {"따져 보면 ③이 옳다.", "그러므로 ③이 맞습니다.", "결국 3번이 적절하다."})
        @DisplayName("검증 지적: 문장을 끝맺는 '이 옳다', '이 맞습니다' 도 단정으로 본다")
        void sentenceFinalJudgement(String sentence) {
            assertThat(solutionAfter("선지를 보세요. " + sentence, "3")).isEqualTo("선지를 보세요.");
        }

        @Test
        @DisplayName("검증 지적: '조건이 맞다면' 같은 가정은 단정이 아니다")
        void conditionalIsKept() {
            String sentence = "조건이 맞다면 3 을 대입해 보세요.";
            assertThat(solutionAfter(sentence, "3")).isEqualTo(sentence);
        }

        @Test
        @DisplayName("검증 지적: '무엇보다' 는 의문사가 아니라 단정을 막지 않는다")
        void mueotbodaIsNotQuestion() {
            assertThat(solutionAfter("선지를 보세요. 옳은 것은 무엇보다 ③이다.", "3")).isEqualTo("선지를 보세요.");
        }

        @Test
        @DisplayName("'최종적으로' 가 있어도 답이 없으면 지우지 않는다")
        void concludingAdverbWithoutAnswer() {
            String sentence = "최종적으로 무엇을 구하는지 3단계에서 확인하세요.";
            assertThat(solutionAfter(sentence, "150")).isEqualTo(sentence);
        }

        @Test
        @DisplayName("'옳은 것은 무엇' 처럼 질문형이면 단정이 아니라 선지 번호가 있어도 남긴다")
        void questionFormIsKept() {
            String sentence = "선지 ③ 을 포함해 옳은 것은 무엇인지 기준을 세우세요.";
            assertThat(solutionAfter(sentence, "3")).isEqualTo(sentence);
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

    @Nested
    @DisplayName("배포 전 검토 반영 - 단계 번호")
    class StepNumbers {

        @ParameterizedTest(name = "{0} / 답 {1}")
        @CsvSource(delimiter = '|', value = {
                "1단계: 답이 될 수 있는 경우를 나눠 보세요.|1",
                "2 단계: 구하려는 값은 무엇인지 먼저 적어 보세요.|2",
                "답이 정해지는 조건을 3번째 줄에서 확인하세요.|3"
        })
        @DisplayName("단계나 순서를 세는 숫자는 단정 표현과 함께 있어도 답으로 보지 않는다")
        void stepNumberIsNotAnswer(String sentence, String answer) {
            assertThat(solutionAfter(sentence, answer)).isEqualTo(sentence);
        }

        @Test
        @DisplayName("같은 줄에 단계 번호와 답이 함께 있으면 여전히 지운다")
        void answerBesideStepNumberIsRemoved() {
            assertThat(solutionAfter("1단계: 조건 정리\n2단계: 따라서 답은 2이다.", "2"))
                    .isEqualTo("1단계: 조건 정리");
        }
    }

    @Nested
    @DisplayName("2026-10-29 dev 실호출 반영 - 단정 표현 없이 결론 자리에 놓인 답의 값")
    class AnswerValue {

        private String solutionAfter(String solution, String finalAnswer, String answerValue) {
            return AnswerLeakFilter.sanitize(result(solution), finalAnswer, answerValue).result().getSolution();
        }

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {
                "4단계: α² + β² = 25 - 12 = 13입니다.",
                "α² + β² = 13",
                "따라서 두 근의 제곱의 합은 13입니다.",
                "식을 계산하면 13이 나옵니다.",
                "정리하면 값은 13이 됩니다.",
                "5² - 2 × 6 = 13."
        })
        @DisplayName("객관식이라 선지 번호가 3 이어도, 값 13 이 등식이나 문장의 끝에 오면 지운다")
        void valueAtConclusionIsRemoved(String sentence) {
            assertThat(solutionAfter("3단계: 곱셈 공식을 떠올려 보세요.\n" + sentence, "3", "13"))
                    .isEqualTo("3단계: 곱셈 공식을 떠올려 보세요.");
        }

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {
                "α + β = 5, αβ = 6임을 알 수 있습니다.",
                "α² + β² = (α + β)² - 2αβ 로 바꿔 보세요.",
                "13개의 공 중에서 두 개를 고르는 경우를 생각합니다.",
                "x = 13 - 2k 로 놓고 범위를 확인하세요.",
                "αβ = 3입니다."
        })
        @DisplayName("값을 결론으로 쓰지 않은 문장과, 숫자뿐인 선지 번호(3)와 같은 중간 값은 남긴다")
        void nonConclusionIsKept(String sentence) {
            assertThat(solutionAfter(sentence, "3", "13")).isEqualTo(sentence);
        }

        @Test
        @DisplayName("값 13 을 ⑬, 13번 같은 선지 표기로 바꿔 찾지 않는다")
        void valueIsNotTreatedAsChoice() {
            String sentence = "정답을 고르기 전에 13번 문제와 비교해 보세요.";
            assertThat(solutionAfter(sentence, "3", "13")).isEqualTo(sentence);
        }

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {"그러므로 ③입니다.", "고를 것은 ③번이다."})
        @DisplayName("선지 기호(③, 3번)로 문장을 끝맺으면 단정 표현이 없어도 지운다")
        void choiceSymbolAtSentenceEndIsRemoved(String sentence) {
            assertThat(solutionAfter("선지를 비교하세요. " + sentence, "3", null)).isEqualTo("선지를 비교하세요.");
        }

        @Test
        @DisplayName("주관식은 answerValue 가 없어도(예전 형식) finalAnswer 를 값으로 본다")
        void subjectiveAnswerWithoutValue() {
            assertThat(solutionAfter("식을 세우세요.\n5C2 × 15 = 150", "150", null)).isEqualTo("식을 세우세요.");
        }

        @ParameterizedTest(name = "\"{0}\" / {1}")
        @CsvSource(delimiter = '|', value = {
                "대동법은 광해군 때 처음 시행되었다.|4|광해군",
                "3단계: 선지에서 광해군을 찾아 선택한다.|4|광해군",
                "3단계: 선택지 중 'careful'이 'cautious'와 가장 유사한 의미를 가집니다.|2|careful",
                "'Cautious' means being careful to avoid danger.|2|careful",
                "2단계: 양변을 2로 나누어 x > 4를 구한다.|3|x > 4",
                "3단계: 확률은 3/6이므로, 이를 약분하여 1/2로 표현한다.|3|1/2",
                "3단계: 18 g ÷ 18 g/mol = 1 mol이므로, 물의 몰수는 1 mol이다.|2|1 mol",
                "2단계: 훈민정음은 조선의 세종대왕이 창제한 것이다.|1|세종"
        })
        @DisplayName("dev 실호출: 값이 낱말이나 식이면 문장 어디에 나오든 지운다")
        void wordOrExpressionValueIsRemovedAnywhere(String sentence, String finalAnswer, String answerValue) {
            assertThat(solutionAfter("조건을 정리하세요.\n" + sentence, finalAnswer, answerValue))
                    .isEqualTo("조건을 정리하세요.");
        }

        @ParameterizedTest(name = "\"{0}\" / {1}")
        @CsvSource(delimiter = '|', value = {
                "4단계: 최종적으로 -1 + 1을 계산하여 0으로 간단화한다.|0",
                "4단계: Δ = 0을 만족하도록 16 - 4k = 0을 풀면 k = 4가 된다.|4",
                "f(1) = 1² - 2×1 + 3 = 2입니다.|2",
                "4단계: x가 두 수 중 큰 수이므로, 큰 수는 7입니다.|7"
        })
        @DisplayName("dev 실호출: 숫자 값이 결론 서술(간단화한다, 가 된다, 입니다)로 끝나면 지운다")
        void plainNumberAtConclusionIsRemoved(String sentence, String answerValue) {
            assertThat(solutionAfter("조건을 정리하세요.\n" + sentence, answerValue, answerValue))
                    .isEqualTo("조건을 정리하세요.");
        }

        @ParameterizedTest(name = "\"{0}\" / {1}")
        @CsvSource(delimiter = '|', value = {
                "어떤 수를 x라고 하면, x + 12 = 24라는 방정식을 세울 수 있습니다.|12",
                "2단계: 이 방정식을 풀기 위해 양변에서 12를 뺍니다.|12",
                "3단계: x = 24 - 12라는 식을 통해 x의 값을 구할 수 있습니다.|12",
                "1단계: 주어진 이차방정식은 x² - 4x + k = 0이다.|4",
                "여기서 a = 1, b = -4, c = k이다.|4",
                "1단계: 주어진 이차함수 f(x) = x² - 2x + 3에서 a = 1, b = -2, c = 3임을 확인합니다.|2",
                "2단계: 양변을 2로 나누어 범위를 정리합니다.|2"
        })
        @DisplayName("dev 실호출: 숫자 값이 문제 조건이나 중간 식에 나오는 문장은 남긴다")
        void plainNumberInConditionIsKept(String sentence, String answerValue) {
            assertThat(solutionAfter(sentence, answerValue, answerValue)).isEqualTo(sentence);
        }

        @Test
        @DisplayName("한 글자 값(가, A)은 선지 표기라 문장 안에 나온 것만으로 지우지 않는다")
        void singleCharacterValueIsNotWord() {
            String sentence = "A 와 B 의 조건을 비교해 보세요.";
            assertThat(solutionAfter(sentence, "A", "A")).isEqualTo(sentence);
        }

        @Test
        @DisplayName("keyPoints 에 값이 결론으로 적혀 있어도 지운다")
        void keyPointWithValue() {
            ProblemAnalysisResult original = result("식을 세우세요.", "c", "t",
                    List.of("근과 계수의 관계", "α² + β² = 13"));

            AnswerLeakFilter.Outcome outcome = AnswerLeakFilter.sanitize(original, "3", "13");

            assertThat(outcome.result().getKeyPoints()).containsExactly("근과 계수의 관계");
            assertThat(outcome.leakedFields()).containsExactly("keyPoints");
        }
    }
}
