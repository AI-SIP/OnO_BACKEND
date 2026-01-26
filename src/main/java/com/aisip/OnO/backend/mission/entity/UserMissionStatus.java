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

    // 총 학습 레벨 (4개 능력치 합산 기준)
    private Long totalStudyLevel;
    private Long totalStudyPoint;

    /**
     * 출석 경험치 획득
     */
    public void gainAttendancePoint(Long value) {
        this.attendancePoint += value;
        while (this.attendancePoint >= getThresholdForLevel(attendanceLevel)) {
            this.attendancePoint -= getThresholdForLevel(attendanceLevel);
            this.attendanceLevel += 1;
        }
        updateTotalStudyLevel(value);
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
        updateTotalStudyLevel(value);
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
        updateTotalStudyLevel(value);
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
        updateTotalStudyLevel(value);
    }

    /**
     * 총 학습 레벨 업데이트 (경험치 획득 시 호출)
     */
    private void updateTotalStudyLevel(Long gainedPoints) {
        // 총 학습 포인트에 획득 포인트 추가
        this.totalStudyPoint += gainedPoints;

        // 레벨업 처리
        while (this.totalStudyLevel < 15 && this.totalStudyPoint >= getTotalStudyThresholdForLevel(this.totalStudyLevel)) {
            this.totalStudyPoint -= getTotalStudyThresholdForLevel(this.totalStudyLevel);
            this.totalStudyLevel += 1;
        }
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
