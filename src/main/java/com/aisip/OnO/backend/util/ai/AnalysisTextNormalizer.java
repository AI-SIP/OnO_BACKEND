package com.aisip.OnO.backend.util.ai;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Map.entry;

/**
 * 학생에게 보이는 분석 글에서 역슬래시 표기를 앱이 그대로 보여 줄 수 있는 글자로 바꾼다.
 *
 * 앱은 분석 글을 일반 Text 로 그린다(수식 렌더링 없음). 프롬프트로 LaTeX 와 이스케이프를 막아도 모델이
 * 줄바꿈을 역슬래시와 n 두 글자로 쓰거나 \( \binom{5}{2} \) 같은 수식을 섞는 일이 있어, 저장 전에 한 번 더 바꾼다.
 * 서버에서 바꾸므로 이미 설치된 앱에도 그대로 적용된다.
 *
 * 바꾸는 것
 * - 역슬래시 n, r, t 두 글자: 줄바꿈, 지움, 공백. 글 안에 숫자나 한글이 붙은 줄바꿈("\n2단계")이 하나라도 있으면
 *   줄바꿈을 쓰는 글로 보고, \ne 나 \nu 처럼 기호 명령과 겹치는 낱말("\ne.g.", "\ne 는 자연상수")도 줄바꿈으로 본다
 * - 수식 구분 기호 \( \) \[ \] $$: 지움. 달러 하나($)는 금액 표기와 구분할 수 없어 두고, 정리 대상에서도 뺀다
 * - \frac{a}{b}, \binom{n}{k}, \sqrt{x}, \sqrt[3]{x}, \text{x}, \vec{v}: a/b, nCk, √x, 3√x, x, v
 * - ^{2}, ^2 처럼 숫자 지수: ², 숫자가 아닌 지수 ^{n+1}: ^(n+1). 숫자 아래 첨자 _{2}: 2, 그 밖은 _a, _(i=1)
 * - ^\circ: °
 * - \times, \le, \pi 같은 기호 명령: ×, ≤, π. 목록에 없는 명령(\sin, \log)은 역슬래시만 지운다
 *
 * 정보 과목은 바꾸지 않는다. 코드 문제의 "\n" 은 줄바꿈 문자를 가리키는 글자라 바꾸면 뜻이 달라진다.
 */
final class AnalysisTextNormalizer {

    /** 바꾸지 않는 과목. OpenAIClient.SUBJECTS 의 값이다 */
    static final String CODE_SUBJECT = "정보";

    /** 인자를 받는 명령은 안쪽부터 바꾼다. 중괄호가 겹친 깊이만큼 되풀이하되, 비정상 입력에서 끝나지 않는 일이 없게 횟수를 둔다 */
    private static final int MAX_NESTING = 12;

    private static final Pattern MATH_DELIMITER = Pattern.compile("\\\\[()\\[\\]]|\\$\\$");
    private static final Pattern DOUBLE_BACKSLASH_BEFORE_LETTER = Pattern.compile("\\\\\\\\(?=[A-Za-z])");
    /** 뒤에 영문자가 오지 않는 역슬래시 n. 기호 명령과 헷갈릴 수 없는 줄바꿈이다 */
    private static final Pattern UNAMBIGUOUS_NEWLINE = Pattern.compile("\\\\n(?![A-Za-z])");
    private static final Pattern DEGREE = Pattern.compile("\\^\\s*(?:\\{\\s*\\\\circ\\s*}|\\\\circ(?![A-Za-z]))");
    private static final Pattern EMPTY_BRACE_BEFORE_SCRIPT = Pattern.compile("\\{}(?=[_^])");
    private static final Pattern FRACTION = Pattern.compile("\\\\[dt]?frac\\s*\\{([^{}]*)}\\s*\\{([^{}]*)}");
    private static final Pattern BINOMIAL = Pattern.compile("\\\\binom\\s*\\{([^{}]*)}\\s*\\{([^{}]*)}");
    private static final Pattern NTH_ROOT = Pattern.compile("\\\\sqrt\\s*\\[([^\\]]*)]\\s*\\{([^{}]*)}");
    private static final Pattern SQUARE_ROOT = Pattern.compile("\\\\sqrt\\s*\\{([^{}]*)}");
    /** 인자 하나를 꾸미기만 하는 명령. 앱에서는 꾸밈을 보일 수 없어 내용만 남긴다 */
    private static final Pattern DECORATION = Pattern.compile(
            "\\\\(" + String.join("|", decorationNames()) + ")\\s*\\{([^{}]*)}");
    /** 명령 이름 바로 뒤의 지수와 첨자(\theta_{a})는 명령을 기호로 바꾼 뒤에 푼다. 먼저 풀면 이름에 붙어 thetaa 가 된다 */
    private static final Pattern BRACED_SUPERSCRIPT = Pattern.compile("(?<!\\\\[A-Za-z]{1,20})\\^\\{([^{}]*)}");
    private static final Pattern BRACED_SUBSCRIPT = Pattern.compile("(?<!\\\\[A-Za-z]{1,20})_\\{([^{}]*)}");
    private static final Pattern DIGIT_SUPERSCRIPT = Pattern.compile("\\^([0-9])(?![0-9])");
    private static final Pattern COMMAND = Pattern.compile("\\\\([A-Za-z]+)");
    private static final Pattern ESCAPED_SYMBOL = Pattern.compile("\\\\([{}%&#_,;:! \\\\])");
    /** 줄 앞 들여쓰기는 두고, 글자 뒤에 겹친 공백만 하나로 줄인다 */
    private static final Pattern REPEATED_SPACES = Pattern.compile("(?<=\\S)[ \\t]{2,}");
    private static final Pattern SPACE_BEFORE_NEWLINE = Pattern.compile("[ \\t]+\\n");

    private static final String SUPERSCRIPT_DIGITS = "⁰¹²³⁴⁵⁶⁷⁸⁹";

    /** 중괄호 인자를 받는 명령. 첫 번째 명령 변환에서 건드리지 않고 인자를 푼 뒤에 처리한다 */
    private static final Set<String> ARGUMENT_COMMANDS = Set.of("frac", "dfrac", "tfrac", "binom", "sqrt");

    /** 기호로 바꾸는 명령. 지우는 명령(\left, \right 등)은 빈 문자열이다 */
    private static final Map<String, String> SYMBOLS = Map.ofEntries(
            entry("times", "×"), entry("cdot", "·"), entry("div", "÷"), entry("pm", "±"), entry("mp", "∓"),
            entry("le", "≤"), entry("leq", "≤"), entry("ge", "≥"), entry("geq", "≥"),
            entry("ne", "≠"), entry("neq", "≠"), entry("approx", "≈"), entry("equiv", "≡"),
            entry("lt", "<"), entry("gt", ">"), entry("infty", "∞"), entry("sim", "~"),
            entry("pi", "π"), entry("theta", "θ"), entry("alpha", "α"), entry("beta", "β"), entry("gamma", "γ"),
            entry("delta", "δ"), entry("Delta", "Δ"), entry("sigma", "σ"), entry("Sigma", "Σ"),
            entry("lambda", "λ"), entry("mu", "μ"), entry("omega", "ω"), entry("phi", "φ"),
            entry("to", "→"), entry("rightarrow", "→"), entry("Rightarrow", "⇒"), entry("leftarrow", "←"),
            entry("Leftrightarrow", "⇔"), entry("therefore", "∴"), entry("because", "∵"),
            entry("circ", "∘"), entry("degree", "°"), entry("angle", "∠"), entry("triangle", "△"),
            entry("perp", "⊥"), entry("parallel", "∥"), entry("in", "∈"), entry("notin", "∉"),
            entry("subset", "⊂"), entry("subseteq", "⊆"), entry("cup", "∪"), entry("cap", "∩"),
            entry("emptyset", "∅"), entry("ldots", "…"), entry("cdots", "…"), entry("dots", "…"),
            entry("nu", "ν"), entry("tau", "τ"), entry("rho", "ρ"), entry("nabla", "∇"), entry("neg", "¬"),
            entry("sum", "Σ"), entry("prod", "Π"), entry("int", "∫"), entry("langle", "⟨"), entry("rangle", "⟩"),
            entry("not", "not"), entry("top", "⊤"),
            // n, r, t 로 시작하는 함수 이름은 아래 command 의 이스케이프 판단보다 먼저 이름 그대로 둔다
            entry("tan", "tan"), entry("ln", "ln"), entry("log", "log"), entry("lim", "lim"),
            entry("sin", "sin"), entry("cos", "cos"), entry("exp", "exp"), entry("max", "max"), entry("min", "min"),
            entry("left", ""), entry("right", ""), entry("displaystyle", ""), entry("quad", " "), entry("qquad", " "),
            entry("newline", "\n"));

    private AnalysisTextNormalizer() {
    }

    private static List<String> decorationNames() {
        return List.of("text", "textbf", "textit", "textrm", "mathrm", "mathbf", "mathit", "mathbb", "mathcal",
                "operatorname", "boldsymbol", "overline", "underline", "vec", "hat", "bar", "boxed", "ce");
    }

    static ProblemAnalysisResult normalize(ProblemAnalysisResult result) {
        if (result == null || CODE_SUBJECT.equals(result.getSubject())) {
            return result;
        }
        return ProblemAnalysisResult.builder()
                .subject(result.getSubject())
                .problemType(normalize(result.getProblemType()))
                .keyPoints(normalizeKeyPoints(result.getKeyPoints()))
                .solution(normalize(result.getSolution()))
                .commonMistakes(normalize(result.getCommonMistakes()))
                .studyTips(normalize(result.getStudyTips()))
                .build();
    }

    /**
     * 정답 검사에 쓰는 내부 답도 학생용 글과 같은 규칙으로 바꾼다. 글만 바꾸면 답 \frac{3}{4} 와 글의 3/4 가 달라져
     * "정답은 3/4 이다" 를 놓친다. 정보 과목은 글을 바꾸지 않으므로 답도 그대로 둔다.
     */
    static String normalizeAnswer(String subject, String answer) {
        return CODE_SUBJECT.equals(subject) ? answer : normalize(answer);
    }

    /** 표기만 있던 항목("\\n" 하나)은 정리하면 빈 글이 되므로 뺀다. null 항목은 정답 검사가 처리하므로 그대로 둔다 */
    private static List<String> normalizeKeyPoints(List<String> keyPoints) {
        if (keyPoints == null) {
            return null;
        }
        return keyPoints.stream()
                .map(keyPoint -> keyPoint == null ? null : normalize(keyPoint))
                .filter(keyPoint -> keyPoint == null || !keyPoint.isBlank())
                .toList();
    }

    static String normalize(String text) {
        if (text == null || !looksLikeMarkup(text)) {
            return text;
        }
        String normalized = MATH_DELIMITER.matcher(text).replaceAll("");
        // JSON 을 두 번 이스케이프해 역슬래시가 둘인 "\\n", "\\frac" 도 하나로 줄여 같은 규칙으로 바꾼다
        normalized = DOUBLE_BACKSLASH_BEFORE_LETTER.matcher(normalized).replaceAll(Matcher.quoteReplacement("\\"));
        boolean usesNewlineEscape = UNAMBIGUOUS_NEWLINE.matcher(normalized).find();
        normalized = DEGREE.matcher(normalized).replaceAll("°");
        // {}_5C_2 처럼 첨자 앞에 붙은 빈 중괄호만 지운다. 공집합 {} 은 그대로 둔다
        normalized = EMPTY_BRACE_BEFORE_SCRIPT.matcher(normalized).replaceAll("");
        normalized = resolveArguments(normalized);
        normalized = replace(COMMAND, normalized, m -> ARGUMENT_COMMANDS.contains(m.group(1))
                ? m.group()
                : command(m.group(1), usesNewlineEscape));
        normalized = resolveArguments(normalized);
        normalized = replace(DIGIT_SUPERSCRIPT, normalized, m -> superscript(m.group(1)));
        // 인자 짝이 맞지 않아 남은 \frac 같은 명령은 이름만 남긴다
        normalized = replace(COMMAND, normalized, m -> command(m.group(1), usesNewlineEscape));
        normalized = replace(ESCAPED_SYMBOL, normalized, m -> escapedSymbol(m.group(1)));
        // 위에서 짝을 찾지 못하고 남은 역슬래시는 앱에 그대로 보이기만 하므로 지운다
        normalized = normalized.replace("\\", "");
        normalized = REPEATED_SPACES.matcher(normalized).replaceAll(" ");
        normalized = SPACE_BEFORE_NEWLINE.matcher(normalized).replaceAll("\n");
        return normalized.strip();
    }

    private static String resolveArguments(String text) {
        String resolved = text;
        for (int i = 0; i < MAX_NESTING; i++) {
            String before = resolved;
            resolved = replace(FRACTION, resolved, m -> group(m.group(1)) + "/" + group(m.group(2)));
            resolved = replace(BINOMIAL, resolved, m -> m.group(1).strip() + "C" + m.group(2).strip());
            resolved = replace(NTH_ROOT, resolved, m -> m.group(1).strip() + "√" + group(m.group(2)));
            resolved = replace(SQUARE_ROOT, resolved, m -> "√" + group(m.group(1)));
            resolved = replace(DECORATION, resolved, m -> m.group(2));
            resolved = replace(BRACED_SUPERSCRIPT, resolved, m -> superscript(m.group(1)));
            resolved = replace(BRACED_SUBSCRIPT, resolved, m -> subscript(m.group(1)));
            if (resolved.equals(before)) {
                break;
            }
        }
        return resolved;
    }

    /** 역슬래시, $$, 중괄호 지수나 첨자가 없으면 손대지 않는다. 일반 글의 "3^5" 는 앱에서도 읽히므로 그대로 둔다 */
    private static boolean looksLikeMarkup(String text) {
        return text.indexOf('\\') >= 0 || text.contains("$$") || text.contains("^{") || text.contains("_{");
    }

    private static String replace(Pattern pattern, String text, Function<MatchResult, String> replacement) {
        return pattern.matcher(text).replaceAll(m -> Matcher.quoteReplacement(Objects.requireNonNull(replacement.apply(m))));
    }

    /** 분수의 분자나 분모, 루트 안이 한 덩어리가 아니면 괄호로 묶는다(\frac{a+b}{2} 를 a+b/2 로 읽지 않게) */
    private static String group(String expression) {
        String stripped = expression.strip();
        boolean simple = stripped.chars().allMatch(c -> Character.isLetterOrDigit(c) || c == '.' || c == '√'
                || SUPERSCRIPT_DIGITS.indexOf(c) >= 0);
        return simple ? stripped : "(" + stripped + ")";
    }

    private static String superscript(String exponent) {
        String stripped = exponent.strip();
        if (isDigits(stripped)) {
            StringBuilder digits = new StringBuilder(stripped.length());
            for (char c : stripped.toCharArray()) {
                digits.append(SUPERSCRIPT_DIGITS.charAt(c - '0'));
            }
            return digits.toString();
        }
        return stripped.length() == 1 ? "^" + stripped : "^(" + stripped + ")";
    }

    /** 숫자 첨자는 그대로 붙인다(5C2, x1). 문자 첨자는 앞 글자와 붙어 다른 낱말로 읽히지 않게 _ 를 남긴다(θ_a, max_x) */
    private static String subscript(String index) {
        String stripped = index.strip();
        if (isDigits(stripped)) {
            return stripped;
        }
        return stripped.length() == 1 ? "_" + stripped : "_(" + stripped + ")";
    }

    private static boolean isDigits(String text) {
        return !text.isEmpty() && text.chars().allMatch(c -> c >= '0' && c <= '9');
    }

    /**
     * 역슬래시 뒤 낱말을 바꾼다. 줄바꿈은 역슬래시와 n 이 다음 낱말에 붙어 나오므로("\n2단계", "\nHowever")
     * 목록에 없는 n, r, t 로 시작하는 낱말은 이스케이프 문자와 나머지 글자로 본다.
     * 글이 줄바꿈 이스케이프를 쓰고 있으면 \ne, \nu 처럼 목록에 있는 n 낱말도 줄바꿈으로 본다("\ne.g.", "\ne 는 자연상수").
     */
    private static String command(String name, boolean usesNewlineEscape) {
        if (usesNewlineEscape && name.charAt(0) == 'n' && !name.equals("newline")) {
            return "\n" + name.substring(1);
        }
        String symbol = SYMBOLS.get(name);
        if (symbol != null) {
            return symbol;
        }
        if (decorationNames().contains(name) || ARGUMENT_COMMANDS.contains(name)) {
            return "";
        }
        return switch (name.charAt(0)) {
            case 'n' -> "\n" + name.substring(1);
            case 'r' -> name.substring(1);
            case 't' -> " " + name.substring(1);
            default -> name;
        };
    }

    private static String escapedSymbol(String symbol) {
        return switch (symbol) {
            case "\\" -> "\n";
            case ",", ";", ":", "!", " " -> " ";
            default -> symbol;
        };
    }
}
