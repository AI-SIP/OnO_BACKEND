package com.aisip.OnO.backend.problem.integration;


import com.aisip.OnO.backend.auth.WithMockCustomUser;
import com.aisip.OnO.backend.user.entity.User;
import com.aisip.OnO.backend.user.repository.UserRepository;
import com.aisip.OnO.backend.util.RandomFolderGenerator;
import com.aisip.OnO.backend.util.RandomProblemGenerator;
import com.aisip.OnO.backend.util.RandomUserGenerator;
import com.aisip.OnO.backend.util.fileupload.service.FileUploadService;
import com.aisip.OnO.backend.folder.entity.Folder;
import com.aisip.OnO.backend.folder.repository.FolderRepository;
import com.aisip.OnO.backend.problem.dto.AddProblemImageUrlsRequest;
import com.aisip.OnO.backend.problem.dto.ProblemDeleteRequestDto;
import com.aisip.OnO.backend.problem.dto.ProblemRegisterDto;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problem.entity.ProblemAnalysis;
import com.aisip.OnO.backend.problem.entity.ProblemImageData;
import com.aisip.OnO.backend.problem.entity.ProblemImageType;
import com.aisip.OnO.backend.problem.reminder.ProblemReviewReminder;
import com.aisip.OnO.backend.problem.reminder.ProblemReviewReminderRepository;
import com.aisip.OnO.backend.problem.reminder.ProblemReviewReminderStatus;
import com.aisip.OnO.backend.problem.repository.ProblemAnalysisRepository;
import com.aisip.OnO.backend.problem.repository.ProblemImageDataRepository;
import com.aisip.OnO.backend.problem.repository.ProblemRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT) // 랜덤 포트로 애플리케이션 실행
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProblemApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private ProblemImageDataRepository problemImageDataRepository;

    @Autowired
    private ProblemAnalysisRepository problemAnalysisRepository;

    @Autowired
    private ProblemReviewReminderRepository reminderRepository;

    @Autowired
    private FolderRepository folderRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private FileUploadService fileUploadService;

    private Long userId;

    private List<Folder> folderList;

    private List<Problem> problemList;

    private List<ProblemImageData> problemImageDataList;

    @BeforeEach
    void setUp() {
        User user = RandomUserGenerator.createRandomUser();
        userRepository.save(user);
        userId = user.getId();

        // 인증 설정
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        userId, null, List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))
                )
        );

        folderList = new ArrayList<>();
        problemList = new ArrayList<>();
        problemImageDataList = new ArrayList<>();

        for (int f = 1; f <= 2; f++) {
            Folder folder = RandomFolderGenerator.createRandomFolder(userId);
            folderRepository.save(folder);
            folderList.add(folder);

            // 문제 5개 생성 (폴더 1번에 3개, 폴더 2번에 2개)
            for (int i = 1; i <= 3; i++) {
                Problem problem = RandomProblemGenerator.createRandomProblemWithFolder(folder, userId);
                problem.updateFolder(folder);
                problemRepository.save(problem);
                ProblemAnalysis analysis = ProblemAnalysis.createSkipped(problem);
                problem.updateProblemAnalysis(analysis);
                problemAnalysisRepository.save(analysis);

                List<ProblemImageData> imageDataList = RandomProblemGenerator.createDefaultProblemImageDataList(problem.getId());

                imageDataList.forEach(imageData -> {
                    imageData.updateProblem(problem);
                    ProblemImageData saveImageData = problemImageDataRepository.save(imageData);
                    problemImageDataList.add(saveImageData);
                });

                problemList.add(problem);
            }
        }
    }

    private void authenticateAsFixtureUser() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        userId, null, List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))
                )
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        reminderRepository.deleteAll();
        problemAnalysisRepository.deleteAll();
        problemImageDataRepository.deleteAll(problemImageDataList);
        problemRepository.deleteAll(problemList);
        folderRepository.deleteAll(folderList);
        userRepository.deleteById(userId);

        problemImageDataList.clear();
        problemList.clear();
        folderList.clear();
    }

    @Test
    @DisplayName("problemId를 사용해 문제를 조회하는 API 테스트")
    @WithMockCustomUser()
    void findProblem() throws Exception {
        // given
        Long problemId = problemList.get(0).getId();

        // when & then - 해당 문제를 조회하는 API 호출
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get(String.format("/api/problems/%d", problemId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.problemId").value(problemId))
                .andExpect(jsonPath("$.data.memo").value(problemList.get(0).getMemo()))
                .andExpect(jsonPath("$.data.reference").value(problemList.get(0).getReference()))
                .andExpect(jsonPath("$.data.solvedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.data.updatedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.imageUrlList.length()").value(problemList.get(0).getProblemImageDataList().size()))
                .andExpect(jsonPath("$.data.imageUrlList[0].imageUrl").value(problemList.get(0).getProblemImageDataList().get(0).getImageUrl()))
                .andExpect(jsonPath("$.data.imageUrlList[1].imageUrl").value(problemList.get(0).getProblemImageDataList().get(1).getImageUrl()))
                .andExpect(status().is2xxSuccessful())
                .andReturn();

        String json = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode root = objectMapper.readTree(json);
        JsonNode dataNode = root.get("data");

        System.out.println("==== 응답 결과 ====");
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(objectMapper.readTree(json)));
    }

    @Test
    @DisplayName("특정 유저의 모든 문제를 조회하는 API 테스트")
    @WithMockCustomUser()
    void findAllUserProblems() throws Exception {
        // given

        // when & then - 해당 문제를 조회하는 API 호출
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/api/problems/user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size()").value(problemList.size()))
                .andExpect(jsonPath("$.data[0].problemId").value(problemList.get(0).getId()))
                .andExpect(jsonPath("$.data[0].memo").value(problemList.get(0).getMemo()))
                .andExpect(jsonPath("$.data[0].reference").value(problemList.get(0).getReference()))
                .andExpect(jsonPath("$.data[0].solvedAt").isNotEmpty())
                .andExpect(jsonPath("$.data[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$.data[0].updatedAt").isNotEmpty())
                .andExpect(jsonPath("$.data[0].imageUrlList.length()").value(problemList.get(0).getProblemImageDataList().size()))
                .andExpect(jsonPath("$.data[0].imageUrlList[0].imageUrl").value(problemList.get(0).getProblemImageDataList().get(0).getImageUrl()))
                .andExpect(jsonPath("$.data[0].imageUrlList[1].imageUrl").value(problemList.get(0).getProblemImageDataList().get(1).getImageUrl()))
                .andExpect(status().is2xxSuccessful())
                .andReturn();

        String json = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode root = objectMapper.readTree(json);
        JsonNode dataNode = root.get("data");

        System.out.println("==== 응답 결과 ====");
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(objectMapper.readTree(json)));
    }

    @Test
    @DisplayName("특정 유저의 문제 개수 조회 API 테스트")
    @WithMockCustomUser()
    void findUserProblemCount() throws Exception {
        // given
        int count = problemList.size();

        // when & then - 해당 문제를 조회하는 API 호출
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/api/problems/problemCount"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(count))
                .andReturn();

        String json = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode root = objectMapper.readTree(json);
        JsonNode dataNode = root.get("data");

        System.out.println("==== 응답 결과 ====");
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(objectMapper.readTree(json)));
    }

    @Test
    @DisplayName("특정 폴더의 모든 문제를 조회하는 API 테스트")
    @WithMockCustomUser()
    void findAllFolderProblems() throws Exception {
        // given
        List<Problem> problemList = problemRepository.findAllByUserId(userId);
        Long folderId = folderRepository.findAllByUserId(userId).get(0).getId();

        // when & then - 해당 문제를 조회하는 API 호출
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get(String.format("/api/problems/folder/%d", folderId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size()").value(3L))
                .andExpect(jsonPath("$.data[0].problemId").value(problemList.get(0).getId()))
                .andExpect(jsonPath("$.data[0].memo").value(problemList.get(0).getMemo()))
                .andExpect(jsonPath("$.data[0].reference").value(problemList.get(0).getReference()))
                .andExpect(jsonPath("$.data[0].solvedAt").isNotEmpty())
                .andExpect(jsonPath("$.data[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$.data[0].updatedAt").isNotEmpty())
                .andExpect(jsonPath("$.data[0].imageUrlList.length()").value(problemList.get(0).getProblemImageDataList().size()))
                .andExpect(jsonPath("$.data[0].imageUrlList[0].imageUrl").value(problemList.get(0).getProblemImageDataList().get(0).getImageUrl()))
                .andExpect(jsonPath("$.data[0].imageUrlList[1].imageUrl").value(problemList.get(0).getProblemImageDataList().get(1).getImageUrl()))
                .andReturn();

        String json = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode root = objectMapper.readTree(json);
        JsonNode dataNode = root.get("data");

        System.out.println("==== 응답 결과 ====");
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(objectMapper.readTree(json)));
    }

    @Test
    @DisplayName("문제 등록 API 테스트")
    @WithMockCustomUser()
    void registerProblem() throws Exception {
        // given
        ProblemRegisterDto problemRegisterDto = new ProblemRegisterDto(
                null,
                "memo",
                "reference",
                folderRepository.findAllByUserId(userId).get(0).getId(),
                LocalDateTime.now()
        );

        // when & then
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/api/problems")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(problemRegisterDto)))
                .andExpect(status().isOk())
                .andReturn();

        String json = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode root = objectMapper.readTree(json);
        JsonNode dataNode = root.get("data");

        System.out.println("==== 응답 결과 ====");
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(objectMapper.readTree(json)));

        Problem problem = problemRepository.findAllByUserId(userId).get((int) (problemRepository.countByUserId(userId) - 1));

        assertThat(problem.getMemo()).isEqualTo(problemRegisterDto.memo());
        assertThat(problem.getReference()).isEqualTo(problemRegisterDto.reference());
        assertThat(problem.getProblemImageDataList()).isEmpty();
    }

    @Test
    @DisplayName("문제 등록 - memo 1000자는 저장되고 reminder 스냅샷은 255자로 잘린다")
    @WithMockCustomUser()
    void registerProblemWithLongMemo() throws Exception {
        // given
        String longMemo = "가".repeat(1000);
        ProblemRegisterDto problemRegisterDto = new ProblemRegisterDto(
                null,
                longMemo,
                "reference",
                folderRepository.findAllByUserId(userId).get(0).getId(),
                LocalDateTime.now()
        );

        // when
        mockMvc.perform(MockMvcRequestBuilders.post("/api/problems")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(problemRegisterDto)))
                .andExpect(status().isOk());

        // then
        Problem problem = problemRepository.findAllByUserId(userId)
                .get((int) (problemRepository.countByUserId(userId) - 1));
        assertThat(problem.getMemo()).isEqualTo(longMemo);

        List<ProblemReviewReminder> reminders = reminderRepository.findAll().stream()
                .filter(reminder -> reminder.getProblemId().equals(problem.getId()))
                .toList();
        assertThat(reminders).isNotEmpty();
        assertThat(reminders).allSatisfy(reminder ->
                assertThat(reminder.getProblemMemoSnapshot()).hasSize(255));
    }

    @Test
    @DisplayName("문제 등록 - memo 가 1000자를 넘으면 500 이 아니라 400 으로 거절한다")
    @WithMockCustomUser()
    void registerProblemWithTooLongMemo() throws Exception {
        // given
        ProblemRegisterDto problemRegisterDto = new ProblemRegisterDto(
                null,
                "가".repeat(1001),
                "reference",
                folderRepository.findAllByUserId(userId).get(0).getId(),
                LocalDateTime.now()
        );

        // when & then
        mockMvc.perform(MockMvcRequestBuilders.post("/api/problems")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(problemRegisterDto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(4006));
    }

    @Test
    @DisplayName("문제 이미지 등록 API 테스트")
    @WithMockCustomUser()
    void registerProblemImageData() throws Exception {
        // given
        authenticateAsFixtureUser();
        Long problemId = problemList.get(0).getId();
        int beforeCount = problemImageDataRepository.findAllByProblemId(problemId).size();
        AddProblemImageUrlsRequest request = new AddProblemImageUrlsRequest(List.of(
                new AddProblemImageUrlsRequest.ImageUrlItem("problemImageUrl", ProblemImageType.PROBLEM_IMAGE.name())
        ));

        // when & then
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/api/problems/{problemId}/imageData/urls", problemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        String json = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode root = objectMapper.readTree(json);
        JsonNode dataNode = root.get("data");

        System.out.println("==== 응답 결과 ====");
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(objectMapper.readTree(json)));

        Optional<Problem> optionalProblem = problemRepository.findProblemWithImageData(problemId);
        assertThat(optionalProblem.isPresent()).isTrue();

        Problem problem = optionalProblem.get();
        List<ProblemImageData> problemImageDataList = problem.getProblemImageDataList();
        assertThat(problemImageDataList).hasSize(beforeCount + 1);
        assertThat(problemImageDataList)
                .extracting(ProblemImageData::getImageUrl)
                .contains("problemImageUrl");
    }

    @Test
    @DisplayName("문제 이미지 등록 API 테스트 - 당일 복습 이미지 중복 등록 시 예외 발생")
    @WithMockCustomUser()
    void registerProblemImageDataDuplicateSolveImage() throws Exception {
        // given
        authenticateAsFixtureUser();
        Problem problem = RandomProblemGenerator.createRandomProblemWithFolder(folderList.get(0), userId);
        problem.updateFolder(folderList.get(0));
        problemRepository.save(problem);
        ProblemAnalysis analysis = ProblemAnalysis.createSkipped(problem);
        problem.updateProblemAnalysis(analysis);
        problemAnalysisRepository.save(analysis);
        problemList.add(problem);
        Long problemId = problem.getId();

        // 첫 번째 복습 이미지 등록
        AddProblemImageUrlsRequest firstSolveImage = new AddProblemImageUrlsRequest(List.of(
                new AddProblemImageUrlsRequest.ImageUrlItem("solveImageUrl1", ProblemImageType.SOLVE_IMAGE.name())
        ));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/problems/{problemId}/imageData/urls", problemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(firstSolveImage)))
                .andExpect(status().isOk());

        // 같은 날 두 번째 복습 이미지 등록 시도
        AddProblemImageUrlsRequest secondSolveImage = new AddProblemImageUrlsRequest(List.of(
                new AddProblemImageUrlsRequest.ImageUrlItem("solveImageUrl2", ProblemImageType.SOLVE_IMAGE.name())
        ));

        // when & then - 예외 발생 확인
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/api/problems/{problemId}/imageData/urls", problemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(secondSolveImage)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(4003))
                .andExpect(jsonPath("$.message").value("이미 오늘의 복습을 완료한 문제입니다."))
                .andReturn();

        String json = result.getResponse().getContentAsString(StandardCharsets.UTF_8);

        System.out.println("==== 응답 결과 (예외) ====");
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(objectMapper.readTree(json)));

        // 복습 이미지가 1개만 등록되었는지 확인
        Optional<Problem> optionalProblem = problemRepository.findProblemWithImageData(problemId);
        assertThat(optionalProblem.isPresent()).isTrue();

        Problem updatedProblem = optionalProblem.get();
        long solveImageCount = updatedProblem.getProblemImageDataList().stream()
                .filter(imageData -> imageData.getProblemImageType().equals(ProblemImageType.SOLVE_IMAGE))
                .count();

        assertThat(solveImageCount).isEqualTo(1);
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
                null
        );

        // when & then
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.patch("/api/problems/info")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(problemRegisterDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("문제가 수정되었습니다."))
                .andReturn();

        String json = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode root = objectMapper.readTree(json);
        JsonNode dataNode = root.get("data");

        System.out.println("==== 응답 결과 ====");
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(objectMapper.readTree(json)));

        Problem problem = problemRepository.findById(problemList.get(0).getId()).get();

        assertThat(problem.getMemo()).isEqualTo(updateMemo);
        assertThat(problem.getReference()).isEqualTo(updateReference);
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
                null
        );

        // when & then
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.patch("/api/problems/path")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(problemRegisterDto)))
                .andExpect(status().isOk())
                .andReturn();

        String json = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode root = objectMapper.readTree(json);
        JsonNode dataNode = root.get("data");

        System.out.println("==== 응답 결과 ====");
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(objectMapper.readTree(json)));

        Problem problem = problemRepository.findById(problemList.get(0).getId()).get();

        assertThat(problem.getFolder().getId()).isEqualTo(updateFolderId);
    }

    @Test
    @DisplayName("문제 이미지 URL 추가")
    @WithMockCustomUser()
    void addProblemImageDataUrls() throws Exception {
        // given
        authenticateAsFixtureUser();
        Long problemId = problemList.get(0).getId();
        int beforeCount = problemImageDataRepository.findAllByProblemId(problemId).size();
        AddProblemImageUrlsRequest request = new AddProblemImageUrlsRequest(List.of(
                new AddProblemImageUrlsRequest.ImageUrlItem("problemImageUrl1", ProblemImageType.PROBLEM_IMAGE.name()),
                new AddProblemImageUrlsRequest.ImageUrlItem("answerImageUrl2", ProblemImageType.ANSWER_IMAGE.name())
        ));

        // when & then
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/api/problems/{problemId}/imageData/urls", problemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        String json = result.getResponse().getContentAsString(StandardCharsets.UTF_8);

        System.out.println("==== 응답 결과 ====");
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(objectMapper.readTree(json)));

        List<ProblemImageData> imageDataList = problemImageDataRepository.findAllByProblemId(problemId);
        assertThat(imageDataList).hasSize(beforeCount + 2);
        assertThat(imageDataList)
                .extracting(ProblemImageData::getImageUrl)
                .contains("problemImageUrl1", "answerImageUrl2");
    }

    @Test
    @DisplayName("문제 삭제 - 문제 id 사용")
    @WithMockCustomUser()
    void deleteProblemsWithProblemId() throws Exception {
        // given
        List<Long> deleteProblemIdList = List.of(problemList.get(0).getId(), problemList.get(1).getId(), problemList.get(4).getId());
        ProblemDeleteRequestDto problemDeleteRequestDto = new ProblemDeleteRequestDto(deleteProblemIdList);

        doNothing().when(fileUploadService).deleteImageFileFromS3(anyString());

        // when & then
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.delete("/api/problems")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(problemDeleteRequestDto)))
                .andExpect(status().isOk())
                .andReturn();

        String json = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode root = objectMapper.readTree(json);
        JsonNode dataNode = root.get("data");

        System.out.println("==== 응답 결과 ====");
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(objectMapper.readTree(json)));

        assertThat(problemRepository.findAllByUserId(userId).size()).isEqualTo(3L);
    }

    @Test
    @DisplayName("문제 삭제 - 유저 모든 문제 삭제")
    @WithMockCustomUser()
    void deleteProblemsWithUserId() throws Exception {
        // given
        doNothing().when(fileUploadService).deleteImageFileFromS3(anyString());

        // when & then
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.delete("/api/problems/all")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        String json = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode root = objectMapper.readTree(json);
        JsonNode dataNode = root.get("data");

        System.out.println("==== 응답 결과 ====");
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(objectMapper.readTree(json)));

        assertThat(problemRepository.findAllByUserId(userId).size()).isEqualTo(0);
    }

    // ──────────── 리마인더 통합 테스트 ────────────

    @Test
    @DisplayName("POST /api/problems 성공 후 DB에 reminder row 5개가 SCHEDULED 상태로 생성된다")
    void registerProblem_createsReminderRows() throws Exception {
        // given: setUp에서 저장한 userId를 SecurityContext에 세팅
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        userId, null, List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))
                )
        );
        Long folderId = folderList.get(0).getId();
        ProblemRegisterDto dto = new ProblemRegisterDto(
                null, "remind-memo", "remind-ref", folderId, LocalDateTime.now()
        );

        // when
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/api/problems")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andReturn();

        // 응답에서 problemId 추출
        String json = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode root = objectMapper.readTree(json);
        Long createdProblemId = root.get("data").asLong();

        // then: AFTER_COMMIT 이벤트가 이미 처리되었으므로 row가 바로 존재해야 함
        List<ProblemReviewReminder> reminderRows = reminderRepository.findAll().stream()
                .filter(r -> r.getProblemId().equals(createdProblemId))
                .toList();

        assertThat(reminderRows).hasSize(5);
        assertThat(reminderRows).allMatch(r -> r.getStatus() == ProblemReviewReminderStatus.SCHEDULED);
    }

    @Test
    @DisplayName("PATCH /api/problems/info 후 reminder SCHEDULED row의 snapshot이 새 값으로 갱신된다")
    void updateProblemInfo_updatesReminderSnapshot() throws Exception {
        // given
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        userId, null, List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))
                )
        );
        Problem targetProblem = problemList.get(0);
        Long problemId = targetProblem.getId();

        // reminder row 5개 직접 삽입
        for (int i = 1; i <= 5; i++) {
            reminderRepository.save(ProblemReviewReminder.create(
                    userId, problemId, "old memo", "old ref",
                    i, i, LocalDateTime.now().plusDays(i)
            ));
        }

        String newMemo = "updated memo";
        String newRef = "updated ref";
        ProblemRegisterDto updateDto = new ProblemRegisterDto(
                problemId, newMemo, newRef, null, null
        );

        // when
        mockMvc.perform(MockMvcRequestBuilders.patch("/api/problems/info")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isOk());

        // then
        List<ProblemReviewReminder> scheduledRows = reminderRepository.findAll().stream()
                .filter(r -> r.getProblemId().equals(problemId)
                        && r.getStatus() == ProblemReviewReminderStatus.SCHEDULED)
                .toList();

        assertThat(scheduledRows).hasSize(5);
        assertThat(scheduledRows).allMatch(r ->
                newMemo.equals(r.getProblemMemoSnapshot()) &&
                newRef.equals(r.getProblemReferenceSnapshot())
        );
    }

    @Test
    @DisplayName("PATCH /api/problems/info - memo 가 길어도 snapshot 갱신이 truncation 으로 실패하지 않는다")
    void updateProblemInfo_truncatesLongMemoSnapshot() throws Exception {
        // given
        authenticateAsFixtureUser();
        Long problemId = problemList.get(0).getId();
        reminderRepository.save(ProblemReviewReminder.create(
                userId, problemId, "old memo", "old ref", 1, 1, LocalDateTime.now().plusDays(1)
        ));

        String longMemo = "나".repeat(1000);
        ProblemRegisterDto updateDto = new ProblemRegisterDto(problemId, longMemo, "ref", null, null);

        // when
        mockMvc.perform(MockMvcRequestBuilders.patch("/api/problems/info")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isOk());

        // then - 문제 본문에는 전문이, 알림 스냅샷에는 255자만 남는다
        assertThat(problemRepository.findById(problemId).orElseThrow().getMemo()).isEqualTo(longMemo);

        List<ProblemReviewReminder> scheduledRows = reminderRepository.findAll().stream()
                .filter(r -> r.getProblemId().equals(problemId)
                        && r.getStatus() == ProblemReviewReminderStatus.SCHEDULED)
                .toList();
        assertThat(scheduledRows).hasSize(1);
        assertThat(scheduledRows.get(0).getProblemMemoSnapshot()).hasSize(255);
    }

    @Test
    @DisplayName("DELETE /api/problems 후 해당 problemId의 reminder row가 CANCELED 상태가 된다")
    void deleteProblems_cancelsReminderRows() throws Exception {
        // given
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        userId, null, List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))
                )
        );
        doNothing().when(fileUploadService).deleteImageFileFromS3(anyString());

        Problem targetProblem = problemList.get(0);
        Long problemId = targetProblem.getId();

        // reminder row 5개 직접 삽입
        for (int i = 1; i <= 5; i++) {
            reminderRepository.save(ProblemReviewReminder.create(
                    userId, problemId, "memo", "ref",
                    i, i, LocalDateTime.now().plusDays(i)
            ));
        }

        ProblemDeleteRequestDto deleteDto = new ProblemDeleteRequestDto(List.of(problemId));

        // when
        mockMvc.perform(MockMvcRequestBuilders.delete("/api/problems")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deleteDto)))
                .andExpect(status().isOk());

        // then
        List<ProblemReviewReminder> reminderRows = reminderRepository.findAll().stream()
                .filter(r -> r.getProblemId().equals(problemId))
                .toList();

        assertThat(reminderRows).hasSize(5);
        assertThat(reminderRows).allMatch(r -> r.getStatus() == ProblemReviewReminderStatus.CANCELED);
    }
}
