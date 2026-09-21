package com.aisip.OnO.backend.mission.entity;

/**
 * 미션이 어떤 주기로 초기화되는지.
 *
 * <p>초기화는 배치가 아니라 {@code period_key} 로 한다. DAILY 는 {@code yyyy-MM-dd},
 * WEEKLY 는 ISO 주차 {@code yyyy-'W'ww} 를 키로 쓰므로 기간이 바뀌면 조회에서 자연히 빠진다.
 */
public enum MissionCategory {
    DAILY,
    WEEKLY
}
