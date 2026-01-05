package com.aisip.OnO.backend.util;

import com.aisip.OnO.backend.folder.dto.FolderRegisterDto;
import com.aisip.OnO.backend.folder.entity.Folder;

import java.util.UUID;

/**
 * 테스트용 랜덤 Folder 생성기
 * 통합 테스트에서 고유한 Folder 엔티티를 쉽게 생성할 수 있도록 지원
 */
public class RandomFolderGenerator {

    private static final String DEFAULT_FOLDER_NAME_PREFIX = "테스트폴더";

    /**
     * 랜덤 이름을 가진 기본 테스트 Folder 생성 (루트 폴더)
     * @param userId 폴더를 소유할 사용자 ID
     * @return 생성된 Folder 엔티티
     */
    public static Folder createRandomFolder(Long userId) {
        String randomName = DEFAULT_FOLDER_NAME_PREFIX + "_" + UUID.randomUUID().toString().substring(0, 8);

        FolderRegisterDto folderRegisterDto = new FolderRegisterDto(
                randomName,
                null,
                null
        );

        return Folder.from(folderRegisterDto, userId);
    }

    /**
     * 지정된 이름으로 테스트 Folder 생성 (루트 폴더)
     * @param folderName 폴더 이름
     * @param userId 폴더를 소유할 사용자 ID
     * @return 생성된 Folder 엔티티
     */
    public static Folder createRandomFolder(String folderName, Long userId) {
        FolderRegisterDto folderRegisterDto = new FolderRegisterDto(
                folderName,
                null,
                null
        );

        return Folder.from(folderRegisterDto, userId);
    }

    /**
     * 부모 폴더를 가진 랜덤 이름의 서브 Folder 생성
     * @param parentFolder 부모 폴더
     * @param userId 폴더를 소유할 사용자 ID
     * @return 생성된 Folder 엔티티
     */
    public static Folder createRandomSubFolder(Folder parentFolder, Long userId) {
        String randomName = DEFAULT_FOLDER_NAME_PREFIX + "_sub_" + UUID.randomUUID().toString().substring(0, 8);

        FolderRegisterDto folderRegisterDto = new FolderRegisterDto(
                randomName,
                null,
                parentFolder.getId()
        );

        return Folder.from(folderRegisterDto, parentFolder, userId);
    }

    /**
     * 부모 폴더와 지정된 이름을 가진 서브 Folder 생성
     * @param folderName 폴더 이름
     * @param parentFolder 부모 폴더
     * @param userId 폴더를 소유할 사용자 ID
     * @return 생성된 Folder 엔티티
     */
    public static Folder createRandomSubFolder(String folderName, Folder parentFolder, Long userId) {
        FolderRegisterDto folderRegisterDto = new FolderRegisterDto(
                folderName,
                null,
                parentFolder.getId()
        );

        return Folder.from(folderRegisterDto, parentFolder, userId);
    }

    /**
     * 짧은 이름을 가진 랜덤 Folder 생성 (디버깅에 유용)
     * @param userId 폴더를 소유할 사용자 ID
     * @return 생성된 Folder 엔티티
     */
    public static Folder createRandomFolderWithShortName(Long userId) {
        String randomName = "folder_" + UUID.randomUUID().toString().substring(0, 4);

        FolderRegisterDto folderRegisterDto = new FolderRegisterDto(
                randomName,
                null,
                null
        );

        return Folder.from(folderRegisterDto, userId);
    }
}