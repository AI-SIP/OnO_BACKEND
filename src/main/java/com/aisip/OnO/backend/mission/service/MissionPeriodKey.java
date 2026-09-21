package com.aisip.OnO.backend.mission.service;

import com.aisip.OnO.backend.mission.entity.MissionCategory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.IsoFields;

/**
 * 진행도 행을 가르는 기간 키.
 *
 * <p>자정에 어제 진행도를 지우는 배치를 두지 않는다. 오늘 키로 조회하면 어제 것은 애초에 안 잡히기 때문이다.
 * Quartz 가 {@code isClustered: false} 인 채로 blue-green 배포를 하고 있어 배포 구간에 스케줄 작업이
 * 양쪽에서 돌 위험이 있는데, 리셋 배치를 만들지 않으면 그 위험이 통째로 사라진다.
 *
 * <p>하루의 경계는 KST 다. problem·studyroom 등 다른 도메인이 이미 KST 로 하루를 가르고 있어
 * JVM 기본 시간대를 쓰면 배포 환경에 따라 "오늘"이 하루 어긋난다.
 */
public final class MissionPeriodKey {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private MissionPeriodKey() {
    }

    public static LocalDate today() {
        return LocalDate.now(KST);
    }

    /**
     * 지금 시각(KST).
     *
     * <p>인자 없는 {@code LocalDateTime.now()} 는 JVM 기본 시간대를 쓴다. 서비스 기준은 KST 이고,
     * 배포 환경의 시간대에 따라 기준이 흔들리면 안 된다.
     */
    public static LocalDateTime now() {
        return LocalDateTime.now(KST);
    }

    public static String of(MissionCategory category, LocalDate date) {
        return category == MissionCategory.WEEKLY ? weekly(date) : daily(date);
    }

    /** {@code yyyy-MM-dd}. */
    public static String daily(LocalDate date) {
        return date.toString();
    }

    /**
     * {@code yyyy-'W'ww} (ISO 8601 주차, 월요일 시작).
     *
     * <p>연도는 달력 연도가 아니라 <b>주 기반 연도</b>를 쓴다. 2019-12-31 은 달력으로는 2019년이지만
     * ISO 로는 2020년 1주차다. 달력 연도로 키를 만들면 그 주가 {@code 2019-W01} 과 {@code 2020-W01} 로
     * 쪼개져, 연말연시에 주간 미션이 두 번 초기화된다.
     */
    public static String weekly(LocalDate date) {
        int weekBasedYear = date.get(IsoFields.WEEK_BASED_YEAR);
        int weekOfYear = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        return String.format("%d-W%02d", weekBasedYear, weekOfYear);
    }
}
