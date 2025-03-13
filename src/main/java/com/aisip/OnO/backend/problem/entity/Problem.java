package com.aisip.OnO.backend.problem.entity;

import com.aisip.OnO.backend.common.entity.BaseEntity;
import com.aisip.OnO.backend.folder.entity.Folder;
import com.aisip.OnO.backend.practicenote.entity.ProblemPracticeNoteMapping;
import com.aisip.OnO.backend.problem.dto.ProblemRegisterDto;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "problem")
public class Problem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "folder_id", nullable = false)
    private Folder folder;

    private String memo;

    private String reference;

    private LocalDateTime solvedAt;

    @OneToMany(mappedBy = "problem", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProblemImageData> problemImageDataList;

    @OneToMany(mappedBy = "practiceNote", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProblemPracticeNoteMapping> problemPracticeNoteMappingList;

    public static Problem from(ProblemRegisterDto problemRegisterDto, Long userId, Folder folder) {
        return Problem.builder()
                .userId(userId)
                .folder(folder)
                .memo(problemRegisterDto.memo())
                .reference(problemRegisterDto.reference())
                .solvedAt(problemRegisterDto.solvedAt())
                .build();
    }

    public void updateProblem(ProblemRegisterDto problemRegisterDto) {
        if (problemRegisterDto.memo() != null && !problemRegisterDto.memo().isBlank()) {
            this.memo = memo;
        }

        if (problemRegisterDto.reference() != null && !problemRegisterDto.reference().isBlank()) {
            this.reference = reference;
        }
    }

    public void updateFolder(Folder folder) {
        this.folder = folder;
    }
}
