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

    /**
     * 총 학습 레벨 상한.
     *
     * <p>치장 해금표가 총 학습 레벨 20 까지 아이템을 두고 있어 15 에서 20 으로 올렸다.
     * 15 에서 20 까지 더 필요한 경험치는 40 x (15+16+17+18+19) = 3,400 점이다.
     *
     * <p>상한에 닿아도 {@code totalStudyPoint} 는 계속 쌓인다. 그래서 상한을 올리는 순간
     * 이미 쌓아 둔 잔여 포인트로 레벨이 한 번에 여러 단계 오를 수 있는데, 상한 15 에 닿은
     * 사용자가 아직 없어 이번 변경으로 그런 사용자는 생기지 않는다.
     */
    public static final long MAX_TOTAL_STUDY_LEVEL = 20L;

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
        while (this.totalStudyLevel < MAX_TOTAL_STUDY_LEVEL && this.totalStudyPoint >= getTotalStudyThresholdForLevel(this.totalStudyLevel)) {
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
     * 개별 능력치 필요 경험치 × 4 = 40 × 레벨
     *
     * - 레벨 1→2: 40 (10 × 4)
     * - 레벨 2→3: 80 (20 × 4)
     * - 레벨 14→15: 560 (140 × 4)
     * - 레벨 19→20: 760 (190 × 4) — 상한. 15 에서 20 까지 합이 3,400 이다
     */
    private Long getTotalStudyThresholdForLevel(Long level) {
        return getThresholdForLevel(level) * 4;
    }

    // 관리자용: 개별 능력치 직접 설정
    public void setAttendanceLevel(Long level, Long point) {
        this.attendanceLevel = level;
        this.attendancePoint = point;
    }

    public void setNoteWriteLevel(Long level, Long point) {
        this.noteWriteLevel = level;
        this.noteWritePoint = point;
    }

    public void setProblemPracticeLevel(Long level, Long point) {
        this.problemPracticeLevel = level;
        this.problemPracticePoint = point;
    }

    public void setNotePracticeLevel(Long level, Long point) {
        this.notePracticeLevel = level;
        this.notePracticePoint = point;
    }

    public void setTotalStudyLevel(Long level, Long point) {
        this.totalStudyLevel = level;
        this.totalStudyPoint = point;
    }
}
