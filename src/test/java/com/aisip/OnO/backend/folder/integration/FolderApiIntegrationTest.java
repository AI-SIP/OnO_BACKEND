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
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

    private List<Folder> folderList;

    private List<Problem> problemList;

    @BeforeEach
    void setUp() {
        userId = 1L;
        // 인증 설정
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        userId, null, List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))
                )
        );

        folderList = new ArrayList<>();
        problemList = new ArrayList<>();

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
        rootFolder = folderRepository.save(rootFolder);
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
            folder = folderRepository.save(folder);
            folderList.add(folder);
            folderList.get(i / 2).addSubFolder(folder);
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
            problem = problemRepository.save(problem);
            problemList.add(problem);

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

        problemList.clear();
        folderList.clear();
    }

    @Test
    @DisplayName("getFolder() api 테스트 - root folder")
    public void getFolderTest_Root() throws Exception {
        //given
        Folder folder = folderList.get(0);

        // when & then - 해당 폴더를 조회하는 API 호출
        mockMvc.perform(MockMvcRequestBuilders.get(String.format("/api/folder/%d", folder.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.folderId").value(folder.getId()))
                .andExpect(jsonPath("$.data.folderName").value(folder.getName()))
                .andExpect(jsonPath("$.data.parentFolder").isEmpty())
                .andExpect(jsonPath("$.data.subFolderList.length()").value(folder.getSubFolderList().size()))
                .andExpect(jsonPath("$.data.subFolderList[0].folderId").value(folder.getSubFolderList().get(0).getId()))
                .andExpect(jsonPath("$.data.subFolderList[0].folderName").value(folder.getSubFolderList().get(0).getName()))
                .andExpect(jsonPath("$.data.subFolderList[1].folderId").value(folder.getSubFolderList().get(1).getId()))
                .andExpect(jsonPath("$.data.subFolderList[1].folderName").value(folder.getSubFolderList().get(1).getName()))
                .andExpect(jsonPath("$.data.problemList.length()").value(folder.getProblemList().size()))
                .andExpect(jsonPath("$.data.problemList[0].problemId").value(folder.getProblemList().get(0).getId()))
                .andExpect(jsonPath("$.data.problemList[0].imageUrlList.length()").value(folder.getProblemList().get(0).getProblemImageDataList().size()))
                .andExpect(jsonPath("$.data.problemList[1].problemId").value(folder.getProblemList().get(1).getId()))
                .andExpect(jsonPath("$.data.problemList[1].imageUrlList.length()").value(folder.getProblemList().get(1).getProblemImageDataList().size()));
    }

    @Test
    @DisplayName("getFolder() api 테스트 - internal folder")
    public void getFolderTest_Internal() throws Exception {
        //given
        Folder folder = folderList.get(1);

        // when & then - 해당 폴더를 조회하는 API 호출
        mockMvc.perform(MockMvcRequestBuilders.get(String.format("/api/folder/%d", folder.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.folderId").value(folder.getId()))
                .andExpect(jsonPath("$.data.folderName").value(folder.getName()))
                .andExpect(jsonPath("$.data.parentFolder.folderId").value(folder.getParentFolder().getId()))
                .andExpect(jsonPath("$.data.subFolderList.length()").value(folder.getSubFolderList().size()))
                .andExpect(jsonPath("$.data.subFolderList[0].folderId").value(folder.getSubFolderList().get(0).getId()))
                .andExpect(jsonPath("$.data.subFolderList[0].folderName").value(folder.getSubFolderList().get(0).getName()))
                .andExpect(jsonPath("$.data.subFolderList[1].folderId").value(folder.getSubFolderList().get(1).getId()))
                .andExpect(jsonPath("$.data.subFolderList[1].folderName").value(folder.getSubFolderList().get(1).getName()))
                .andExpect(jsonPath("$.data.problemList.length()").value(folder.getProblemList().size()))
                .andExpect(jsonPath("$.data.problemList[0].problemId").value(folder.getProblemList().get(0).getId()))
                .andExpect(jsonPath("$.data.problemList[0].imageUrlList.length()").value(folder.getProblemList().get(0).getProblemImageDataList().size()))
                .andExpect(jsonPath("$.data.problemList[1].problemId").value(folder.getProblemList().get(1).getId()))
                .andExpect(jsonPath("$.data.problemList[1].imageUrlList.length()").value(folder.getProblemList().get(1).getProblemImageDataList().size()));
    }

    @Test
    @DisplayName("getFolder() api 테스트 - external folder")
    public void getFolderTest_External() throws Exception {
        //given
        Folder folder = folderList.get(folderList.size() - 1);

        // when & then - 해당 폴더를 조회하는 API 호출
        mockMvc.perform(MockMvcRequestBuilders.get(String.format("/api/folder/%d", folder.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.folderId").value(folder.getId()))
                .andExpect(jsonPath("$.data.folderName").value(folder.getName()))
                .andExpect(jsonPath("$.data.parentFolder.folderId").value(folder.getParentFolder().getId()))
                .andExpect(jsonPath("$.data.subFolderList.length()").value(0))
                .andExpect(jsonPath("$.data.problemList.length()").value(folder.getProblemList().size()))
                .andExpect(jsonPath("$.data.problemList[0].problemId").value(folder.getProblemList().get(0).getId()))
                .andExpect(jsonPath("$.data.problemList[0].imageUrlList.length()").value(folder.getProblemList().get(0).getProblemImageDataList().size()))
                .andExpect(jsonPath("$.data.problemList[1].problemId").value(folder.getProblemList().get(1).getId()))
                .andExpect(jsonPath("$.data.problemList[1].imageUrlList.length()").value(folder.getProblemList().get(1).getProblemImageDataList().size()));
    }

    @Test
    @DisplayName("getRootFolder() api 테스트 - 루트 폴더가 존재할 경우")
    public void getRootFolderTest_Exist() throws Exception {
        //given
        Folder rootFolder = folderList.get(0);

        // when & then - 해당 폴더를 조회하는 API 호출
        mockMvc.perform(MockMvcRequestBuilders.get("/api/folder/root"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.folderId").value(rootFolder.getId()))
                .andExpect(jsonPath("$.data.folderName").value(rootFolder.getName()))
                .andExpect(jsonPath("$.data.parentFolder").isEmpty())
                .andExpect(jsonPath("$.data.subFolderList.length()").value(rootFolder.getSubFolderList().size()))
                .andExpect(jsonPath("$.data.problemList.length()").value(rootFolder.getProblemList().size()))
                .andExpect(jsonPath("$.data.subFolderList[0].folderId").value(rootFolder.getSubFolderList().get(0).getId()))
                .andExpect(jsonPath("$.data.subFolderList[0].folderName").value(rootFolder.getSubFolderList().get(0).getName()))
                .andExpect(jsonPath("$.data.subFolderList[1].folderId").value(rootFolder.getSubFolderList().get(1).getId()))
                .andExpect(jsonPath("$.data.subFolderList[1].folderName").value(rootFolder.getSubFolderList().get(1).getName()))
                .andExpect(jsonPath("$.data.problemList[0].problemId").value(rootFolder.getProblemList().get(0).getId()))
                .andExpect(jsonPath("$.data.problemList[0].imageUrlList.length()").value(rootFolder.getProblemList().get(0).getProblemImageDataList().size()))
                .andExpect(jsonPath("$.data.problemList[1].problemId").value(rootFolder.getProblemList().get(1).getId()))
                .andExpect(jsonPath("$.data.problemList[1].imageUrlList.length()").value(rootFolder.getProblemList().get(1).getProblemImageDataList().size()));

    }

    @Test
    @DisplayName("getRootFolder() api 테스트 - 루트 폴더가 존재하지 않을 경우")
    public void getRootFolderTest_NotExist() throws Exception {
        //given
        folderRepository.deleteAll();

        // when & then - 해당 폴더를 조회하는 API 호출
        mockMvc.perform(MockMvcRequestBuilders.get("/api/folder/root"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.folderId").isNotEmpty())
                .andExpect(jsonPath("$.data.folderName").isNotEmpty())
                .andExpect(jsonPath("$.data.parentFolder").isEmpty())
                .andExpect(jsonPath("$.data.subFolderList.length()").value(0))
                .andExpect(jsonPath("$.data.problemList.length()").value(0));
    }

    @Test
    @DisplayName("getAllUserFolderThumbnails() api 테스트")
    public void getAllUserFolderThumbnails_Test() throws Exception {
        //given

        // when
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/api/folder/thumbnails"))
                .andExpect(status().isOk())
                .andReturn();

        // then
        String content = result.getResponse().getContentAsString();

        for (int i = 0; i < folderList.size(); i++) {
            Folder folder = folderList.get(i);
            assertThat(folder.getId().intValue()).isEqualTo( JsonPath.read(content, "$.data[" + i + "].folderId"));
            assertThat(folder.getName()).isEqualTo(JsonPath.read(content, "$.data[" + i + "].folderName"));
        }
    }
}
