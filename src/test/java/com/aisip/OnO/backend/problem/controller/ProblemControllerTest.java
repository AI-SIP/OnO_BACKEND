package com.aisip.OnO.backend.problem.controller;

import com.aisip.OnO.backend.auth.WithMockCustomUser;
import com.aisip.OnO.backend.problem.dto.ProblemImageDataResponseDto;
import com.aisip.OnO.backend.problem.dto.ProblemResponseDto;
import com.aisip.OnO.backend.problem.entity.ProblemImageType;
import com.aisip.OnO.backend.problem.service.ProblemService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@AutoConfigureMockMvc
class ProblemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProblemService problemService;

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
                    "memo" + i,
                    "reference" + i,
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    imageUrlList
            );

            problemResponseDtoList.add(problemResponseDto);
        }
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    @DisplayName("특정 문제 조회")
    @WithMockCustomUser(userId = 1L, role = "ROLE_MEMBER")
    void getProblem() throws Exception {
        //given
        Long problemId = 1L;
        given(problemService.findProblem(problemId, 1L)).willReturn(problemResponseDtoList.get(0));

        // When & Then
        mockMvc.perform(get("/api/problem/" + problemId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.problemId").value(1L))
                .andExpect(jsonPath("$.data.memo").value("memo1"))
                .andExpect(jsonPath("$.data.reference").value("reference1"))
                .andExpect(jsonPath("$.data.imageUrlList.size()").value(3))
                .andExpect(jsonPath("$.data.imageUrlList[0].imageUrl").value("imageUrl_1_1"))
                .andExpect(jsonPath("$.data.imageUrlList[0].problemImageType").value("PROBLEM_IMAGE"));
    }

    @Test
    @DisplayName("특정 유저의 문제 전체 조회")
    @WithMockCustomUser(userId = 1L, role = "ROLE_MEMBER")
    void getProblemsByUserId() throws Exception {
        given(problemService.findUserProblems(1L)).willReturn(problemResponseDtoList);

        // When & Then
        mockMvc.perform(get("/api/problem/all")
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
    @DisplayName("특정 유저의 문제 개수 조회")
    @WithMockCustomUser(userId = 1L, role = "ROLE_MEMBER")
    void getUserProblemCount() throws Exception {
        given(problemService.findProblemCountByUser(1L)).willReturn((long) problemResponseDtoList.size());

        // When & Then
        mockMvc.perform(get("/api/problem/problemCount")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(5));
    }

    @Test
    void registerProblem() {
    }

    @Test
    void registerProblemImageData() {
    }

    @Test
    void updateProblemInfo() {
    }

    @Test
    void updateProblemPath() {
    }

    @Test
    void updateProblemImageData() {
    }

    @Test
    void deleteProblems() {
    }

    @Test
    void deleteProblemImageData() {
    }
}