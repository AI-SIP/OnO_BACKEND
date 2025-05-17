package com.aisip.OnO.backend.problem.entity;

import com.aisip.OnO.backend.common.entity.BaseEntity;
import com.aisip.OnO.backend.folder.entity.Folder;
import com.aisip.OnO.backend.practicenote.entity.ProblemPracticeNoteMapping;
import com.aisip.OnO.backend.problem.dto.ProblemRegisterDto;
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
@SQLDelete(sql = "UPDATE problem SET deleted_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Table(name = "problem")
public class Problem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "folder_id")
    private Folder folder;

    private String memo;

    private String reference;

    private LocalDateTime solvedAt;

    @OneToMany(mappedBy = "problem", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProblemImageData> problemImageDataList = new ArrayList<>();

    @OneToMany(mappedBy = "problem", cascade = CascadeType.ALL)
    private List<ProblemPracticeNoteMapping> problemPracticeNoteMappingList = new ArrayList<>();

    public static Problem from(ProblemRegisterDto problemRegisterDto, Long userId) {
        return Problem.builder()
                .userId(userId)
                .memo(problemRegisterDto.memo())
                .folder(null)
                .reference(problemRegisterDto.reference())
                .solvedAt(problemRegisterDto.solvedAt())
                .problemImageDataList(new ArrayList<>())
                .problemPracticeNoteMappingList(new ArrayList<>())
                .build();
    }

    public void addImageData(ProblemImageData problemImageData) {
        problemImageDataList.add(problemImageData);
    }

    public void updateProblem(ProblemRegisterDto problemRegisterDto) {
        if (problemRegisterDto.solvedAt() != null) {
            this.solvedAt = problemRegisterDto.solvedAt();
        }

        if (problemRegisterDto.memo() != null && !problemRegisterDto.memo().isBlank()) {
            this.memo = problemRegisterDto.memo();
        }

        if (problemRegisterDto.reference() != null && !problemRegisterDto.reference().isBlank()) {
            this.reference = problemRegisterDto.reference();
        }
    }

    public void updateImageDataList(List<ProblemImageData> imageDataList) {
        if (imageDataList != null) {
            if (this.problemImageDataList != null) {
                this.problemImageDataList.clear();
                this.problemImageDataList.addAll(imageDataList);
            }
        }
    }

    public void updateFolder(Folder folder) {
        if(this.getFolder() != null){
            this.getFolder().removeProblem(this);
        }

        this.folder = folder;
        folder.addProblem(this);
    }

    public void addPracticeMappingToProblem(ProblemPracticeNoteMapping problemPracticeNoteMapping) {
        problemPracticeNoteMappingList.add(problemPracticeNoteMapping);
    }

    public void removePracticeMappingFromProblem(ProblemPracticeNoteMapping problemPracticeNoteMapping) {
        problemPracticeNoteMappingList.remove(problemPracticeNoteMapping);
    }
}