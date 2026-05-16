package com.aisip.OnO.backend.problem.controller;

import com.aisip.OnO.backend.auth.WithMockCustomUser;
import com.aisip.OnO.backend.problem.dto.*;
import com.aisip.OnO.backend.problem.entity.ProblemImageType;
import com.aisip.OnO.backend.problem.service.ProblemService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProblemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProblemService problemService;

    @Autowired
    private ObjectMapper objectMapper;

    private List<ProblemResponseDto> problemResponseDtoList;

    @BeforeEach
    void setUp() {
        problemResponseDtoList = new ArrayList<>();

        for(int i = 1; i <= 5; i++){
            List<ProblemImageDataResponseDto> imageUrlList = new ArrayList<>();
            for(int j = 1; j <= 3; j++){
                ProblemImageDataResponseDto problemImageDataResponseDto = new ProblemImageDataResponseDto(
                        "imageUrl_" + i + "_" + j,
                        ProblemImageType.valueOf(j),
                        LocalDateTime.now()
                );

                imageUrlList.add(problemImageDataResponseDto);
            }

            ProblemResponseDto problemResponseDto = new ProblemResponseDto(
                    (long)i,
                    -1L,
                    "memo" + i,
                    "reference" + i,
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    imageUrlList,
                    (long) i,
                    LocalDateTime.now(),
                    null,
                    List.of(),
                    List.of()
            );

            problemResponseDtoList.add(problemResponseDto);
        }
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    @DisplayName("특정 문제 조회")
    @WithMockCustomUser()
    void getProblem() throws Exception {
        //given
        Long problemId = 1L;
        given(problemService.findProblem(problemId, 1L)).willReturn(problemResponseDtoList.get(0));

        // When & Then
        mockMvc.perform(get("/api/problems/" + problemId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.problemId").value(1L))
                .andExpect(jsonPath("$.data.solveCount").value(1L))
                .andExpect(jsonPath("$.data.lastSolvedAt").exists())
                .andExpect(jsonPath("$.data.memo").value("memo1"))
                .andExpect(jsonPath("$.data.reference").value("reference1"))
                .andExpect(jsonPath("$.data.imageUrlList.size()").value(3))
                .andExpect(jsonPath("$.data.imageUrlList[0].imageUrl").value("imageUrl_1_1"))
                .andExpect(jsonPath("$.data.imageUrlList[0].problemImageType").value("PROBLEM_IMAGE"));
    }

    @Test
    @DisplayName("특정 유저의 문제 전체 조회")
    @WithMockCustomUser()
    void getProblemsByUserId() throws Exception {
        given(problemService.findUserProblems(1L)).willReturn(problemResponseDtoList);

        // When & Then
        mockMvc.perform(get("/api/problems/user")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size()").value(5))
                .andExpect(jsonPath("$.data[0].solveCount").value(1L))
                .andExpect(jsonPath("$.data[0].lastSolvedAt").exists())
                .andExpect(jsonPath("$.data[0].memo").value("memo1"))
                .andExpect(jsonPath("$.data[0].reference").value("reference1"))
                .andExpect(jsonPath("$.data[0].imageUrlList.size()").value(3))
                .andExpect(jsonPath("$.data[0].imageUrlList[0].imageUrl").value("imageUrl_1_1"))
                .andExpect(jsonPath("$.data[0].imageUrlList[0].problemImageType").value("PROBLEM_IMAGE"));
    }

    @Test
    @DisplayName("특정 유저의 문제 개수 조회")
    @WithMockCustomUser()
    void getUserProblemCount() throws Exception {
        //given
        given(problemService.findProblemCountByUser(1L)).willReturn((long) problemResponseDtoList.size());

        // When & Then
        mockMvc.perform(get("/api/problems/problemCount")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(5));
    }

    @Test
    @DisplayName("특정 폴더 내부 문제 개수 조회")
    @WithMockCustomUser()
    void getFolderProblems() throws Exception {
        //given
        given(problemService.findFolderProblemList(1L, 1L)).willReturn(problemResponseDtoList);

        // When & Then
        mockMvc.perform(get("/api/problems/folder/1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size()").value(5))
                .andExpect(jsonPath("$.data[0].memo").value("memo1"))
                .andExpect(jsonPath("$.data[0].reference").value("reference1"))
                .andExpect(jsonPath("$.data[0].imageUrlList.size()").value(3))
                .andExpect(jsonPath("$.data[0].imageUrlList[0].imageUrl").value("imageUrl_1_1"))
                .andExpect(jsonPath("$.data[0].imageUrlList[0].problemImageType").value("PROBLEM_IMAGE"));
    }

    @Test
    @DisplayName("문제 등록 기능")
    @WithMockCustomUser()
    void registerProblem() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/problems")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProblemRegisterDto(
                                    1L,
                                        "memo",
                                        "reference",
                                        1L,
                                        LocalDateTime.now()
                                ))))
                .andExpect(status().isOk());

        verify(problemService, times(1)).registerProblem(any(), eq(1L));  // userId가 1L인 것도 검증
    }

    @Test
    @DisplayName("문제 배치 등록 v2 기능")
    @WithMockCustomUser()
    void registerProblemsV2() throws Exception {
        given(problemService.registerProblemsV2(any(), eq(1L))).willReturn(List.of(1L, 2L));

        ProblemRegisterV2BatchDto dto = new ProblemRegisterV2BatchDto(List.of(
                new ProblemRegisterV2Dto(
                        null,
                        "memo1",
                        "reference1",
                        1L,
                        LocalDateTime.now(),
                        List.of("https://example.com/problem1.png"),
                        List.of("https://example.com/answer1.png"),
                        null
                ),
                new ProblemRegisterV2Dto(
                        null,
                        "memo2",
                        "reference2",
                        1L,
                        LocalDateTime.now(),
                        List.of("https://example.com/problem2.png"),
                        List.of(),
                        null
                )
        ));

        mockMvc.perform(post("/api/problems/v2/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size()").value(2))
                .andExpect(jsonPath("$.data[0]").value(1))
                .andExpect(jsonPath("$.data[1]").value(2));

        verify(problemService, times(1)).registerProblemsV2(any(), eq(1L));
    }

    @Test
    @DisplayName("문제 이미지 등록")
    @WithMockCustomUser()
    void registerProblemImageData() throws Exception {
        // When & Then
        MockMultipartFile image = new MockMultipartFile(
                "problemImages",
                "problem.png",
                MediaType.IMAGE_PNG_VALUE,
                "problem-image".getBytes()
        );

        mockMvc.perform(multipart("/api/problems/{problemId}/imageData", 1L)
                        .file(image)
                        .param("problemImageTypes", ProblemImageType.PROBLEM_IMAGE.name()))
                .andExpect(status().isOk());

        verify(problemService, times(1)).uploadProblemImages(eq(1L), eq(1L), any(), any());
        verify(problemService, times(1)).analysisProblem(eq(1L), eq(1L));
    }

    @Test
    @DisplayName("문제 메모, 출처 수정")
    @WithMockCustomUser()
    void updateProblemInfo() throws Exception {
        // given
        ProblemRegisterDto problemRegisterDto = new ProblemRegisterDto(
                1L,
                "memo update",
                "reference update",
                null,
                null
        );

        // when & then
        mockMvc.perform(patch("/api/problems/info")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(problemRegisterDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("문제가 수정되었습니다."));

        verify(problemService, times(1)).updateProblemInfo(any(), eq(1L));
    }

    @Test
    @DisplayName("문제 폴더 수정")
    @WithMockCustomUser()
    void updateProblemPath() throws Exception {
        // given
        ProblemRegisterDto problemRegisterDto = new ProblemRegisterDto(
                1L,
                null,
                null,
                2L,
                null
        );

        // when & then
        mockMvc.perform(patch("/api/problems/path")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(problemRegisterDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("문제가 수정되었습니다."));

        verify(problemService, times(1)).updateProblemFolder(any(), eq(1L));
    }

    @Test
    @DisplayName("문제 이미지 삭제")
    @WithMockCustomUser()
    void deleteProblemImageData() throws Exception {
        // given
        String imageUrl = "imageUrl";

        // when & then
        mockMvc.perform(delete("/api/problems/imageData")
                        .param("imageUrl", imageUrl))
                .andExpect(status().isOk());

        Mockito.verify(problemService, Mockito.times(1)).deleteProblemImageData(imageUrl, 1L);
    }
}
