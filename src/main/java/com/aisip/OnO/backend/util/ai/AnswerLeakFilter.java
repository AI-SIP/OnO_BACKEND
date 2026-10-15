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
 * - 단정 표현(정답, 답은, 따라서 답, 구하는 값은 등)과 답이 한 문장에 함께 있으면 지운다.
 * - 답이 숫자나 선지 번호(3, ③, 3번, -1.5)이거나 공백을 빼고 3글자보다 짧으면 단정 표현이 있을 때만 지운다.
 *   "3" 은 조건 정리("3개의 공")나 단계 번호에도 흔히 나와 단독 일치로 지우면 멀쩡한 문장이 사라진다.
 * - 그보다 긴 글자 답(예: "광합성", "x = 2")은 단정 표현이 없어도 문장에 나오면 지운다. 우연히 겹칠 일이 드물다.
 * - 숫자 답은 앞뒤가 다른 숫자로 이어지면 같은 답으로 보지 않는다("3" 은 "13", "3.5" 와 일치하지 않는다).
 * - 지운 뒤 필드가 비면 원문 대신 무난한 안내 문장을 둔다. 원문을 되살리면 걸러 낸 의미가 없다.
 */
final class AnswerLeakFilter {

    static final String FALLBACK_SOLUTION = "이 문제는 단계별로 접근해 보세요. 먼저 주어진 조건과 구해야 하는 것을 정리해 보세요.";
    static final String FALLBACK_COMMON_MISTAKES = "조건을 끝까지 읽었는지, 개념을 맞게 적용했는지 다시 확인해 보세요.";
    static final String FALLBACK_STUDY_TIPS = "관련 개념을 다시 정리한 뒤 비슷한 문제를 풀어 보세요.";
    static final String FALLBACK_KEY_POINT = "주어진 조건과 구하는 것 정리하기";

    /** 단정 표현 없이 단독 일치만으로 지울 수 있는 글자 답의 최소 길이(공백 제외) */
    static final int MIN_STANDALONE_LENGTH = 3;

    /**
     * 답을 단정하는 표현. "대답은" 처럼 다른 낱말의 끝인 "답" 은 제외한다.
     * 영어 문제 해설이 영어로 나오는 경우를 위해 answer is 도 둔다.
     */
    private static final Pattern ASSERTION = Pattern.compile(
            "정답|해답|최종\\s*답|따라서\\s*답|(?<![가-힣])답\\s*(?:은|는|이|:|=)"
                    + "|구하(?:는|려는)\\s*값\\s*(?:은|는|이)|answer\\s*(?:is|:)",
            Pattern.CASE_INSENSITIVE);

    /** 숫자 답과 선지 번호. 이런 답은 단정 표현이 있을 때만 지운다 */
    private static final Pattern NUMERIC = Pattern.compile("[-+−]?[0-9]+(?:[.,/][0-9]+)*\\s*번?|[①-⑳]");
    private static final Pattern CHOICE = Pattern.compile("([1-9])\\s*번?|([①-⑨])");
    private static final Pattern TRAILING_PUNCTUATION = Pattern.compile("[.。]+$");
    private static final Pattern LIST_NUMBER = Pattern.compile("[0-9]{1,2}\\.");

    private AnswerLeakFilter() {
    }

    /** 걸러 낸 결과와 문장을 지운 필드 이름 */
    record Outcome(ProblemAnalysisResult result, List<String> leakedFields) {
    }

    static Outcome sanitize(ProblemAnalysisResult result, String finalAnswer) {
        List<String> tokens = answerTokens(finalAnswer);
        if (result == null || tokens.isEmpty()) {
            return new Outcome(result, List.of());
        }
        List<String> leakedFields = new ArrayList<>();

        String solution = filterField("solution", result.getSolution(), tokens, FALLBACK_SOLUTION, leakedFields);
        String commonMistakes = filterField("commonMistakes", result.getCommonMistakes(), tokens,
                FALLBACK_COMMON_MISTAKES, leakedFields);
        String studyTips = filterField("studyTips", result.getStudyTips(), tokens, FALLBACK_STUDY_TIPS, leakedFields);
        List<String> keyPoints = filterKeyPoints(result.getKeyPoints(), tokens, leakedFields);

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

    private static String filterField(String field, String text, List<String> tokens, String fallback,
                                      List<String> leakedFields) {
        Filtered filtered = filterText(text, tokens);
        if (!filtered.removed()) {
            return text;
        }
        leakedFields.add(field);
        return filtered.text().isBlank() ? fallback : filtered.text();
    }

    private static List<String> filterKeyPoints(List<String> keyPoints, List<String> tokens, List<String> leakedFields) {
        if (keyPoints == null || keyPoints.isEmpty()) {
            return keyPoints;
        }
        List<String> kept = new ArrayList<>(keyPoints.size());
        boolean removed = false;
        for (String keyPoint : keyPoints) {
            Filtered filtered = filterText(keyPoint, tokens);
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

    private static Filtered filterText(String text, List<String> tokens) {
        if (text == null || text.isBlank()) {
            return new Filtered(text, false);
        }
        StringBuilder kept = new StringBuilder(text.length());
        boolean removed = false;
        for (String sentence : splitSentences(text)) {
            if (leaksAnswer(sentence, tokens)) {
                removed = true;
            } else {
                kept.append(sentence);
            }
        }
        return removed ? new Filtered(kept.toString().strip(), true) : new Filtered(text, false);
    }

    static boolean leaksAnswer(String sentence, List<String> tokens) {
        boolean asserts = ASSERTION.matcher(sentence).find();
        for (String token : tokens) {
            if (containsToken(sentence, token) && (asserts || isStandalone(token))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isStandalone(String token) {
        if (NUMERIC.matcher(token).matches()) {
            return false;
        }
        String compact = token.replaceAll("\\s", "");
        return compact.codePointCount(0, compact.length()) >= MIN_STANDALONE_LENGTH;
    }

    /**
     * 답 표기의 공백은 있어도 없어도 같게 본다("x = 2" 와 "x=2"). 숫자로 시작하거나 끝나는 답은 앞뒤가 숫자로 이어지면,
     * 영문자로 시작하거나 끝나는 답은 앞뒤가 영문자로 이어지면 다른 낱말로 본다.
     */
    static boolean containsToken(String sentence, String token) {
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
            regex.append("(?![0-9]|\\.[0-9])");
        } else if (isAsciiLetter(last)) {
            regex.append("(?![A-Za-z])");
        }
        return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE).matcher(sentence).find();
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

    private static boolean isAsciiDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isAsciiLetter(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
    }
}
