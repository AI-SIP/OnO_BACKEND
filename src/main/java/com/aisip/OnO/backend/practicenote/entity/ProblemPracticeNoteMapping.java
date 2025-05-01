package com.aisip.OnO.backend.practicenote.entity;

import com.aisip.OnO.backend.common.entity.BaseEntity;
import com.aisip.OnO.backend.problem.entity.Problem;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLDelete(sql = "UPDATE problem_practice_note_mapping SET deleted_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Table(name = "problem_practice_note_mapping")
public class ProblemPracticeNoteMapping extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "practice_note_id")
    private PracticeNote practiceNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id", referencedColumnName = "id")
    private Problem problem;

    public static ProblemPracticeNoteMapping from() {
        return ProblemPracticeNoteMapping.builder()
                .build();
    }

    public void addMappingToProblemAndPractice(Problem problem, PracticeNote practiceNote) {
        this.problem = problem;
        this.practiceNote = practiceNote;

        problem.addPracticeMappingToProblem(this);
        practiceNote.addPracticeMappingToPracticeNote(this);
    }
}