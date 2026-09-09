package com.aisip.OnO.backend.mission.service;

import com.aisip.OnO.backend.mission.entity.MissionCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 기간 키는 리셋 배치를 대신하는 장치다. 여기가 틀리면 미션이 하루 늦게 초기화되거나
 * 한 주에 두 번 초기화된다. 순수 로직이라 스프링 없이 경계만 확인한다.
 */
@DisplayName("미션 기간 키")
class MissionPeriodKeyTest {

    @Nested
    @DisplayName("일일 키")
    class Daily {

        @Test
        @DisplayName("yyyy-MM-dd 형식이다")
        void formatsAsIsoDate() {
            assertThat(MissionPeriodKey.daily(LocalDate.of(2026, 9, 9))).isEqualTo("2026-09-09");
        }

        @Test
        @DisplayName("하루가 바뀌면 키도 바뀐다")
        void differsByDay() {
            assertThat(MissionPeriodKey.daily(LocalDate.of(2026, 9, 9)))
                    .isNotEqualTo(MissionPeriodKey.daily(LocalDate.of(2026, 9, 10)));
        }
    }

    @Nested
    @DisplayName("주간 키")
    class Weekly {

        @Test
        @DisplayName("yyyy-Www 형식이다")
        void formatsAsIsoWeek() {
            assertThat(MissionPeriodKey.weekly(LocalDate.of(2026, 9, 9))).isEqualTo("2026-W37");
        }

        @Test
        @DisplayName("월요일 0시부터 일요일 23시 59분까지가 같은 주다")
        void mondayToSundayShareOneKey() {
            LocalDate monday = LocalDate.of(2026, 9, 7);
            LocalDate sunday = LocalDate.of(2026, 9, 13);

            assertThat(MissionPeriodKey.weekly(monday)).isEqualTo("2026-W37");
            assertThat(MissionPeriodKey.weekly(sunday)).isEqualTo("2026-W37");
        }

        @Test
        @DisplayName("다음 월요일부터는 다음 주다")
        void nextMondayStartsNewWeek() {
            assertThat(MissionPeriodKey.weekly(LocalDate.of(2026, 9, 14))).isEqualTo("2026-W38");
        }

        @Test
        @DisplayName("12월 31일이 다음 해 1주차면 다음 해 키를 쓴다")
        void yearEndBelongsToNextIsoYear() {
            // 2019-12-31 은 달력으로는 2019년이지만 ISO 로는 2020년 1주차다.
            // 달력 연도로 키를 만들면 이 주가 2019-W01 과 2020-W01 로 쪼개져 주간 미션이 두 번 초기화된다.
            assertThat(MissionPeriodKey.weekly(LocalDate.of(2019, 12, 31))).isEqualTo("2020-W01");
        }

        @Test
        @DisplayName("1월 1일이 지난 해 마지막 주면 지난 해 키를 쓴다")
        void yearStartCanBelongToPreviousIsoYear() {
            assertThat(MissionPeriodKey.weekly(LocalDate.of(2021, 1, 1))).isEqualTo("2020-W53");
        }

        @Test
        @DisplayName("한 자리 주차는 0을 채운다")
        void padsSingleDigitWeek() {
            assertThat(MissionPeriodKey.weekly(LocalDate.of(2026, 1, 5))).isEqualTo("2026-W02");
        }
    }

    @Nested
    @DisplayName("카테고리별 키 선택")
    class ByCategory {

        private final LocalDate date = LocalDate.of(2026, 9, 9);

        @Test
        @DisplayName("일일 미션은 날짜 키를 쓴다")
        void dailyUsesDateKey() {
            assertThat(MissionPeriodKey.of(MissionCategory.DAILY, date)).isEqualTo("2026-09-09");
        }

        @Test
        @DisplayName("주간 미션은 주차 키를 쓴다")
        void weeklyUsesWeekKey() {
            assertThat(MissionPeriodKey.of(MissionCategory.WEEKLY, date)).isEqualTo("2026-W37");
        }
    }
}
