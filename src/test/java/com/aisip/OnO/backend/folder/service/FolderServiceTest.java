package com.aisip.OnO.backend.folder.service;

import com.aisip.OnO.backend.folder.repository.FolderRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class FolderServiceTest {

    @InjectMocks
    private FolderService folderService;

    @Mock
    private FolderRepository folderRepository;



    @BeforeEach
    void setUp() {
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    void findFolder() {
    }

    @Test
    void findFolderEntity() {
    }

    @Test
    void findAllFolderThumbnails() {
    }

    @Test
    void findAllFolders() {
    }

    @Test
    void findRootFolder() {
    }

    @Test
    void createRootFolder() {
    }

    @Test
    void createFolder() {
    }

    @Test
    void updateFolder() {
    }

    @Test
    void deleteFoldersWithProblems() {
    }

    @Test
    void deleteAllUserFoldersWithProblems() {
    }

    @Test
    void getAllFolderIdsIncludingSubFolders() {
    }

    @Test
    void deleteAllByFolderIds() {
    }

    @Test
    void deleteAllUserFolders() {
    }
}