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

    private Long getThresholdForLevel(Long level) {
        return 100 + (level - 1) * 20;
    }
}
