package com.aisip.OnO.backend.problem.integration;


import com.aisip.OnO.backend.auth.WithMockCustomUser;
import com.aisip.OnO.backend.fileupload.service.FileUploadService;
import com.aisip.OnO.backend.folder.dto.FolderRegisterDto;
import com.aisip.OnO.backend.folder.entity.Folder;
import com.aisip.OnO.backend.folder.repository.FolderRepository;
import com.aisip.OnO.backend.problem.dto.ProblemDeleteRequestDto;
import com.aisip.OnO.backend.problem.dto.ProblemImageDataRegisterDto;
import com.aisip.OnO.backend.problem.dto.ProblemRegisterDto;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problem.entity.ProblemImageData;
import com.aisip.OnO.backend.problem.entity.ProblemImageType;
import com.aisip.OnO.backend.problem.repository.ProblemImageDataRepository;
import com.aisip.OnO.backend.problem.repository.ProblemRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.assertj.core.api.Assertions;
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT) // 랜덤 포트로 애플리케이션 실행
@AutoConfigureMockMvc
class ProblemApiIntegrationTest {

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

        for (int f = 1; f <= 2; f++) {
            Folder folder = Folder.from(
                    new FolderRegisterDto("folder" + f, null, null),
                    null,
                    userId
            );
            folderRepository.save(folder);
            folderList.add(folder);

            // 문제 5개 생성 (폴더 1번에 3개, 폴더 2번에 2개)
            for (int i = 1; i <= 3; i++) {
                Problem problem = Problem.from(
                        new ProblemRegisterDto(
                                null,
                                "memo" + i,
                                "reference" + i,
                                (long) f,
                                LocalDateTime.now(),
                                new ArrayList<>()
                        ),
                        userId
                );
                problem.updateFolder(folder);
                problemRepository.save(problem);
                problemList.add(problem);

                // 이미지 2개씩 추가
                List<ProblemImageData> imageDataList = List.of(
                        ProblemImageData.from(new ProblemImageDataRegisterDto(problem.getId(), "url" + i * f + "_1", ProblemImageType.PROBLEM_IMAGE), problem),
                        ProblemImageData.from(new ProblemImageDataRegisterDto(problem.getId(), "url" + i * f + "_2", ProblemImageType.ANSWER_IMAGE), problem)
                );
                problemImageDataRepository.saveAll(imageDataList);

                problem.updateImageDataList(imageDataList);
            }
        }
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        problemList.clear();
        folderList.clear();

        problemImageDataRepository.deleteAll();
        problemRepository.deleteAll();
        folderRepository.deleteAll();
    }

    @Test
    @DisplayName("problemId를 사용해 문제를 조회하는 API 테스트")
    @WithMockCustomUser()
    void findProblem() throws Exception {
        // given
        Long problemId = problemList.get(0).getId();

        // when & then - 해당 문제를 조회하는 API 호출
        mockMvc.perform(MockMvcRequestBuilders.get(String.format("/api/problems/%d", problemId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.problemId").value(problemId))
                .andExpect(jsonPath("$.data.memo").value(problemList.get(0).getMemo()))
                .andExpect(jsonPath("$.data.reference").value(problemList.get(0).getReference()))
                .andExpect(jsonPath("$.data.solvedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.data.updatedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.imageUrlList.length()").value(2))
                .andExpect(jsonPath("$.data.imageUrlList[0].imageUrl").value(problemList.get(0).getProblemImageDataList().get(0).getImageUrl()))
                .andExpect(jsonPath("$.data.imageUrlList[1].imageUrl").value(problemList.get(0).getProblemImageDataList().get(1).getImageUrl()));
    }

    @Test
    @DisplayName("특정 유저의 모든 문제를 조회하는 API 테스트")
    @WithMockCustomUser()
    void findAllUserProblems() throws Exception {
        // given

        // when & then - 해당 문제를 조회하는 API 호출
        mockMvc.perform(MockMvcRequestBuilders.get("/api/problems/user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size()").value(problemList.size()))
                .andExpect(jsonPath("$.data[0].problemId").value(problemList.get(0).getId()))
                .andExpect(jsonPath("$.data[0].memo").value(problemList.get(0).getMemo()))
                .andExpect(jsonPath("$.data[0].reference").value(problemList.get(0).getReference()))
                .andExpect(jsonPath("$.data[0].solvedAt").isNotEmpty())
                .andExpect(jsonPath("$.data[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$.data[0].updatedAt").isNotEmpty())
                .andExpect(jsonPath("$.data[0].imageUrlList.length()").value(2))
                .andExpect(jsonPath("$.data[0].imageUrlList[0].imageUrl").value(problemList.get(0).getProblemImageDataList().get(0).getImageUrl()))
                .andExpect(jsonPath("$.data[0].imageUrlList[1].imageUrl").value(problemList.get(0).getProblemImageDataList().get(1).getImageUrl()));
    }

    @Test
    @DisplayName("특정 유저의 문제 개수 조회 API 테스트")
    @WithMockCustomUser()
    void findUserProblemCount() throws Exception {
        // given
        int count = problemList.size();

        // when & then - 해당 문제를 조회하는 API 호출
        mockMvc.perform(MockMvcRequestBuilders.get("/api/problems/problemCount"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(count));
    }

    @Test
    @DisplayName("특정 폴더의 모든 문제를 조회하는 API 테스트")
    @WithMockCustomUser()
    void findAllFolderProblems() throws Exception {
        // given
        List<Problem> problemList = problemRepository.findAllByUserId(userId);
        Long folderId = folderRepository.findAllByUserId(userId).get(0).getId();

        // when & then - 해당 문제를 조회하는 API 호출
        mockMvc.perform(MockMvcRequestBuilders.get(String.format("/api/problems/folder/%d", folderId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size()").value(3L))
                .andExpect(jsonPath("$.data[0].problemId").value(problemList.get(0).getId()))
                .andExpect(jsonPath("$.data[0].memo").value(problemList.get(0).getMemo()))
                .andExpect(jsonPath("$.data[0].reference").value(problemList.get(0).getReference()))
                .andExpect(jsonPath("$.data[0].solvedAt").isNotEmpty())
                .andExpect(jsonPath("$.data[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$.data[0].updatedAt").isNotEmpty())
                .andExpect(jsonPath("$.data[0].imageUrlList.length()").value(2))
                .andExpect(jsonPath("$.data[0].imageUrlList[0].imageUrl").value(problemList.get(0).getProblemImageDataList().get(0).getImageUrl()))
                .andExpect(jsonPath("$.data[0].imageUrlList[1].imageUrl").value(problemList.get(0).getProblemImageDataList().get(1).getImageUrl()));
    }

    @Test
    @DisplayName("문제 등록 API 테스트")
    @WithMockCustomUser()
    void registerProblem() throws Exception {
        // given
        List<ProblemImageDataRegisterDto> problemImageDataRegisterDtoList = List.of(
                new ProblemImageDataRegisterDto(
                        null,
                        "problemImageUrl",
                        ProblemImageType.PROBLEM_IMAGE
                ),
                new ProblemImageDataRegisterDto(
                        null,
                        "answerImageUrl",
                        ProblemImageType.ANSWER_IMAGE
                ),
                new ProblemImageDataRegisterDto(
                        null,
                        "solveImageUrl",
                        ProblemImageType.SOLVE_IMAGE
                )
        );
        ProblemRegisterDto problemRegisterDto = new ProblemRegisterDto(
                null,
                "memo",
                "reference",
                folderRepository.findAllByUserId(userId).get(0).getId(),
                LocalDateTime.now(),
                problemImageDataRegisterDtoList
        );

        // when & then
        mockMvc.perform(MockMvcRequestBuilders.post("/api/problems")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(problemRegisterDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("문제가 등록되었습니다.")); // 또는 반환값에 맞게 수정

        Problem problem = problemRepository.findAllByUserId(userId).get((int) (problemRepository.countByUserId(userId) - 1));

        Assertions.assertThat(problem.getMemo()).isEqualTo(problemRegisterDto.memo());
        Assertions.assertThat(problem.getReference()).isEqualTo(problemRegisterDto.reference());
        Assertions.assertThat(problem.getProblemImageDataList().size()).isEqualTo(3);
    }

    @Test
    @DisplayName("문제 이미지 등록 API 테스트")
    @WithMockCustomUser()
    @Transactional
    void registerProblemImageData() throws Exception {
        // given
        Long problemId = problemList.get(0).getId();
        ProblemImageDataRegisterDto problemImageDataRegisterDto = new ProblemImageDataRegisterDto(
                problemId,
                "solveImageUrl",
                ProblemImageType.SOLVE_IMAGE
        );

        // when & then
        mockMvc.perform(MockMvcRequestBuilders.post("/api/problems/imageData")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(problemImageDataRegisterDto)))
                .andExpect(status().isOk());

        Problem problem = problemRepository.findById(problemList.get(0).getId()).get();
        List<ProblemImageData> problemImageDataList = problem.getProblemImageDataList();

        Assertions.assertThat(problemImageDataList.size()).isEqualTo(3);
        Assertions.assertThat(problemImageDataList.get(problemImageDataList.size() - 1).getImageUrl()).isEqualTo("solveImageUrl");
    }

    @Test
    @DisplayName("문제 메모, 출처 수정")
    @WithMockCustomUser()
    void updateProblemInfo() throws Exception {
        // given
        Long problemId = problemList.get(0).getId();
        String updateMemo = "update memo";
        String updateReference = "update reference";
        ProblemRegisterDto problemRegisterDto = new ProblemRegisterDto(
                problemId,
                updateMemo,
                updateReference,
                null,
                null,
                null
        );

        // when & then
        mockMvc.perform(MockMvcRequestBuilders.patch("/api/problems/info")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(problemRegisterDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("문제가 수정되었습니다.")); // 또는 반환값에 맞게 수정

        Problem problem = problemRepository.findById(problemList.get(0).getId()).get();

        Assertions.assertThat(problem.getMemo()).isEqualTo(updateMemo);
        Assertions.assertThat(problem.getReference()).isEqualTo(updateReference);
    }

    @Test
    @DisplayName("문제 폴더 수정")
    @WithMockCustomUser()
    void updateProblemPath() throws Exception {
        // given
        Long problemId = problemList.get(0).getId();
        Long updateFolderId = folderList.get(1).getId();

        ProblemRegisterDto problemRegisterDto = new ProblemRegisterDto(
                problemId,
                null,
                null,
                updateFolderId,
                null,
                null
        );

        // when & then
        mockMvc.perform(MockMvcRequestBuilders.patch("/api/problems/path")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(problemRegisterDto)))
                .andExpect(status().isOk());

        Problem problem = problemRepository.findById(problemList.get(0).getId()).get();

        Assertions.assertThat(problem.getFolder().getId()).isEqualTo(updateFolderId);
    }

    @Test
    @DisplayName("문제 이미지 수정")
    @WithMockCustomUser()
    void updateProblemImageData() throws Exception {
        // given
        Long problemId = problemList.get(0).getId();

        ProblemRegisterDto problemRegisterDto = new ProblemRegisterDto(
                problemId,
                null,
                null,
                null,
                null,
                null
        );

        // when & then
        mockMvc.perform(MockMvcRequestBuilders.patch("/api/problems/path")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(problemRegisterDto)))
                .andExpect(status().isOk());

        Problem problem = problemRepository.findById(problemList.get(0).getId()).get();
    }

    @Test
    @DisplayName("문제 삭제 - 유저 모든 문제 삭제")
    @WithMockCustomUser()
    void deleteProblemsWithUserId() throws Exception {
        // given
        ProblemDeleteRequestDto problemDeleteRequestDto = new ProblemDeleteRequestDto(
                userId,
                null,
                null
        );
        doNothing().when(fileUploadService).deleteImageFileFromS3(anyString());

        // when & then
        mockMvc.perform(MockMvcRequestBuilders.delete("/api/problems")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(problemDeleteRequestDto)))
                .andExpect(status().isOk());

        Assertions.assertThat(problemRepository.findAllByUserId(userId).size()).isEqualTo(0);
    }
    @Test
    @DisplayName("문제 삭제 - 문제 id 사용")
    @WithMockCustomUser()
    void deleteProblemsWithProblemId() throws Exception {
        // given
        List<Long> deleteProblemIdList = List.of(problemList.get(0).getId(), problemList.get(1).getId(), problemList.get(4).getId());

        ProblemDeleteRequestDto problemDeleteRequestDto = new ProblemDeleteRequestDto(
                null,
                deleteProblemIdList,
                null
        );
        doNothing().when(fileUploadService).deleteImageFileFromS3(anyString());

        // when & then
        mockMvc.perform(MockMvcRequestBuilders.delete("/api/problems")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(problemDeleteRequestDto)))
                .andExpect(status().isOk());

        Assertions.assertThat(problemRepository.findAllByUserId(userId).size()).isEqualTo(3L);
    }

    @Test
    @DisplayName("문제 삭제 - folder id 사용")
    @WithMockCustomUser()
    void deleteProblemsWithFolderId() throws Exception {
        // given
        List<Long> folderIdList = List.of(folderList.get(0).getId());

        ProblemDeleteRequestDto problemDeleteRequestDto = new ProblemDeleteRequestDto(
                null,
                null,
                folderIdList
        );
        doNothing().when(fileUploadService).deleteImageFileFromS3(anyString());

        // when & then
        mockMvc.perform(MockMvcRequestBuilders.delete("/api/problems")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(problemDeleteRequestDto)))
                .andExpect(status().isOk());

        Assertions.assertThat(problemRepository.findAllByUserId(userId).size()).isEqualTo(3L);
    }
}
