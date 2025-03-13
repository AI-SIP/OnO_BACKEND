package com.aisip.OnO.backend.folder.entity;

import com.aisip.OnO.backend.common.entity.BaseEntity;
import com.aisip.OnO.backend.folder.dto.FolderRegisterDto;
import com.aisip.OnO.backend.problem.entity.Problem;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "folder")
public class Folder extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_folder_id")
    private Folder parentFolder;

    @OneToMany(mappedBy = "parentFolder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Folder> subFolderList = new ArrayList<>();

    @OneToMany(mappedBy = "folder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Problem> problemList = new ArrayList<>();
    public static Folder from(FolderRegisterDto folderRegisterDto, Folder parentFolder, Long userId) {
        return Folder.builder()
                .name(folderRegisterDto.folderName())
                .userId(userId)
                .parentFolder(parentFolder)
                .build();
    }

    public void updateFolderInfo(FolderRegisterDto folderRegisterDto) {
        if (folderRegisterDto.folderName() != null) {
            this.name = folderRegisterDto.folderName();
        }
    }

    public void updateParentFolder(Folder parentFolder) {
        this.parentFolder = parentFolder;
    }
}
