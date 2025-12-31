package com.aisip.OnO.backend.util;

import java.util.UUID;

/**
 * 테스트용 랜덤 identifier 생성기
 * 통합 테스트에서 User 생성 시 고유한 identifier를 생성하여 중복 키 제약 조건 위반을 방지
 */
public class TestIdentifierGenerator {

    /**
     * UUID 기반 랜덤 identifier 생성
     * @return 고유한 identifier 문자열
     */
    public static String generateRandomIdentifier() {
        return "test_user_" + UUID.randomUUID().toString();
    }

    /**
     * 지정된 prefix를 가진 랜덤 identifier 생성
     * @param prefix identifier의 prefix
     * @return prefix + UUID 형태의 identifier 문자열
     */
    public static String generateRandomIdentifier(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString();
    }

    /**
     * 짧은 랜덤 identifier 생성 (UUID의 앞 8자리만 사용)
     * @return 짧은 고유 identifier 문자열
     */
    public static String generateShortRandomIdentifier() {
        return "test_user_" + UUID.randomUUID().toString().substring(0, 8);
    }
}
