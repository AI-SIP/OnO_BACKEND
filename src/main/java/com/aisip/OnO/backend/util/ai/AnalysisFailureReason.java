package com.aisip.OnO.backend.util.ai;

import java.util.EnumSet;
import java.util.Set;

/**
 * AI 문제 분석이 실패한 이유.
 *
 * 앱은 FAILED 상태일 때 problem_analysis.error_message 를 사용자 화면에 그대로 보여 준다.
 * 그래서 예외 메시지 원문 대신 여기 있는 사용자용 문구를 저장하고, 원문은 로그에만 남긴다.
 * tag 는 실패 메트릭(ono.ai.analysis.failures)의 reason 태그로 쓴다.
 */
public enum AnalysisFailureReason {

    REFUSAL("refusal", "AI가 이 이미지를 분석하지 못했어요. 문제 부분만 잘라서 다시 올려 주세요."),
    UNREADABLE_IMAGE("unreadable_image", "이미지에서 문제를 찾지 못했어요. 문제가 잘 보이는 사진으로 다시 올려 주세요."),
    UNSUPPORTED_IMAGE("unsupported_image", "이 이미지는 분석할 수 없어요. JPG나 PNG 사진으로 다시 올려 주세요."),
    IMAGE_TOO_LARGE("image_too_large", "이미지가 너무 커서 분석하지 못했어요. 사진 수를 줄이거나 더 작은 사진으로 다시 올려 주세요."),
    INVALID_IMAGE_SOURCE("invalid_image_source", "이미지를 불러오지 못했어요. 이미지를 다시 올려 주세요."),
    // 장수 상한은 설정값(openai.analysis.max-images)이라 문구에 숫자를 넣지 않는다
    TOO_MANY_IMAGES("too_many_images", "문제 이미지가 너무 많아요. 사진 수를 줄인 뒤 다시 분석해 주세요."),
    REQUEST_REJECTED("request_rejected", "AI가 이 요청을 처리하지 못했어요. 잠시 후 다시 시도해 주세요."),
    INVALID_RESPONSE("invalid_response", "AI 분석 결과를 정리하지 못했어요. 잠시 후 다시 시도해 주세요."),
    QUOTA("quota", "지금은 AI 분석을 사용할 수 없어요. 잠시 후 다시 시도해 주세요."),
    DISPATCH("dispatch", "분석 요청을 보내지 못했어요. 다시 시도해 주세요."),
    MAX_RETRIES("max_retries", "AI 분석이 여러 번 실패했어요. 잠시 후 다시 시도해 주세요."),
    TRANSIENT("transient", "일시적인 문제로 분석하지 못했어요. 잠시 후 다시 시도해 주세요.");

    /**
     * OpenAI 를 부르기 전에 이미지 쪽에서 끝난 실패. 사용자가 AI 를 쓰지 않았으므로 하루 한도 1회를 돌려준다.
     */
    private static final Set<AnalysisFailureReason> QUOTA_REFUNDED = EnumSet.of(
            INVALID_IMAGE_SOURCE, UNSUPPORTED_IMAGE, TOO_MANY_IMAGES, IMAGE_TOO_LARGE);

    private final String tag;
    private final String userMessage;

    AnalysisFailureReason(String tag, String userMessage) {
        this.tag = tag;
        this.userMessage = userMessage;
    }

    public String tag() {
        return tag;
    }

    public String userMessage() {
        return userMessage;
    }

    public boolean refundsQuota() {
        return QUOTA_REFUNDED.contains(this);
    }

    /**
     * 재시도하지 않는 실패는 예외가 이유를 들고 있고, 그 밖의 예외는 일시적인 실패로 본다.
     */
    public static AnalysisFailureReason from(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof NonRetryableAnalysisException nonRetryable) {
                return nonRetryable.getReason();
            }
            current = current.getCause();
        }
        return TRANSIENT;
    }
}
