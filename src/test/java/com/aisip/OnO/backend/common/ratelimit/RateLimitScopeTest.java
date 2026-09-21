package com.aisip.OnO.backend.common.ratelimit;

import com.aisip.OnO.backend.problem.exception.ProblemErrorCase;
import com.aisip.OnO.backend.util.fileupload.exception.FileUploadErrorCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 한도 종류와 사용자에게 보이는 문구가 어긋나지 않도록 고정한다.
 * 예전에는 이미지 업로드 한도를 넘겨도 "AI 분석 일일 요청 횟수를 초과했습니다" 가 떴다.
 */
class RateLimitScopeTest {

    @Test
    @DisplayName("이미지 업로드 한도는 업로드 오류로 알린다")
    void fileUploadScopeUsesUploadErrorCase() {
        var errorCase = RateLimitScope.FILE_UPLOAD.getErrorCase();

        assertThat(errorCase).isEqualTo(FileUploadErrorCase.UPLOAD_RATE_LIMIT_EXCEEDED);
        assertThat(errorCase.getHttpStatusCode()).isEqualTo(429);
        assertThat(errorCase.getMessage()).doesNotContain("AI");
    }

    @Test
    @DisplayName("AI 분석 한도는 분석 오류로 알린다")
    void aiAnalysisScopeUsesAnalysisErrorCase() {
        assertThat(RateLimitScope.AI_ANALYSIS.getErrorCase())
                .isEqualTo(ProblemErrorCase.ANALYSIS_RATE_LIMIT_EXCEEDED);
    }
}
