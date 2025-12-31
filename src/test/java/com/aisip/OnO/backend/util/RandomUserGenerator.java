package com.aisip.OnO.backend.util;

import com.aisip.OnO.backend.user.dto.UserRegisterDto;
import com.aisip.OnO.backend.user.entity.User;

/**
 * 테스트용 랜덤 User 생성기
 * 통합 테스트에서 고유한 User 엔티티를 쉽게 생성할 수 있도록 지원
 */
public class RandomUserGenerator {

    private static final String DEFAULT_PLATFORM = "GOOGLE";
    private static final String DEFAULT_NAME_PREFIX = "테스트유저";
    private static final String DEFAULT_EMAIL_SUFFIX = "@test.com";

    /**
     * 랜덤 identifier를 가진 기본 테스트 User 생성
     * @return 생성된 User 엔티티
     */
    public static User createRandomUser() {
        String randomIdentifier = TestIdentifierGenerator.generateRandomIdentifier();

        UserRegisterDto userRegisterDto = UserRegisterDto.builder()
                .identifier(randomIdentifier)
                .platform(DEFAULT_PLATFORM)
                .name(DEFAULT_NAME_PREFIX)
                .email(randomIdentifier + DEFAULT_EMAIL_SUFFIX)
                .build();

        return User.from(userRegisterDto);
    }

    /**
     * 지정된 platform으로 랜덤 User 생성
     * @param platform 플랫폼 (GOOGLE, KAKAO 등)
     * @return 생성된 User 엔티티
     */
    public static User createRandomUser(String platform) {
        String randomIdentifier = TestIdentifierGenerator.generateRandomIdentifier();

        UserRegisterDto userRegisterDto = UserRegisterDto.builder()
                .identifier(randomIdentifier)
                .platform(platform)
                .name(DEFAULT_NAME_PREFIX)
                .email(randomIdentifier + DEFAULT_EMAIL_SUFFIX)
                .build();

        return User.from(userRegisterDto);
    }

    /**
     * 모든 필드를 커스터마이징한 랜덤 User 생성
     * @param platform 플랫폼
     * @param name 이름
     * @param emailPrefix 이메일 prefix (randomIdentifier가 자동으로 추가됨)
     * @return 생성된 User 엔티티
     */
    public static User createRandomUser(String platform, String name, String emailPrefix) {
        String randomIdentifier = TestIdentifierGenerator.generateRandomIdentifier();

        UserRegisterDto userRegisterDto = UserRegisterDto.builder()
                .identifier(randomIdentifier)
                .platform(platform)
                .name(name)
                .email(emailPrefix + "_" + randomIdentifier + DEFAULT_EMAIL_SUFFIX)
                .build();

        return User.from(userRegisterDto);
    }

    /**
     * 지정된 identifier prefix를 가진 랜덤 User 생성
     * @param identifierPrefix identifier의 prefix
     * @return 생성된 User 엔티티
     */
    public static User createRandomUserWithPrefix(String identifierPrefix) {
        String randomIdentifier = TestIdentifierGenerator.generateRandomIdentifier(identifierPrefix);

        UserRegisterDto userRegisterDto = UserRegisterDto.builder()
                .identifier(randomIdentifier)
                .platform(DEFAULT_PLATFORM)
                .name(DEFAULT_NAME_PREFIX)
                .email(randomIdentifier + DEFAULT_EMAIL_SUFFIX)
                .build();

        return User.from(userRegisterDto);
    }

    /**
     * 짧은 identifier를 가진 랜덤 User 생성 (로그 확인 등에 유용)
     * @return 생성된 User 엔티티
     */
    public static User createRandomUserWithShortId() {
        String randomIdentifier = TestIdentifierGenerator.generateShortRandomIdentifier();

        UserRegisterDto userRegisterDto = UserRegisterDto.builder()
                .identifier(randomIdentifier)
                .platform(DEFAULT_PLATFORM)
                .name(DEFAULT_NAME_PREFIX)
                .email(randomIdentifier + DEFAULT_EMAIL_SUFFIX)
                .build();

        return User.from(userRegisterDto);
    }
}