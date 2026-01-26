package com.aisip.OnO.backend.problem.entity;

import com.aisip.OnO.backend.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "problem_analysis")
@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProblemAnalysis extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id", unique = true)
    private Problem problem;

    private String subject;           // 과목 (수학, 영어, 과학 등)

    private String problemType;       // 문제 유형 (계산, 서술형, 객관식 등)

    @Column(columnDefinition = "TEXT")
    private String keyPoints;         // 핵심 개념 (JSON 배열 문자열)

    @Column(columnDefinition = "TEXT")
    private String solution;          // 풀이 과정

    @Column(columnDefinition = "TEXT")
    private String commonMistakes;    // 자주 하는 실수

    @Column(columnDefinition = "TEXT")
    private String studyTips;         // 학습 팁

    @Enumerated(EnumType.STRING)
    private AnalysisStatus status;    // 분석 상태

    @Column(columnDefinition = "TEXT")
    private String errorMessage;      // 에러 발생 시 메시지

    public static ProblemAnalysis createProcessing(Problem problem) {
        return ProblemAnalysis.builder()
                .problem(problem)
                .status(AnalysisStatus.PROCESSING)
                .build();
    }

    public static ProblemAnalysis createSkipped(Problem problem) {
        return ProblemAnalysis.builder()
                .problem(problem)
                .status(AnalysisStatus.NOT_STARTED)
                .errorMessage("이미지가 없어 분석을 진행하지 않았습니다.")
                .build();
    }

    public void updateWithSuccess(String subject, String problemType,
                                   String keyPoints, String solution, String commonMistakes,
                                   String studyTips) {
        this.subject = subject;
        this.problemType = problemType;
        this.keyPoints = keyPoints;
        this.solution = solution;
        this.commonMistakes = commonMistakes;
        this.studyTips = studyTips;
        this.status = AnalysisStatus.COMPLETED;
    }

    public void updateWithFailure(String errorMessage) {
        this.status = AnalysisStatus.FAILED;
        this.errorMessage = errorMessage;
    }
}
