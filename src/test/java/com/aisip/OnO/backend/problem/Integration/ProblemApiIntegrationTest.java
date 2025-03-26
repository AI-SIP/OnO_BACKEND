package com.aisip.OnO.backend.problem.Integration;


import com.aisip.OnO.backend.auth.WithMockCustomUser;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT) // 랜덤 포트로 애플리케이션 실행
@AutoConfigureMockMvc
public class ProblemApiIntegrationTest {

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

        for (int f = 1; f <= 2; f++) {
            Folder folder = Folder.from(
                    new FolderRegisterDto("folder" + f, null, null),
                    null,
                    userId
            );
            folderRepository.save(folder);

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
                        userId,
                        folder
                );
                problemRepository.save(problem);

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
        problemImageDataRepository.deleteAll();
        problemRepository.deleteAll();
        folderRepository.deleteAll();
    }

    @Test
    @DisplayName("problemId를 사용해 문제를 조회하는 API 테스트")
    @WithMockCustomUser()
    void findProblem() throws Exception {
        // given
        Problem problem = problemRepository.findAll().get(0);
        List<ProblemImageData> problemImageData = problem.getProblemImageDataList();

        Long problemId = problem.getId();

        // when & then - 해당 문제를 조회하는 API 호출
        mockMvc.perform(MockMvcRequestBuilders.get("/api/problem/{problemId}", problemId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.problemId").value(problemId))
                .andExpect(jsonPath("$.data.memo").value(problem.getMemo()))
                .andExpect(jsonPath("$.data.reference").value(problem.getReference()))
                .andExpect(jsonPath("$.data.solvedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.data.updatedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.imageUrlList.length()").value(2))
                .andExpect(jsonPath("$.data.imageUrlList[0].imageUrl").value(problemImageData.get(0).getImageUrl()))
                .andExpect(jsonPath("$.data.imageUrlList[1].imageUrl").value(problemImageData.get(1).getImageUrl()));
    }

    @Test
    @DisplayName("특정 유저의 모든 문제를 조회하는 API 테스트")
    @WithMockCustomUser()
    void findAllUserProblems() throws Exception {
        // given
        Problem problem = problemRepository.findAll().get(0);
        List<ProblemImageData> problemImageData = problem.getProblemImageDataList();

        Long problemId = problem.getId();

        // when & then - 해당 문제를 조회하는 API 호출
        mockMvc.perform(MockMvcRequestBuilders.get("/api/problem/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size()").value(6))
                .andExpect(jsonPath("$.data[0].problemId").value(problemId))
                .andExpect(jsonPath("$.data[0].memo").value(problem.getMemo()))
                .andExpect(jsonPath("$.data[0].reference").value(problem.getReference()))
                .andExpect(jsonPath("$.data[0].solvedAt").isNotEmpty())
                .andExpect(jsonPath("$.data[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$.data[0].updatedAt").isNotEmpty())
                .andExpect(jsonPath("$.data[0].imageUrlList.length()").value(2))
                .andExpect(jsonPath("$.data[0].imageUrlList[0].imageUrl").value(problemImageData.get(0).getImageUrl()))
                .andExpect(jsonPath("$.data[0].imageUrlList[1].imageUrl").value(problemImageData.get(1).getImageUrl()));
    }
}
