package com.aisip.OnO.backend.practicenote.service;

import com.aisip.OnO.backend.common.web.AppVersionResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 주간 반복 알림에 요일을 강제할 요청인지 정하는 한 곳.
 *
 * <p>요일 없는 주간 반복을 400 으로 막는 검증(#302)이 <b>구버전 앱 사용자를 가뒀다.</b>
 * 구버전 앱에는 요일을 고르라는 클라이언트 검증이 없어서 "매주" 만 고른 저장이 그대로 올라오고,
 * 예전 서버는 그 요청을 매일 크론으로 받아 줬다. 그래서 운영 DB 에 요일이 빈 주간 알림 행이 이미 있고,
 * 그 복습 세트를 연 구버전 사용자는 제목만 바꿔도 계속 400 을 받는다. 앱을 올리기 전에는 빠져나갈 길이 없다.
 *
 * <p>그래서 <b>요청이 온 앱 버전으로 가른다.</b> 기준 버전 이상에서 온 요청만 막고, 그 아래는 예전처럼
 * 매일 크론으로 저장한다. 프론트는 신버전에서 요일을 강제하므로 신버전 사용자는 이 조합을 만들 수 없다.
 *
 * <p><b>미션의 {@code LegacyAccrualPolicy} 를 그대로 쓰지 않은 이유.</b> 거기에는 자동 적립을 통째로
 * 멈추는 비상 스위치({@code ono.mission.legacy-accrual.enabled})가 붙어 있다. 그 스위치를 내렸을 때
 * 복습 알림 검증까지 같이 움직이면, XP 사고를 막으려고 끈 설정이 알림 저장 동작을 조용히 바꾼다.
 * 공용으로 두는 것은 {@link AppVersionResolver#isAtLeast(String)} 의 버전 비교까지다.
 */
@Component
@RequiredArgsConstructor
public class PracticeNotificationWeekDayPolicy {

    private final AppVersionResolver appVersionResolver;

    /**
     * 요일 없는 주간 반복을 거절하기 시작하는 첫 앱 버전.
     *
     * <p>기본값이 {@code 4.0.0} 인 근거는 <b>헤더 자체가 이 버전 라인에서 처음 붙는다</b> 는 것이다
     * (AI-SIP/OnO_FRONT#214, 프론트 {@code pubspec.yaml} 이 {@code 4.0.0+70}). 헤더를 안 보내는
     * 스토어 빌드는 기준값이 무엇이든 구버전으로 떨어지므로, 이 값은 <b>헤더를 보내는 앱 중
     * 어디까지를 새 앱으로 볼지</b>만 가른다. 헤더를 붙인 커밋과 요일을 강제하는 클라이언트 검증은
     * 같은 미출시 빌드에 들어 있다.
     *
     * <p>설정값으로 둔 이유는 요일 강제가 빠진 빌드가 뒤늦게 드러났을 때 배포 없이 기준을 올려
     * 되돌리기 위해서다.
     */
    @Value("${ono.practice-note.week-days-required-version:4.0.0}")
    private String weekDaysRequiredVersion;

    /**
     * 이번 요청에서 주간 반복에 요일을 강제하는가.
     *
     * <p><b>모르면 강제하지 않는다.</b> 헤더가 없는 요청은 구버전이다. 잘못 보면 신버전 사용자가
     * 요일 없는 주간 알림을 하나 저장할 뿐이지만, 반대로 틀리면 구버전 사용자는 복습 세트를
     * 영영 수정할 수 없다.
     */
    public boolean requiresWeekDays() {
        return appVersionResolver.isAtLeast(weekDaysRequiredVersion);
    }
}
