package com.aisip.OnO.backend.practicenote.service;

import com.aisip.OnO.backend.common.web.AppVersionResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * 요일 없는 주간 반복 알림을 어떤 요청에서 막을지 정하는 판정.
 *
 * <p>버전 비교 자체는 {@code AppVersionResolverTest} 가 본다. 여기서는 <b>이 도메인이 무엇을 기준으로
 * 삼는가</b>와, 그 기준이 미션 도메인의 설정에 끌려가지 않는가를 본다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("주간 알림 요일 강제 판정")
class PracticeNotificationWeekDayPolicyTest {

    @Mock
    private AppVersionResolver appVersionResolver;

    @InjectMocks
    private PracticeNotificationWeekDayPolicy policy;

    @Test
    @DisplayName("기준 버전은 설정으로 바꿀 수 있고 기본값은 4.0.0 이다")
    void thresholdIsConfigurable() throws NoSuchFieldException {
        // 코드에 박으면 기준을 되돌릴 때마다 배포해야 한다. 값이 아니라 선언을 본다.
        Field field = PracticeNotificationWeekDayPolicy.class.getDeclaredField("weekDaysRequiredVersion");

        assertThat(field.getAnnotation(Value.class).value())
                .as("프로퍼티 이름과 기본값은 운영에서 기준을 옮길 때 쓰는 계약이다")
                .isEqualTo("${ono.practice-note.week-days-required-version:4.0.0}");
    }

    @Test
    @DisplayName("기준 버전 이상에서 온 요청이면 요일을 강제한다")
    void requiresWeekDaysForNewApp() {
        ReflectionTestUtils.setField(policy, "weekDaysRequiredVersion", "4.0.0");
        given(appVersionResolver.isAtLeast("4.0.0")).willReturn(true);

        assertThat(policy.requiresWeekDays()).isTrue();
    }

    @Test
    @DisplayName("그 아래거나 버전을 모르면 강제하지 않는다")
    void doesNotRequireWeekDaysForLegacyApp() {
        // 헤더가 없는 요청도 여기로 떨어진다. 잘못 보면 구버전 사용자가 복습 세트를 영영 수정할 수 없다.
        ReflectionTestUtils.setField(policy, "weekDaysRequiredVersion", "4.0.0");
        given(appVersionResolver.isAtLeast("4.0.0")).willReturn(false);

        assertThat(policy.requiresWeekDays()).isFalse();
    }

    @Test
    @DisplayName("미션 도메인에 기대지 않는다")
    void doesNotDependOnMissionDomain() {
        // 미션의 LegacyAccrualPolicy 에는 자동 적립을 통째로 끄는 비상 스위치가 붙어 있다.
        // 그 스위치를 내렸을 때 복습 알림 검증까지 같이 움직이면 안 된다.
        assertThat(Arrays.stream(PracticeNotificationWeekDayPolicy.class.getDeclaredFields())
                .map(field -> field.getType().getName()))
                .noneMatch(type -> type.startsWith("com.aisip.OnO.backend.mission"));
    }
}
