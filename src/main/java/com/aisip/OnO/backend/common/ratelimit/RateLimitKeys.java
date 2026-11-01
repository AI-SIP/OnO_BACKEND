package com.aisip.OnO.backend.common.ratelimit;

/**
 * RateLimitService 에 넘기는 한도 키 이름.
 *
 * 한도를 쓰는 곳과 돌려주는 곳이 다른 키를 쓰면 환불이 조용히 아무 일도 하지 않으므로 한 곳에 둔다.
 */
public final class RateLimitKeys {

    /** AI 문제 분석 하루 한도. 요청(ProblemService)과 환불(전송 실패, 이미지 쪽 실패)이 같이 쓴다 */
    public static final String AI_ANALYSIS = "ai_analysis";

    private RateLimitKeys() {
    }
}
