package com.aisip.OnO.backend.util.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 분석 글의 역슬래시 표기 정리. 앱은 분석 글을 일반 Text 로 그리므로 화면에 보일 글자만 남는지 본다.
 * 입력 문자열의 역슬래시는 모델 응답을 JSON 으로 읽은 뒤의 값이다(자바 문자열 "\\n" 은 역슬래시와 n 두 글자).
 */
@DisplayName("분석 글 표기 정리")
class AnalysisTextNormalizerTest {

    private static String normalize(String text) {
        return AnalysisTextNormalizer.normalize(text);
    }

    @Nested
    @DisplayName("줄바꿈 두 글자")
    class EscapedNewline {

        @Test
        @DisplayName("dev 실호출 응답처럼 단계 사이의 역슬래시 n 을 줄바꿈으로 바꾼다")
        void devResponse() {
            String text = "1단계: 전체 경우의 수는 3의 5제곱이다.\\n2단계: 빈 상자가 생기는 경우를 뺀다.\\n3단계: 검산한다.";

            assertThat(normalize(text))
                    .isEqualTo("1단계: 전체 경우의 수는 3의 5제곱이다.\n2단계: 빈 상자가 생기는 경우를 뺀다.\n3단계: 검산한다.");
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', value = {
                "앞 문장.\\nHowever 뒤 문장.|앞 문장.\\nHowever 뒤 문장.",
                "앞 문장.\\r\\n뒤 문장.|앞 문장.\\n뒤 문장.",
                "앞 문장.\\\\n뒤 문장.|앞 문장.\\n뒤 문장.",
                "칸\\t나눔|칸 나눔"
        })
        @DisplayName("영어 낱말이 붙은 경우, CRLF, 두 번 이스케이프된 경우, 탭도 바꾼다")
        void variants(String input, String expected) {
            assertThat(normalize(input)).isEqualTo(expected.replace("\\n", "\n"));
        }

        @Test
        @DisplayName("이미 실제 줄바꿈이면 그대로 둔다")
        void realNewlineIsKept() {
            String text = "1단계: 조건 정리\n2단계: 검산";
            assertThat(normalize(text)).isSameAs(text);
        }
    }

    @Nested
    @DisplayName("LaTeX")
    class Latex {

        @Test
        @DisplayName("dev 실호출 응답의 \\( \\binom{5}{2} \\times \\binom{3}{1} \\times 2! \\) 를 일반 기호로 바꾼다")
        void devResponse() {
            String text = "\\( \\binom{5}{2} \\times \\binom{3}{1} \\times 2! \\) = 60가지 경우가 있다.";

            assertThat(normalize(text)).isEqualTo("5C2 × 3C1 × 2! = 60가지 경우가 있다.");
        }

        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource(delimiter = '|', value = {
                "\\frac{3}{4}|3/4",
                "\\dfrac{a+b}{2}|(a+b)/2",
                "\\sqrt{2}|√2",
                "\\sqrt{x+1}|√(x+1)",
                "3^{5}|3⁵",
                "x^{n+1}|x^(n+1)",
                "{}_{5}\\mathrm{C}_{2}|5C2",
                "\\left( x \\right)|( x )",
                "a \\le b \\neq c|a ≤ b ≠ c",
                "\\pi r^2|π r²",
                "\\sin x + \\tan x|sin x + tan x",
                "\\text{넓이} = 6|넓이 = 6",
                "\\[ x = 1 \\]|x = 1",
                "$$x = 1$$|x = 1",
                "\\{1, 2\\}|{1, 2}"
        })
        @DisplayName("자주 나오는 명령을 앱에서 읽을 수 있는 기호로 바꾼다")
        void commands(String input, String expected) {
            assertThat(normalize(input)).isEqualTo(expected);
        }

        @Test
        @DisplayName("겹친 명령은 안쪽부터 바꾼다")
        void nested() {
            assertThat(normalize("\\frac{\\sqrt{3}}{2}")).isEqualTo("√3/2");
        }

        @Test
        @DisplayName("목록에 없는 명령은 역슬래시만 지우고, 짝이 없는 역슬래시도 남기지 않는다")
        void unknownCommandAndStrayBackslash() {
            assertThat(normalize("\\mathbb R 위의 함수")).isEqualTo("R 위의 함수");
            assertThat(normalize("\\foo 명령")).isEqualTo("foo 명령");
            assertThat(normalize("끝에 남은 \\")).isEqualTo("끝에 남은");
        }

        @Test
        @DisplayName("달러 하나는 금액 표기와 구분할 수 없어 그대로 둔다")
        void singleDollarIsKept() {
            String text = "The price is $5 and $10.";
            assertThat(normalize(text)).isSameAs(text);
        }

        @Test
        @DisplayName("내용이 있는 중괄호(집합 표기)는 지우지 않는다")
        void setNotationIsKept() {
            assertThat(normalize("A = {1, 2, 3} 이고 \\times 2")).isEqualTo("A = {1, 2, 3} 이고 × 2");
        }
    }

    @Nested
    @DisplayName("검증 지적 반영")
    class ReviewFollowUps {

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', value = {
                "1단계 정리\\n2단계\\ne 는 자연상수|1단계 정리\\n2단계\\ne 는 자연상수",
                "예를 들면\\n3단계\\neq. 처럼|예를 들면\\n3단계\\neq. 처럼",
                "For example:\\n1. first\\ne.g., the subject|For example:\\n1. first\\ne.g., the subject"
        })
        @DisplayName("글이 줄바꿈 이스케이프를 쓰면 \\ne, \\neq 처럼 기호와 겹치는 낱말도 줄바꿈으로 본다")
        void newlineBeforeSymbolName(String input, String expected) {
            assertThat(normalize(input)).isEqualTo(expected.replace("\\n", "\n"));
        }

        @Test
        @DisplayName("줄바꿈 이스케이프가 없는 글의 \\ne 는 기호로 본다")
        void symbolWithoutNewlineEscape() {
            assertThat(normalize("a \\ne b")).isEqualTo("a ≠ b");
        }

        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource(delimiter = '|', value = {
                "\\theta_{a} 와 \\tau_{i}|θ_a 와 τ_i",
                "\\sum_{i=1}^{n} a_{i}|Σ_(i=1)^n a_i",
                "\\max_{x} f(x)|max_x f(x)",
                "x_{1} + x_{2}|x1 + x2",
                "\\frac{\\theta_{a}}{2}|(θ_a)/2",
                "\\sqrt[3]{8}|3√8",
                "\\vec{v} \\cdot \\vec{w}|v · w",
                "\\overline{AB} 의 길이|AB 의 길이",
                "\\boxed{x}|x",
                "30^\\circ 와 30^{\\circ}|30° 와 30°",
                "f \\circ g|f ∘ g",
                "\\textit{italic}|italic"
        })
        @DisplayName("명령 이름 뒤의 첨자는 기호로 바꾼 뒤 풀고, 꾸밈 명령은 내용만 남긴다")
        void subscriptsAndDecorations(String input, String expected) {
            assertThat(normalize(input)).isEqualTo(expected);
        }

        @Test
        @DisplayName("줄 앞 들여쓰기는 그대로 두고 글자 뒤의 겹친 공백만 줄인다")
        void indentationIsKept() {
            assertThat(normalize("목록:\\n    - 항목  하나")).isEqualTo("목록:\n    - 항목 하나");
        }

        @Test
        @DisplayName("공집합 {} 은 지우지 않고, 첨자 앞의 빈 중괄호만 지운다")
        void emptySetIsKept() {
            assertThat(normalize("{}_{5}C_{2} 와 공집합 {} \\times 2")).isEqualTo("5C2 와 공집합 {} × 2");
        }

        @Test
        @DisplayName("깊게 겹친 분수도 끝까지 푼다")
        void deepNesting() {
            String text = "1";
            for (int i = 0; i < 10; i++) {
                text = "\\frac{" + text + "}{2}";
            }
            assertThat(normalize(text)).doesNotContain("frac", "{", "}");
        }

        @Test
        @DisplayName("표기만 있던 keyPoints 항목은 정리 후 빈 글이 되므로 뺀다")
        void blankKeyPointIsDropped() {
            ProblemAnalysisResult result = ProblemAnalysisResult.builder()
                    .subject("수학").keyPoints(Arrays.asList("\\n", "판별식", null)).build();

            assertThat(AnalysisTextNormalizer.normalize(result).getKeyPoints()).containsExactly("판별식", null);
        }

        @Test
        @DisplayName("내부 답도 같은 규칙으로 바꾸고, 정보 과목이면 그대로 둔다")
        void normalizesAnswer() {
            assertThat(AnalysisTextNormalizer.normalizeAnswer("수학", "\\frac{3}{4}")).isEqualTo("3/4");
            assertThat(AnalysisTextNormalizer.normalizeAnswer("수학", null)).isNull();
            assertThat(AnalysisTextNormalizer.normalizeAnswer(AnalysisTextNormalizer.CODE_SUBJECT, "\\n")).isEqualTo("\\n");
        }
    }

    @Nested
    @DisplayName("결과 전체")
    class WholeResult {

        private ProblemAnalysisResult result(String subject) {
            return ProblemAnalysisResult.builder()
                    .subject(subject)
                    .problemType("경우의 수\\n")
                    .keyPoints(Arrays.asList("\\binom{5}{2}", null, "포함배제"))
                    .solution("1단계\\n2단계")
                    .commonMistakes("\\( 3^5 \\) 를 놓친다")
                    .studyTips(null)
                    .build();
        }

        @Test
        @DisplayName("학생에게 보이는 모든 필드를 정리하고 null 은 그대로 둔다")
        void allVisibleFields() {
            ProblemAnalysisResult normalized = AnalysisTextNormalizer.normalize(result("수학"));

            assertThat(normalized.getSubject()).isEqualTo("수학");
            assertThat(normalized.getProblemType()).isEqualTo("경우의 수");
            assertThat(normalized.getKeyPoints()).containsExactly("5C2", null, "포함배제");
            assertThat(normalized.getSolution()).isEqualTo("1단계\n2단계");
            assertThat(normalized.getCommonMistakes()).isEqualTo("3⁵ 를 놓친다");
            assertThat(normalized.getStudyTips()).isNull();
        }

        @Test
        @DisplayName("정보 과목은 코드의 \\n 이 뜻을 가지므로 바꾸지 않는다")
        void codeSubjectIsKept() {
            ProblemAnalysisResult original = result(AnalysisTextNormalizer.CODE_SUBJECT);

            assertThat(AnalysisTextNormalizer.normalize(original)).isSameAs(original);
        }

        @Test
        @DisplayName("null 결과와 null keyPoints 를 견딘다")
        void nulls() {
            assertThat(AnalysisTextNormalizer.normalize((ProblemAnalysisResult) null)).isNull();
            ProblemAnalysisResult noKeyPoints = ProblemAnalysisResult.builder().subject("수학").solution("a\\nb").build();
            assertThat(AnalysisTextNormalizer.normalize(noKeyPoints).getKeyPoints()).isNull();
        }

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {"", "역슬래시 없는 글", "3^5 처럼 역슬래시 없는 지수"})
        @DisplayName("역슬래시, $$, 중괄호 지수와 첨자가 없는 글은 그대로 돌려준다")
        void untouched(String text) {
            assertThat(normalize(text)).isSameAs(text);
        }
    }
}
