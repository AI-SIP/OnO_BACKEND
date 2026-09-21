package com.aisip.OnO.backend.common.ratelimit;

import com.aisip.OnO.backend.common.exception.ErrorCase;
import com.aisip.OnO.backend.problem.exception.ProblemErrorCase;
import com.aisip.OnO.backend.util.fileupload.exception.FileUploadErrorCase;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 한도를 넘겼을 때 어떤 오류로 알릴지 고른다.
 *
 * 예전에는 RateLimitAspect 가 한도 종류와 무관하게 ANALYSIS_RATE_LIMIT_EXCEEDED 를 던져서,
 * 이미지 업로드 한도를 넘긴 사용자에게 "AI 분석 일일 요청 횟수를 초과했습니다" 가 떴다.
 */
@Getter
@RequiredArgsConstructor
public enum RateLimitScope {

    FILE_UPLOAD(FileUploadErrorCase.UPLOAD_RATE_LIMIT_EXCEEDED),

    AI_ANALYSIS(ProblemErrorCase.ANALYSIS_RATE_LIMIT_EXCEEDED);

    private final ErrorCase errorCase;
}
