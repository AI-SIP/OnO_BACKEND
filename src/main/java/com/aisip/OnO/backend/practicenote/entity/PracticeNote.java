package com.aisip.OnO.backend.practicenote.entity;

import com.aisip.OnO.backend.common.entity.BaseEntity;
import com.aisip.OnO.backend.practicenote.dto.PracticeNoteRegisterDto;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLDelete(sql = "UPDATE practice_note SET deleted_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Table(name = "practice_note")
public class PracticeNote extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private String title;

    private Long practiceCount;

    private LocalDateTime lastSolvedAt;

    @OneToMany(mappedBy = "practiceNote", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProblemPracticeNoteMapping> problemPracticeNoteMappingList = new ArrayList<>();

    public static PracticeNote from(PracticeNoteRegisterDto practiceNoteRegisterDto, Long userId) {
        return PracticeNote.builder()
                .userId(userId)
                .title(practiceNoteRegisterDto.practiceTitle())
                .practiceCount(0L)
                .lastSolvedAt(null)
                .problemPracticeNoteMappingList(new ArrayList<>())
                .build();
    }

    public void updateTitle(String title) {
        if (title != null && !title.isBlank()) {
            this.title = title;
        }
    }

    public void updatePracticeNoteCount() {
        this.practiceCount += 1;
        this.lastSolvedAt = LocalDateTime.now();
    }

    public void addProblemToPracticeNote(ProblemPracticeNoteMapping problemPracticeNoteMapping) {
        problemPracticeNoteMappingList.add(problemPracticeNoteMapping);
    }
}
