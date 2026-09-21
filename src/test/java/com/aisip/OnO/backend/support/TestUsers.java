package com.aisip.OnO.backend.support;

import com.aisip.OnO.backend.user.dto.UserRegisterDto;
import com.aisip.OnO.backend.user.entity.User;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 저장되지 않은 User 엔티티를 만드는 정적 헬퍼.
 *
 * <p>스프링 컨텍스트가 없는 단위 테스트나, 리포지토리를 직접 잡고 저장하는
 * 테스트에서 쓴다. 컨텍스트가 있으면 {@link TestFixtures}를 쓰는 쪽이 낫다.
 *
 * <p>{@code User.identifier}에 유니크 인덱스가 있으므로 호출마다 다른 값을 만든다.
 */
public final class TestUsers {

    private static final AtomicLong SEQUENCE = new AtomicLong();

    private TestUsers() {
    }

    public static User create() {
        return create("GOOGLE", "테스트유저", "user");
    }

    public static User create(String platform, String name, String emailPrefix) {
        long seq = SEQUENCE.incrementAndGet();
        String identifier = emailPrefix + "-" + seq;

        return User.from(UserRegisterDto.builder()
                .identifier(identifier)
                .platform(platform)
                .name(name)
                .email(identifier + "@test.ono")
                .build());
    }
}
