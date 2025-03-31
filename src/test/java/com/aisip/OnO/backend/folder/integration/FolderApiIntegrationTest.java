package com.aisip.OnO.backend.folder.integration;

import com.aisip.OnO.backend.fileupload.service.FileUploadService;
import com.aisip.OnO.backend.folder.dto.FolderRegisterDto;
import com.aisip.OnO.backend.folder.entity.Folder;
import com.aisip.OnO.backend.folder.repository.FolderRepository;
import com.aisip.OnO.backend.problem.dto.ProblemImageDataRegisterDto;
import com.aisip.OnO.backend.problem.dto.ProblemRegisterDto;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problem.entity.ProblemImageData;
import com.aisip.OnO.backend.problem.entity.ProblemImageType;
import com.aisip.OnO.backend.problem.repository.ProblemImageDataRepository;
import com.aisip.OnO.backend.problem.repository.ProblemRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT) // 랜덤 포트로 애플리케이션 실행
@AutoConfigureMockMvc
public class FolderApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private ProblemImageDataRepository problemImageDataRepository;

    @Autowired
    private FolderRepository folderRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private FileUploadService fileUploadService;

    private Long userId;

    @BeforeEach
    void setUp() {
        userId = 1L;
        // 인증 설정
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        userId, null, List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))
                )
        );

        List<Folder> folderList = new ArrayList<>();
        List<Problem> problemList = new ArrayList<>();

         /*
           0
        /    \
        1     2
        | \   |
        3, 4  5
         */
        Folder rootFolder = Folder.from(
                new FolderRegisterDto(
                        "rootFolder",
                        null,
                        null
                ),
                null,
                1L
        );
        folderRepository.save(rootFolder);
        folderList.add(rootFolder);

        for (int i = 0; i < 5; i++) {
            Folder folder = Folder.from(
                    new FolderRegisterDto(
                            "folder " + i,
                            null,
                            (long) i / 2
                    ),
                    folderList.get(i / 2),
                    userId
            );
            folderList.add(folder);
            folderList.get(i / 2).addSubFolder(folder);
            folderRepository.save(folder);
        }

        for (int i = 0; i < 12; i++) {
            Folder targetFolder = folderList.get(i / 2);

            Problem problem = Problem.from(
                    new ProblemRegisterDto(
                            null,
                            "memo" + i,
                            "reference" + i,
                            targetFolder.getId(),
                            LocalDateTime.now(),
                            null
                    ),
                    userId,
                    targetFolder
            );
            targetFolder.addProblem(problem);
            problemRepository.save(problem);

            List<ProblemImageData> imageDataList = new ArrayList<>();
            for (int j = 1; j <= 3; j++){
                ProblemImageDataRegisterDto problemImageDataRegisterDto = new ProblemImageDataRegisterDto(
                        (long) i,
                        "http://example.com/problemId/" + i + "/image" + j,
                        ProblemImageType.valueOf(j)
                );

                ProblemImageData imageData = ProblemImageData.from(problemImageDataRegisterDto, problem);
                imageDataList.add(imageData);
                problemImageDataRepository.save(imageData);
            }
            problem.updateImageDataList(imageDataList);
        }
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        problemImageDataRepository.deleteAll();
        problemRepository.deleteAll();
        folderRepository.deleteAll();
    }
}
