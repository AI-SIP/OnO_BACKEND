package com.aisip.OnO.backend.achievement.service;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * 날짜 묶음에서 하루도 안 끊긴 가장 긴 구간의 길이.
 *
 * <p>훈장 '개근'이 쓴다. <b>오늘까지 이어질 필요는 없다.</b> 지난달에 서른 날을 채웠으면 그때 받은
 * 것이고, 이번 달에 하루 빠졌다고 되돌려 주지 않는다. 그래서 "지금 며칠째인가" 가 아니라
 * "가장 길었던 때가 며칠인가" 를 본다.
 */
public final class ConsecutiveDays {

    private ConsecutiveDays() {
    }

    /** @param dates 중복이 있어도 된다. 안에서 정렬하고 같은 날은 하루로 접는다. */
    public static long longestRun(Collection<LocalDate> dates) {
        if (dates.isEmpty()) {
            return 0;
        }

        List<LocalDate> sorted = dates.stream().distinct().sorted().toList();

        long longest = 1;
        long current = 1;
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i - 1).plusDays(1).equals(sorted.get(i))) {
                current++;
            } else {
                current = 1;
            }
            longest = Math.max(longest, current);
        }
        return longest;
    }
}
