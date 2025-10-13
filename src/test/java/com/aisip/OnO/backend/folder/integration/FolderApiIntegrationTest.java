package com.aisip.OnO.backend.folder.integration;

import com.aisip.OnO.backend.folder.dto.FolderDeleteRequestDto;
import com.aisip.OnO.backend.util.fileupload.service.FileUploadService;
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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
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
         root
        /    \
        0     1
        | \   |
        2  3  4
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
                    userId
            );
            problem.updateFolder(targetFolder);
            problem = problemRepository.save(problem);

            List<ProblemImageData> imageDataList = new ArrayList<>();
            for (int j = 1; j <= 3; j++){
                ProblemImageDataRegisterDto problemImageDataRegisterDto = new ProblemImageDataRegisterDto(
                        (long) i,
                        "http://example.com/problemId/" + i + "/image" + j,
                        ProblemImageType.valueOf(j)
                );

                ProblemImageData imageData = ProblemImageData.from(problemImageDataRegisterDto);
                imageData.updateProblem(problem);
                problemImageDataRepository.save(imageData);
            }
            problemList.add(problem);
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
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get(String.format("/api/folders/%d", folder.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.folderId").value(folder.getId()))
                .andExpect(jsonPath("$.data.folderName").value(folder.getName()))
                .andExpect(jsonPath("$.data.parentFolder").isEmpty())
                .andExpect(jsonPath("$.data.subFolderList.length()").value(folder.getSubFolderList().size()))
                .andExpect(jsonPath("$.data.subFolderList[0].folderId").value(folder.getSubFolderList().get(0).getId()))
                .andExpect(jsonPath("$.data.subFolderList[0].folderName").value(folder.getSubFolderList().get(0).getName()))
                .andExpect(jsonPath("$.data.subFolderList[1].folderId").value(folder.getSubFolderList().get(1).getId()))
                .andExpect(jsonPath("$.data.subFolderList[1].folderName").value(folder.getSubFolderList().get(1).getName()))
                .andExpect(jsonPath("$.data.problemIdList.length()").value(folder.getProblemList().size()))
                .andExpect(jsonPath("$.data.problemIdList[0]").value(folder.getProblemList().get(0).getId()))
                .andReturn();

        String json = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        System.out.println("==== 응답 결과 ====");
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(objectMapper.readTree(json)));
    }

    @Test
    @DisplayName("getFolder() api 테스트 - internal folder")
    public void getFolderTest_Internal() throws Exception {
        //given
        Folder folder = folderList.get(1);

        // when & then - 해당 폴더를 조회하는 API 호출
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get(String.format("/api/folders/%d", folder.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.folderId").value(folder.getId()))
                .andExpect(jsonPath("$.data.folderName").value(folder.getName()))
                .andExpect(jsonPath("$.data.parentFolder.folderId").value(folder.getParentFolder().getId()))
                .andExpect(jsonPath("$.data.subFolderList.length()").value(folder.getSubFolderList().size()))
                .andExpect(jsonPath("$.data.subFolderList[0].folderId").value(folder.getSubFolderList().get(0).getId()))
                .andExpect(jsonPath("$.data.subFolderList[0].folderName").value(folder.getSubFolderList().get(0).getName()))
                .andExpect(jsonPath("$.data.subFolderList[1].folderId").value(folder.getSubFolderList().get(1).getId()))
                .andExpect(jsonPath("$.data.subFolderList[1].folderName").value(folder.getSubFolderList().get(1).getName()))
                .andExpect(jsonPath("$.data.problemIdList.length()").value(folder.getProblemList().size()))
                .andExpect(jsonPath("$.data.problemIdList[0]").value(folder.getProblemList().get(0).getId()))
                .andReturn();

        String json = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        System.out.println("==== 응답 결과 ====");
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(objectMapper.readTree(json)));
    }

    @Test
    @DisplayName("getFolder() api 테스트 - external folder")
    public void getFolderTest_External() throws Exception {
        //given
        Folder folder = folderList.get(folderList.size() - 1);

        // when & then - 해당 폴더를 조회하는 API 호출
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get(String.format("/api/folders/%d", folder.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.folderId").value(folder.getId()))
                .andExpect(jsonPath("$.data.folderName").value(folder.getName()))
                .andExpect(jsonPath("$.data.parentFolder.folderId").value(folder.getParentFolder().getId()))
                .andExpect(jsonPath("$.data.subFolderList.length()").value(0))
                .andExpect(jsonPath("$.data.problemIdList.length()").value(folder.getProblemList().size()))
                .andExpect(jsonPath("$.data.problemIdList[0]").value(folder.getProblemList().get(0).getId()))
                .andReturn();

        String json = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        System.out.println("==== 응답 결과 ====");
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(objectMapper.readTree(json)));
    }

    @Test
    @DisplayName("getRootFolder() api 테스트 - 루트 폴더가 존재할 경우")
    public void getRootFolderTest_Exist() throws Exception {
        //given
        Folder rootFolder = folderList.get(0);

        // when & then - 해당 폴더를 조회하는 API 호출
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/api/folders/root"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.folderId").value(rootFolder.getId()))
                .andExpect(jsonPath("$.data.folderName").value(rootFolder.getName()))
                .andExpect(jsonPath("$.data.parentFolder").isEmpty())
                .andExpect(jsonPath("$.data.subFolderList.length()").value(rootFolder.getSubFolderList().size()))
                .andExpect(jsonPath("$.data.problemIdList.length()").value(rootFolder.getProblemList().size()))
                .andExpect(jsonPath("$.data.subFolderList[0].folderId").value(rootFolder.getSubFolderList().get(0).getId()))
                .andExpect(jsonPath("$.data.subFolderList[0].folderName").value(rootFolder.getSubFolderList().get(0).getName()))
                .andExpect(jsonPath("$.data.subFolderList[1].folderId").value(rootFolder.getSubFolderList().get(1).getId()))
                .andExpect(jsonPath("$.data.subFolderList[1].folderName").value(rootFolder.getSubFolderList().get(1).getName()))
                .andExpect(jsonPath("$.data.problemIdList[0]").value(rootFolder.getProblemList().get(0).getId()))
                .andReturn();

        String json = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        System.out.println("==== 응답 결과 ====");
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(objectMapper.readTree(json)));
    }

    @Test
    @DisplayName("getRootFolder() api 테스트 - 루트 폴더가 존재하지 않을 경우")
    public void getRootFolderTest_NotExist() throws Exception {
        //given
        folderRepository.deleteAll();

        // when & then - 해당 폴더를 조회하는 API 호출
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/api/folders/root"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.folderId").isNotEmpty())
                .andExpect(jsonPath("$.data.folderName").isNotEmpty())
                .andExpect(jsonPath("$.data.parentFolder").isEmpty())
                .andExpect(jsonPath("$.data.subFolderList.length()").value(0))
                .andExpect(jsonPath("$.data.problemIdList.length()").value(0))
                .andReturn();

        String json = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        System.out.println("==== 응답 결과 ====");
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(objectMapper.readTree(json)));
    }

    @Test
    @DisplayName("getAllUserFolderThumbnails() api 테스트")
    public void getAllUserFolderThumbnails_Test() throws Exception {
        //given

        // when
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/api/folders/thumbnails"))
                .andExpect(status().isOk())
                .andReturn();

        // then
        String content = result.getResponse().getContentAsString(StandardCharsets.UTF_8);

        for (int i = 0; i < folderList.size(); i++) {
            Folder folder = folderList.get(i);
            assertThat(folder.getId().intValue()).isEqualTo( JsonPath.read(content, "$.data[" + i + "].folderId"));
            assertThat(folder.getName()).isEqualTo(JsonPath.read(content, "$.data[" + i + "].folderName"));
        }

        System.out.println("==== 응답 결과 ====");
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(objectMapper.readTree(content)));
    }

    @Test
    @DisplayName("getAllUserFolderDetails() api 테스트")
    public void getAllUserFolderDetails_Test() throws Exception {
        //given

        // when
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/api/folders"))
                .andExpect(status().isOk())
                .andReturn();

        // then
        String content = result.getResponse().getContentAsString(StandardCharsets.UTF_8);

        for (int i = 0; i < folderList.size(); i++) {
            Folder folder = folderList.get(i);
            assertThat(folder.getId().intValue()).isEqualTo(JsonPath.read(content, "$.data[" + i + "].folderId"));
            assertThat(folder.getName()).isEqualTo(JsonPath.read(content, "$.data[" + i + "].folderName"));
            if (folder.getParentFolder() != null) {
                assertThat(folder.getParentFolder().getId().intValue()).isEqualTo(JsonPath.read(content, "$.data[" + i + "].parentFolder.folderId"));
            } else{
                assertThat(folder.getParentFolder()).isEqualTo(JsonPath.read(content, "$.data[" + i + "].parentFolder"));
            }
            assertThat(folder.getSubFolderList().size()).isEqualTo(JsonPath.read(content, "$.data[" + i + "].subFolderList.length()"));
            assertThat(folder.getProblemList().size()).isEqualTo(JsonPath.read(content, "$.data[" + i + "].problemIdList.length()"));
        }

        System.out.println("==== 응답 결과 ====");
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(objectMapper.readTree(content)));
    }

    @Test
    @DisplayName("createFolder() api 테스트")
    public void createFolderTest() throws Exception {
        //given
        String folderName = "new Folder";
        FolderRegisterDto folderRegisterDto = new FolderRegisterDto(
                folderName,
                null,
                folderList.get(0).getId()
        );

        // when
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/api/folders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(folderRegisterDto)))
                .andExpect(status().isOk())
                .andReturn();

        // then
        List<Folder> folders = folderRepository.findAll();
        Folder folder = folders.get(folders.size() - 1);
        assertThat(folder.getName()).isEqualTo(folderName);
        assertThat(folder.getParentFolder().getId()).isEqualTo(folderList.get(0).getId());

        String json = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        System.out.println("==== 응답 결과 ====");
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(objectMapper.readTree(json)));
    }

    @Test
    @DisplayName("updateFolder() api 테스트 - 이름 변경")
    public void updateFolderTest_FolderName() throws Exception {
        //given
        Long folderId = folderList.get(0).getId();
        String folderName = "new FolderName";
        FolderRegisterDto folderRegisterDto = new FolderRegisterDto(
                folderName,
                folderId,
                null
        );

        // when
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.patch("/api/folders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(folderRegisterDto)))
                .andExpect(status().isOk())
                .andReturn();

        // then
        Folder folder = folderRepository.findById(folderId).get();
        assertThat(folder.getName()).isEqualTo(folderName);

        String json = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        System.out.println("==== 응답 결과 ====");
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(objectMapper.readTree(json)));
    }

    @Test
    @DisplayName("updateFolder() api 테스트 - 부모 폴더 변경")
    public void updateFolderTest_ParentFolder() throws Exception {
        //given
        Long oldParentFolderId = folderList.get(0).getId();
        Long folderId = folderList.get(1).getId();
        Long newParentFolderId = folderList.get(2).getId();
        FolderRegisterDto folderRegisterDto = new FolderRegisterDto(
                null,
                folderId,
                newParentFolderId
        );

        // when
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.patch("/api/folders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(folderRegisterDto)))
                .andExpect(status().isOk())
                .andReturn();

        // then
        Folder folder = folderRepository.findById(folderId).get();

        assertThat(folder.getParentFolder().getId()).isEqualTo(newParentFolderId);

        String json = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        System.out.println("==== 응답 결과 ====");
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(objectMapper.readTree(json)));
    }


    @Test
    @DisplayName("deleteFolderWithProblems() api 테스트 - 중간 폴더 단일 삭제")
    public void deleteFolderWithProblemsTest_SingleFolder() throws Exception {
        //given
        FolderDeleteRequestDto folderDeleteRequestDto = new FolderDeleteRequestDto(
                List.of(folderList.get(1).getId())
        );
        doNothing().when(fileUploadService).deleteImageFileFromS3(anyString());

        // when
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.delete("/api/folders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(folderDeleteRequestDto)))
                .andExpect(status().isOk())
                .andReturn();

        String json = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        System.out.println("==== 응답 결과 ====");
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(objectMapper.readTree(json)));

        // then
        assertThat(folderRepository.findAll().size()).isEqualTo(3);
    }


    @Test
    @DisplayName("deleteFolderWithProblems() api 테스트 - 중간 폴더 모두 삭제")
    public void deleteFolderWithProblemsTest_MultipleFolder() throws Exception {
        //given
        FolderDeleteRequestDto folderDeleteRequestDto = new FolderDeleteRequestDto(
                List.of(folderList.get(1).getId(), folderList.get(2).getId())
        );
        doNothing().when(fileUploadService).deleteImageFileFromS3(anyString());

        // when
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.delete("/api/folders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(folderDeleteRequestDto)))
                .andExpect(status().isOk())
                .andReturn();

        String json = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        System.out.println("==== 응답 결과 ====");
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(objectMapper.readTree(json)));

        // then
        assertThat(folderRepository.findAll().size()).isEqualTo(1);
    }

    @Test
    @DisplayName("deleteFolderWithProblems() api 테스트 - 유저 폴더 모두 삭제")
    public void deleteFolderWithProblemsTest_UserFolders() throws Exception {
        //given
        FolderDeleteRequestDto folderDeleteRequestDto = new FolderDeleteRequestDto(
                null
        );
        doNothing().when(fileUploadService).deleteImageFileFromS3(anyString());

        // when
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.delete("/api/folders/all")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(folderDeleteRequestDto)))
                .andExpect(status().isOk())
                .andReturn();

        String json = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        System.out.println("==== 응답 결과 ====");
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(objectMapper.readTree(json)));

        // then
        assertThat(folderRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("deleteFolderWithProblems() api 테스트 - 존재하지 않는 폴더를 제거할 떄")
    public void deleteFolderWithProblemsTest_FolderNotExist() throws Exception {

        FolderDeleteRequestDto folderDeleteRequestDto = new FolderDeleteRequestDto(
                List.of(999L)
        );
        doNothing().when(fileUploadService).deleteImageFileFromS3(anyString());

        // when & then
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.delete("/api/folders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(folderDeleteRequestDto)))
                .andExpect(status().is4xxClientError())
                .andReturn();

        String json = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        System.out.println("==== 응답 결과 ====");
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(objectMapper.readTree(json)));
    }
}
