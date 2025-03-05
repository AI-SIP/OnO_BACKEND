package com.aisip.OnO.backend.problem.entity;

import com.aisip.OnO.backend.common.entity.BaseEntity;
import com.aisip.OnO.backend.practicenote.entity.ProblemPracticeNoteMapping;
import com.aisip.OnO.backend.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Getter
@Setter
@Table(name = "problem")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Problem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "folder_id")
    private Folder folder;

    private String memo;

    private String reference;

    private LocalDateTime solvedAt;

    @Column(columnDefinition = "LONGTEXT")
    private String analysis;

    @Enumerated(EnumType.STRING)
    @Column(name = "template_type")
    private ProblemTemplateType problemTemplateType;

    @OneToMany(mappedBy = "problem", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ProblemImageData> images;

    @OneToMany(mappedBy = "problem", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProblemPracticeNoteMapping> problemPracticeNoteMappings;
}
