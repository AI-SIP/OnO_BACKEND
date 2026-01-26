package com.aisip.OnO.backend.mission.entity;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UserMissionStatus {
    // 데일리 출석
    private Long attendanceLevel;
    private Long attendancePoint;

    // 오답노트 작성
    private Long noteWriteLevel;
    private Long noteWritePoint;

    // 문제 복습
    private Long problemPracticeLevel;
    private Long problemPracticePoint;

    // 복습노트 사용
    private Long notePracticeLevel;
    private Long notePracticePoint;

    /**
     * 출석 경험치 획득
     */
    public void gainAttendancePoint(Long value) {
        this.attendancePoint += value;
        while (this.attendancePoint >= getThresholdForLevel(attendanceLevel)) {
            this.attendancePoint -= getThresholdForLevel(attendanceLevel);
            this.attendanceLevel += 1;
        }
    }

    /**
     * 오답노트 작성 경험치 획득
     */
    public void gainNoteWritePoint(Long value) {
        this.noteWritePoint += value;
        while (this.noteWritePoint >= getThresholdForLevel(noteWriteLevel)) {
            this.noteWritePoint -= getThresholdForLevel(noteWriteLevel);
            this.noteWriteLevel += 1;
        }
    }

    /**
     * 문제 복습 경험치 획득
     */
    public void gainProblemPracticePoint(Long value) {
        this.problemPracticePoint += value;
        while (this.problemPracticePoint >= getThresholdForLevel(problemPracticeLevel)) {
            this.problemPracticePoint -= getThresholdForLevel(problemPracticeLevel);
            this.problemPracticeLevel += 1;
        }
    }

    /**
     * 복습노트 사용 경험치 획득
     */
    public void gainNotePracticePoint(Long value) {
        this.notePracticePoint += value;
        while (this.notePracticePoint >= getThresholdForLevel(notePracticeLevel)) {
            this.notePracticePoint -= getThresholdForLevel(notePracticeLevel);
            this.notePracticeLevel += 1;
        }
    }

    /**
     * 총 학습 레벨 계산 (4개 능력치의 모든 포인트 합 기준, 만렙 15)
     * 총 학습 레벨의 필요 경험치 = 개별 능력치 필요 경험치 × 4
     */
    public Long getTotalStudyLevel() {
        Long totalPoints = getTotalStudyPoints();
        Long level = 1L;
        Long remainingPoints = totalPoints;

        // 레벨 15까지만 계산
        while (level < 15 && remainingPoints >= getTotalStudyThresholdForLevel(level)) {
            remainingPoints -= getTotalStudyThresholdForLevel(level);
            level++;
        }

        return level;
    }

    /**
     * 총 학습 포인트 (4개 능력치의 누적 획득 포인트 합)
     * 레벨과 현재 포인트로 총 획득량 역산
     */
    public Long getTotalStudyPoints() {
        return getTotalEarnedPoints(attendanceLevel, attendancePoint)
                + getTotalEarnedPoints(noteWriteLevel, noteWritePoint)
                + getTotalEarnedPoints(problemPracticeLevel, problemPracticePoint)
                + getTotalEarnedPoints(notePracticeLevel, notePracticePoint);
    }

    /**
     * 특정 능력치의 총 획득 포인트 계산
     * (레벨업으로 소진한 포인트 + 현재 포인트)
     */
    private Long getTotalEarnedPoints(Long level, Long currentPoint) {
        Long totalSpent = 0L;
        for (long i = 1; i < level; i++) {
            totalSpent += getThresholdForLevel(i);
        }
        return totalSpent + currentPoint;
    }

    /**
     * 총 학습 레벨의 현재 경험치
     */
    public Long getTotalStudyCurrentPoint() {
        Long totalPoints = getTotalStudyPoints();
        Long level = 1L;
        Long remainingPoints = totalPoints;

        while (level < 15 && remainingPoints >= getTotalStudyThresholdForLevel(level)) {
            remainingPoints -= getTotalStudyThresholdForLevel(level);
            level++;
        }

        return remainingPoints;
    }

    /**
     * 총 학습 레벨의 다음 레벨까지 필요한 경험치
     */
    public Long getTotalStudyNextLevelThreshold() {
        Long currentLevel = getTotalStudyLevel();
        if (currentLevel >= 15) {
            return 0L; // 만렙 도달
        }
        return getTotalStudyThresholdForLevel(currentLevel);
    }

    /**
     * 개별 능력치 레벨별 필요 경험치 계산
     *
     * 현재 공식: 10 + (level - 1) * 10
     * - 레벨 1→2: 10
     * - 레벨 2→3: 20
     * - 레벨 14→15: 140
     */
    private Long getThresholdForLevel(Long level) {
        return 10 + (level - 1) * 10;
    }

    /**
     * 총 학습 레벨의 필요 경험치 계산
     * 개별 능력치 필요 경험치 × 4
     *
     * - 레벨 1→2: 40 (10 × 4)
     * - 레벨 2→3: 80 (20 × 4)
     * - 레벨 14→15: 560 (140 × 4)
     */
    private Long getTotalStudyThresholdForLevel(Long level) {
        return getThresholdForLevel(level) * 4;
    }
}
