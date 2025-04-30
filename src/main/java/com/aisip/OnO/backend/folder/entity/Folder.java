package com.aisip.OnO.backend.folder.entity;

import com.aisip.OnO.backend.common.entity.BaseEntity;
import com.aisip.OnO.backend.folder.dto.FolderRegisterDto;
import com.aisip.OnO.backend.problem.entity.Problem;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLDelete(sql = "UPDATE folder SET deleted_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
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

    @OneToMany(mappedBy = "parentFolder", cascade = CascadeType.ALL)
    private List<Folder> subFolderList = new ArrayList<>();

    @OneToMany(mappedBy = "folder", cascade = CascadeType.ALL)
    private List<Problem> problemList = new ArrayList<>();

    public static Folder from(FolderRegisterDto folderRegisterDto, Long userId) {
        return Folder.builder()
                .name(folderRegisterDto.folderName())
                .userId(userId)
                .build();
    }

    public static Folder from(FolderRegisterDto folderRegisterDto, Folder parentFolder, Long userId) {
        return Folder.builder()
                .name(folderRegisterDto.folderName())
                .userId(userId)
                .parentFolder(parentFolder)
                .build();
    }

    public void addProblem(Problem problem) {
        problemList.add(problem);
    }

    public void removeProblem(Problem problem) {
        problemList.remove(problem);
    }

    public void addSubFolder(Folder folder) {
        subFolderList.add(folder);
        folder.updateParentFolder(this);
    }

    public void removeSubFolder(Folder folder) {
        subFolderList.remove(folder);
    }

    public void updateFolderInfo(FolderRegisterDto folderRegisterDto) {
        if (folderRegisterDto.folderName() != null) {
            this.name = folderRegisterDto.folderName();
        }
    }

    public void updateParentFolder(Folder parentFolder) {
        if (this.parentFolder != null) {
            this.parentFolder.removeSubFolder(this);
        }
        this.parentFolder = parentFolder;
        parentFolder.addSubFolder(this);
    }
}
