package com.aisip.OnO.backend.util.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * AI 문제 분석에 넘기는 입력.
 *
 * 문제 이미지가 분석 대상이고, 해설 이미지와 제목, 메모는 모델이 문제를 맞게 풀고 이 학생에게 맞는 분석을 쓰도록 돕는 참고 자료다.
 * 제목과 메모는 학생이 직접 쓴 글이라 프롬프트에서 지시문과 분리해 넣는다(OpenAIClient).
 *
 * @param problemImageUrls 문제 이미지(PROBLEM_IMAGE) URL. 등록 순서대로
 * @param answerImageUrls  해설 이미지(ANSWER_IMAGE) URL. 등록 순서대로
 * @param reference        학생이 붙인 제목(Problem.reference). 없으면 null
 * @param memo             학생이 남긴 메모(Problem.memo). 없으면 null
 */
public record AnalysisInput(
        List<String> problemImageUrls,
        List<String> answerImageUrls,
        String reference,
        String memo
) {

    public AnalysisInput {
        problemImageUrls = unmodifiableCopy(problemImageUrls);
        answerImageUrls = unmodifiableCopy(answerImageUrls);
    }

    /** 문제 이미지만으로 분석할 때(다시 분석 테스트 경로, 예전 비동기 경로) */
    public static AnalysisInput ofProblemImages(List<String> problemImageUrls) {
        return new AnalysisInput(problemImageUrls, List.of(), null, null);
    }

    // List.copyOf 는 null 원소를 거부한다. 원소 검증은 이미지 로더가 맡으므로 여기서는 그대로 복사만 한다
    private static List<String> unmodifiableCopy(List<String> source) {
        return source == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(source));
    }
}
