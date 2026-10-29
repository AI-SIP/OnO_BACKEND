package com.aisip.OnO.backend.util.ai;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 학생에게 보이는 분석 글에서 최종 답을 밝히는 문장을 지운다.
 *
 * 정답은 보여 주지 않기로 했다(2026-10-15). 프롬프트로 막아도 모델이 "따라서 정답은 ③이다" 같은 문장을 쓸 수 있어,
 * 모델이 내부 필드(finalAnswer)에 적은 답을 기준으로 저장 전에 한 번 더 거른다.
 *
 * 판단 규칙
 * - 문장 단위로 본다. 문장 일부만 잘라 내면 앞뒤가 어색한 글이 남기 때문이다.
 * - 단정 표현(정답은, 답은, 따라서 답, 구하는 값은, 옳은 것은 등)과 답이 한 문장에 함께 있을 때만 지운다.
 *   "정답", "해답" 은 조사나 기호가 붙은 단정 형태(정답은, 정답:)이거나 바로 뒤에 답이 올 때(정답 ③)만 단정으로 본다.
 *   "정답을 찾기 위해 3단계로" 처럼 낱말만 나오는 문장까지 지우면 풀이 안내가 사라진다.
 *   선지 번호는 단정 표현 없이 나오면 남긴다. 숫자 "3" 은 조건 정리("3개의 공")에도 흔히 나오기 때문이다.
 *   답의 값은 아래 규칙으로 따로 본다.
 * - 숫자 답은 앞뒤가 다른 숫자로 이어지면 같은 답으로 보지 않는다("3" 은 "13", "3.5" 와 일치하지 않는다).
 * - 영문 한 글자 답(A~E 등)은 대소문자를 구분한다. 구분하지 않으면 관사 "a" 와 일치한다.
 * - 한글 한 글자 답(가~하, ㄱ~ㅎ 선지)은 앞에 한글이 붙어 있으면 다른 낱말의 글자로 본다("봅니다" 의 "다").
 *   뒤에는 조사나 서술격 조사(이, 가, 은, 는, 입니다, 번 등)만 붙을 수 있다("다음" 의 "다" 는 답이 아니다).
 * - 답의 값(answerValue)은 단정 표현이 없어도 지운다. 2026-10-29 dev 실호출 20건 중 9건이 "25 - 12 = 13입니다",
 *   "대동법은 광해군 때 처음 시행되었다", "x > 4를 구한다" 처럼 단정 표현 없이 답을 보여 줬다.
 *   - 값이 낱말이나 식(광해군, careful, x > 4, 1/6, 1 mol)이면 어느 문장에 나오든 지운다. 이런 값이 분석 글에
 *     나오면 그 문장은 답을 알려 주고 있었다.
 *   - 값이 숫자뿐이면 등식의 끝("= 13"), 문장의 끝("13입니다"), 결론 서술("0으로 간단화한다", "4를 구한다")에 올 때만
 *     지운다. 숫자는 문제 조건("x² - 4x + k")이나 중간 값에도 나와서, 나오기만 해도 지우면 힌트가 사라진다.
 *   - 선지 번호 "3" 은 값이 아니라 중간 값("αβ = 3입니다")과 구별할 수 없어 쓰지 않고, ③ 과 3번은 문장 끝에 올 때만 지운다.
 * - 지운 뒤 필드가 비면 원문 대신 무난한 안내 문장을 둔다. 원문을 되살리면 걸러 낸 의미가 없다.
 */
final class AnswerLeakFilter {

    static final String FALLBACK_SOLUTION = "이 문제는 단계별로 접근해 보세요. 먼저 주어진 조건과 구해야 하는 것을 정리해 보세요.";
    static final String FALLBACK_COMMON_MISTAKES = "조건을 끝까지 읽었는지, 개념을 맞게 적용했는지 다시 확인해 보세요.";
    static final String FALLBACK_STUDY_TIPS = "관련 개념을 다시 정리한 뒤 비슷한 문제를 풀어 보세요.";
    static final String FALLBACK_KEY_POINT = "주어진 조건과 구하는 것 정리하기";

    /**
     * 답을 단정하는 표현. "대답은" 처럼 다른 낱말의 끝인 "답" 은 제외한다.
     * "정답", "해답" 은 조사나 기호가 붙은 형태만 본다("정답을 찾기 위해" 는 단정이 아니다). 바로 뒤에 답이 오는
     * "정답 3" 은 답에 따라 달라지므로 leaksAnswer 에서 따로 본다.
     * 객관식 해설의 결론 표현(옳은 것은, 적절하지 않은 것은 등)도 단정으로 본다. "값은" 단독은 조건 설명에도 흔해 넣지 않는다.
     * 다만 "옳은 것은 무엇인지" 처럼 바로 뒤에 의문사가 오면 결론이 아니라 질문이라 단정으로 보지 않는다.
     * "③이 옳다" 처럼 문장을 끝맺는 판단도 단정으로 본다. 문장 끝일 때만 보는 것은 "조건이 맞다면" 같은 가정을 빼기 위해서다.
     * "최종적으로", "결론적으로" 는 같은 문장이 "이다", "가 된다" 같은 결론으로 끝날 때만 단정으로 본다(#406).
     * "최종적으로 경우의 수는 10이다" 는 지우고, "최종적으로 3가지 조건을 확인해 보세요" 같은 안내는 남긴다.
     * 영어 문제 해설이 영어로 나오는 경우를 위해 answer is 도 둔다.
     */
    private static final Pattern ASSERTION = Pattern.compile(
            "(?:정답|해답)\\s*(?:은|는|이|인|:|=|으로|입니다)|(?:정답|해답)\\s*[.。!]?\\s*$"
                    + "|최종\\s*답|따라서\\s*답|(?<![가-힣])답\\s*(?:은|는|이|:|=)"
                    + "|(?:최종적으로|결론적으로)(?=[^.。!?\\n]*(?:이다|입니다|이에요|예요|[이가]\\s*(?:된다|됩니다)))"
                    + "|[이가]\\s*(?:옳다|옳습니다|맞다|맞습니다|적절하다|적절합니다)\\s*[.。!]?\\s*$"
                    + "|구하(?:는|려는)\\s*값\\s*(?:은|는|이)"
                    + "|(?:옳은|옳지\\s*않은|적절한|적절하지\\s*않은|알맞은|알맞지\\s*않은|바른|바르지\\s*않은|맞는|맞지\\s*않는)"
                    + "\\s*것\\s*(?:은|는|이|으로|:|=)(?!\\s*(?:무엇인|무엇일|무엇이|어느|어떤|몇))"
                    + "|answer\\s*(?:is|:)",
            Pattern.CASE_INSENSITIVE);

    /** "정답 ③", "해답: 3" 처럼 낱말 바로 뒤에 답이 오는 형태. 뒤에 답 표기를 붙여 쓴다 */
    private static final String ANSWER_NOUN_PREFIX = "(?:정답|해답)\\s*[:=]?\\s*";

    /** 선지 번호. 1~20 과 ①~⑳ 을 서로 바꿔 본다(유니코드 ①~⑳ 은 U+2460~U+2473 으로 이어져 있다) */
    private static final Pattern CHOICE = Pattern.compile("(1[0-9]|20|[1-9])\\s*번?|([①-⑳])");
    private static final Pattern TRAILING_PUNCTUATION = Pattern.compile("[.。]+$");
    private static final Pattern LIST_NUMBER = Pattern.compile("[0-9]{1,2}\\.");

    /** 등식의 끝. 값 뒤에 연산자가 이어지면 아직 계산 중인 식이라 끝으로 보지 않는다("= 13 - 2") */
    private static final String EQUATION_END_PREFIX = "=\\s*";
    private static final String EQUATION_END_SUFFIX = "(?!\\s*[-+−×÷*/^=<>±])";
    /**
     * 값으로 문장을 끝맺는 서술. "13입니다", "13이 됩니다", "13을 구한다", "0으로 간단화한다", "13.", 선지 기호는 "③번이다".
     * "2로 나누어" 처럼 값 뒤에 다른 동작이 오면 끝이 아니다.
     */
    private static final String SENTENCE_END_SUFFIX = "\\s*\\)?\\s*번?\\s*(?:이다|입니다|이에요|예요|이야|임|"
            + "(?:이|가|을|를|으로|로)\\s*(?:간단히\\s*)?(?:간단화|정리|계산|결정)?\\s*"
            + "(?:된다|됩니다|돼요|한다|합니다|해요|나온다|나옵니다|나와요|얻는다|얻습니다|얻어요|구한다|구합니다|구해요"
            + "|구할\\s*수\\s*있(?:다|습니다|어요)))?"
            + "\\s*[.。!]?\\s*$";
    /** 숫자뿐인 값. 이런 값은 조건과 중간 값에도 나와서 결론 자리에 올 때만 지운다 */
    private static final Pattern PLAIN_NUMBER = Pattern.compile("[-+−]?[0-9]+(?:\\.[0-9]+)?");

    /** 한글 한 글자 답 뒤에 붙을 수 있는 조사와 서술격 조사의 첫 글자. "다이다", "다입니다", "다인", "다에", "다라고", "다뿐" */
    private static final String KOREAN_PARTICLE_START = "[이입인임가은는을를와과로번예야의에라도뿐]";
    /** 자음 선지(ㄱ~ㅎ)는 "ㄱ만" 도 받는다. 음절 답에 넣으면 "다만" 이 걸려 자음에만 둔다 */
    private static final String JAMO_PARTICLE_START = "[이입인임가은는을를와과로번예야의에라도뿐만]";

    private AnswerLeakFilter() {
    }

    /** 걸러 낸 결과와 문장을 지운 필드 이름 */
    record Outcome(ProblemAnalysisResult result, List<String> leakedFields) {
    }

    static Outcome sanitize(ProblemAnalysisResult result, String finalAnswer) {
        return sanitize(result, finalAnswer, null);
    }

    /**
     * finalAnswer 는 객관식이면 선지 번호라 그 값("③ 13" 의 13)이 글에 나와도 알 수 없다. 그래서 정답의 값(answerValue)을
     * 따로 받아 답 표기에 더하고, 값이 결론처럼 놓인 문장도 지운다.
     */
    static Outcome sanitize(ProblemAnalysisResult result, String finalAnswer, String answerValue) {
        Answer answer = Answer.of(finalAnswer, answerValue);
        if (result == null || answer.tokens().isEmpty()) {
            return new Outcome(result, List.of());
        }
        List<String> leakedFields = new ArrayList<>();

        String solution = filterField("solution", result.getSolution(), answer, FALLBACK_SOLUTION, leakedFields);
        String commonMistakes = filterField("commonMistakes", result.getCommonMistakes(), answer,
                FALLBACK_COMMON_MISTAKES, leakedFields);
        String studyTips = filterField("studyTips", result.getStudyTips(), answer, FALLBACK_STUDY_TIPS, leakedFields);
        List<String> keyPoints = filterKeyPoints(result.getKeyPoints(), answer, leakedFields);

        if (leakedFields.isEmpty()) {
            return new Outcome(result, List.of());
        }
        ProblemAnalysisResult sanitized = ProblemAnalysisResult.builder()
                .subject(result.getSubject())
                .problemType(result.getProblemType())
                .keyPoints(keyPoints)
                .solution(solution)
                .commonMistakes(commonMistakes)
                .studyTips(studyTips)
                .build();
        return new Outcome(sanitized, List.copyOf(leakedFields));
    }

    /**
     * 답 표기 셋. tokens 는 단정 표현과 함께 나오면 지울 표기, values 는 결론 자리(등식 끝, 문장 끝)에 놓이면 지울 표기,
     * mentions 는 어디에 나오든 지울 표기다. values 에는 선지 기호(③, 3번)와 답의 값을 넣고, 숫자만 있는 선지 번호 "3" 은
     * "αβ = 3입니다" 같은 중간 값과 구별할 수 없어 넣지 않는다. mentions 에는 숫자뿐이 아닌 두 글자 이상의 값만 넣는다.
     */
    record Answer(List<String> tokens, List<String> values, List<String> mentions) {

        static Answer of(String finalAnswer, String answerValue) {
            Set<String> tokens = new LinkedHashSet<>(answerTokens(finalAnswer));
            Set<String> values = new LinkedHashSet<>();
            Set<String> mentions = new LinkedHashSet<>();
            boolean choice = isChoice(finalAnswer);
            for (String token : answerTokens(finalAnswer)) {
                if (!choice) {
                    values.add(token);
                    addIfWordOrExpression(mentions, token);
                } else if (!isAsciiDigit(token.charAt(token.length() - 1))) {
                    values.add(token);
                }
            }
            // 값은 선지 번호로 바꿔 보지 않는다. 값 13 을 ⑬, 13번으로 바꾸면 없는 선지를 찾게 된다
            for (String token : valueTokens(answerValue)) {
                tokens.add(token);
                values.add(token);
                addIfWordOrExpression(mentions, token);
            }
            return new Answer(List.copyOf(tokens), List.copyOf(values), List.copyOf(mentions));
        }

        /** 한 글자 값(가, A, ㄱ)은 선지 표기라 낱말로 보지 않는다 */
        private static void addIfWordOrExpression(Set<String> mentions, String token) {
            String stripped = token.strip();
            if (stripped.length() >= 2 && !PLAIN_NUMBER.matcher(stripped).matches()) {
                mentions.add(stripped);
            }
        }
    }

    private static boolean isChoice(String finalAnswer) {
        return finalAnswer != null
                && CHOICE.matcher(TRAILING_PUNCTUATION.matcher(finalAnswer.strip()).replaceAll("").strip()).matches();
    }

    /** 답의 값에서 비교할 표기. 값 그대로와, 등식이면 오른쪽 값 */
    static List<String> valueTokens(String answerValue) {
        if (answerValue == null) {
            return List.of();
        }
        String value = TRAILING_PUNCTUATION.matcher(answerValue.strip()).replaceAll("").strip();
        if (value.isEmpty()) {
            return List.of();
        }
        Set<String> tokens = new LinkedHashSet<>();
        tokens.add(value);
        int equals = value.lastIndexOf('=');
        if (equals >= 0 && !value.substring(equals + 1).isBlank()) {
            tokens.add(value.substring(equals + 1).strip());
        }
        return List.copyOf(tokens);
    }

    /**
     * 모델이 적은 최종 답에서 비교할 표기를 만든다. 선지 번호는 3, ③, 3번을 모두 같은 답으로 보고,
     * "x = 2" 처럼 등식이면 오른쪽 값도 답으로 본다.
     */
    static List<String> answerTokens(String finalAnswer) {
        if (finalAnswer == null) {
            return List.of();
        }
        String answer = TRAILING_PUNCTUATION.matcher(finalAnswer.strip()).replaceAll("").strip();
        if (answer.isEmpty()) {
            return List.of();
        }
        Set<String> tokens = new LinkedHashSet<>();
        tokens.add(answer);
        Matcher choice = CHOICE.matcher(answer);
        if (choice.matches()) {
            // 정규식이 1~20, ①~⑳ 만 받으므로 number 는 1~20 이고 '①' + number - 1 은 ①~⑳ 안에 든다
            int number = choice.group(1) != null
                    ? Integer.parseInt(choice.group(1))
                    : choice.group(2).charAt(0) - '①' + 1;
            tokens.add(String.valueOf((char) ('①' + number - 1)));
            tokens.add(number + "번");
            tokens.add(String.valueOf(number));
        }
        int equals = answer.lastIndexOf('=');
        if (equals >= 0) {
            String value = answer.substring(equals + 1).strip();
            if (!value.isEmpty()) {
                tokens.add(value);
            }
        }
        return List.copyOf(tokens);
    }

    private static String filterField(String field, String text, Answer answer, String fallback,
                                      List<String> leakedFields) {
        Filtered filtered = filterText(text, answer);
        if (!filtered.removed()) {
            return text;
        }
        leakedFields.add(field);
        return filtered.text().isBlank() ? fallback : filtered.text();
    }

    private static List<String> filterKeyPoints(List<String> keyPoints, Answer answer, List<String> leakedFields) {
        if (keyPoints == null || keyPoints.isEmpty()) {
            return keyPoints;
        }
        List<String> kept = new ArrayList<>(keyPoints.size());
        boolean removed = false;
        for (String keyPoint : keyPoints) {
            Filtered filtered = filterText(keyPoint, answer);
            if (!filtered.removed()) {
                kept.add(keyPoint);
                continue;
            }
            removed = true;
            if (!filtered.text().isBlank()) {
                kept.add(filtered.text());
            }
        }
        if (!removed) {
            return keyPoints;
        }
        leakedFields.add("keyPoints");
        return kept.isEmpty() ? List.of(FALLBACK_KEY_POINT) : kept;
    }

    private record Filtered(String text, boolean removed) {
    }

    private static Filtered filterText(String text, Answer answer) {
        if (text == null || text.isBlank()) {
            return new Filtered(text, false);
        }
        StringBuilder kept = new StringBuilder(text.length());
        boolean removed = false;
        for (String sentence : splitSentences(text)) {
            if (leaksAnswer(sentence, answer)) {
                removed = true;
            } else {
                kept.append(sentence);
            }
        }
        return removed ? new Filtered(kept.toString().strip(), true) : new Filtered(text, false);
    }

    static boolean leaksAnswer(String sentence, List<String> tokens) {
        return leaksAnswer(sentence, new Answer(tokens, List.of(), List.of()));
    }

    static boolean leaksAnswer(String sentence, Answer answer) {
        boolean asserted = ASSERTION.matcher(sentence).find();
        for (String token : answer.tokens()) {
            if (asserted ? containsToken(sentence, token) : matches(sentence, ANSWER_NOUN_PREFIX + tokenRegex(token), token)) {
                return true;
            }
        }
        for (String mention : answer.mentions()) {
            if (containsToken(sentence, mention)) {
                return true;
            }
        }
        for (String value : answer.values()) {
            String regex = tokenRegex(value);
            if (matches(sentence, EQUATION_END_PREFIX + regex + EQUATION_END_SUFFIX, value)
                    || matches(sentence, regex + SENTENCE_END_SUFFIX, value)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 답 표기의 공백은 있어도 없어도 같게 본다("x = 2" 와 "x=2"). 숫자로 시작하거나 끝나는 답은 앞뒤가 숫자로 이어지면,
     * 영문자로 시작하거나 끝나는 답은 앞뒤가 영문자로 이어지면 다른 낱말로 본다.
     * 영문 한 글자 답은 대소문자를 구분하고, 그 밖의 답은 구분하지 않는다.
     */
    static boolean containsToken(String sentence, String token) {
        return matches(sentence, tokenRegex(token), token);
    }

    private static boolean matches(String sentence, String regex, String token) {
        int flags = isSingleAsciiLetter(token) ? 0 : Pattern.CASE_INSENSITIVE;
        return Pattern.compile(regex, flags).matcher(sentence).find();
    }

    private static boolean isSingleAsciiLetter(String token) {
        String stripped = token.strip();
        return stripped.length() == 1 && isAsciiLetter(stripped.charAt(0));
    }

    private static String tokenRegex(String token) {
        String[] parts = token.strip().split("\\s+");
        StringBuilder regex = new StringBuilder();
        char first = token.strip().charAt(0);
        char last = token.strip().charAt(token.strip().length() - 1);
        if (isAsciiDigit(first)) {
            regex.append("(?<![0-9.])");
        } else if (isAsciiLetter(first)) {
            regex.append("(?<![A-Za-z])");
        }
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                regex.append("\\s*");
            }
            regex.append(Pattern.quote(parts[i]));
        }
        if (isAsciiDigit(last)) {
            // 프롬프트가 풀이를 "1단계, 2단계" 로 쓰게 해서, 답이 1~3 이면 "1단계: 답이 될 수 있는 경우를 나눠 보세요" 같은
            // 정상 힌트 줄이 지워졌다. 단계와 순서를 세는 말이 붙은 숫자는 답으로 보지 않는다.
            // "가지", "개" 는 개수를 묻는 문제의 답("정답은 2가지")일 수 있어 넣지 않는다.
            regex.append("(?![0-9]|\\.[0-9]|\\s*(?:단계|번째))");
        } else if (last == '번') {
            // 선지 표기 "3번" 이 "3번째" 안에서 일치하지 않게 한다
            regex.append("(?!째)");
        } else if (isAsciiLetter(last)) {
            regex.append("(?![A-Za-z])");
        } else if (isSingleKorean(token)) {
            regex.insert(0, "(?<![가-힣ㄱ-ㅎ])");
            char answer = token.strip().charAt(0);
            String particles = answer >= 'ㄱ' && answer <= 'ㅎ' ? JAMO_PARTICLE_START : KOREAN_PARTICLE_START;
            regex.append("(?:(?![가-힣ㄱ-ㅎ])|(?=").append(particles).append("))");
        }
        return regex.toString();
    }

    /**
     * 문장 끝(. ! ? 。 뒤에 공백이나 끝, 또는 줄바꿈)에서 나눈다. 뒤따르는 공백은 앞 문장에 붙여 원래 모양을 지킨다.
     * 소수점(0.5)은 뒤에 공백이 없어 나누지 않고, 줄 앞의 목록 번호("1. ")도 문장 끝으로 보지 않는다.
     */
    static List<String> splitSentences(String text) {
        List<String> sentences = new ArrayList<>();
        int length = text.length();
        int start = 0;
        int i = 0;
        while (i < length) {
            char c = text.charAt(i);
            boolean terminal = c == '.' || c == '!' || c == '?' || c == '。';
            boolean end = c == '\n'
                    || (terminal && (i + 1 == length || Character.isWhitespace(text.charAt(i + 1)))
                    && !(c == '.' && LIST_NUMBER.matcher(text.substring(start, i + 1).strip()).matches()));
            i++;
            if (end) {
                while (i < length && Character.isWhitespace(text.charAt(i))) {
                    i++;
                }
                sentences.add(text.substring(start, i));
                start = i;
            }
        }
        if (start < length) {
            sentences.add(text.substring(start));
        }
        return sentences;
    }

    private static boolean isSingleKorean(String token) {
        String stripped = token.strip();
        if (stripped.length() != 1) {
            return false;
        }
        char c = stripped.charAt(0);
        return (c >= '가' && c <= '힣') || (c >= 'ㄱ' && c <= 'ㅎ');
    }

    private static boolean isAsciiDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isAsciiLetter(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
    }
}
