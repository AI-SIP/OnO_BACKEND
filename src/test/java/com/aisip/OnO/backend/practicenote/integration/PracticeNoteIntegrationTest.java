package com.aisip.OnO.backend.practicenote.integration;

import com.aisip.OnO.backend.practicenote.dto.PracticeNoteDetailResponseDto;
import com.aisip.OnO.backend.practicenote.dto.PracticeNoteRegisterDto;
import com.aisip.OnO.backend.practicenote.dto.PracticeNoteThumbnailResponseDto;
import com.aisip.OnO.backend.practicenote.entity.PracticeNote;
import com.aisip.OnO.backend.practicenote.entity.ProblemPracticeNoteMapping;
import com.aisip.OnO.backend.practicenote.repository.PracticeNoteRepository;
import com.aisip.OnO.backend.practicenote.repository.ProblemPracticeNoteMappingRepository;
import com.aisip.OnO.backend.problem.dto.ProblemImageDataRegisterDto;
import com.aisip.OnO.backend.problem.dto.ProblemRegisterDto;
import com.aisip.OnO.backend.problem.dto.ProblemResponseDto;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problem.entity.ProblemImageData;
import com.aisip.OnO.backend.problem.entity.ProblemImageType;
import com.aisip.OnO.backend.problem.repository.ProblemImageDataRepository;
import com.aisip.OnO.backend.problem.repository.ProblemRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT) // 랜덤 포트로 애플리케이션 실행
@AutoConfigureMockMvc
public class PracticeNoteIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private ProblemImageDataRepository problemImageDataRepository;

    @Autowired
    private PracticeNoteRepository practiceNoteRepository;

    @Autowired
    private ProblemPracticeNoteMappingRepository problemPracticeNoteMappingRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Long userId;

    private List<Problem> problemList;

    private List<PracticeNote> practiceNoteList;

    @BeforeEach
    void setUp() {
        userId = 1L;
        // 인증 설정
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        userId, null, List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))
                )
        );

        practiceNoteList = new ArrayList<>();

        /*
        practice 0 : problem 0, 1, 2, 3
        practice 1 : problem 0, 1, 4, 5, 6, 7
        practice 2 : problem 0, 8, 9, 10, 11
         */
        problemList = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            Problem problem = Problem.from(
                    new ProblemRegisterDto(
                            null,
                            "memo" + i,
                            "reference" + i,
                            null,
                            LocalDateTime.now(),
                            null
                    ),
                    userId
            );
            problemRepository.save(problem);

            List<ProblemImageData> imageDataList = new ArrayList<>();
            for (int j = 1; j <= 3; j++){
                ProblemImageDataRegisterDto problemImageDataRegisterDto = new ProblemImageDataRegisterDto(
                        (long) problem.getId(),
                        "http://example.com/problemId/" + i + "/image" + j,
                        ProblemImageType.valueOf(j)
                );

                ProblemImageData imageData = ProblemImageData.from(problemImageDataRegisterDto);
                imageData.updateProblem(problem);
                problemImageDataRepository.save(imageData);
                imageDataList.add(imageData);
            }
            problemList.add(problem);
        }

        practiceNoteList = new ArrayList<>();
        for(int i = 0; i < 3; i++){
            List<Long> problemIdList = new ArrayList<>();
            for(int j = 0; j < 4; j++){
                problemIdList.add(problemList.get(i * 4 + j).getId());
            }
            PracticeNote practiceNote = practiceNoteRepository.save(PracticeNote.from(
                    new PracticeNoteRegisterDto(
                            null,
                            "practiceNote" + i,
                            problemIdList
                    ),
                    userId
            ));

            for(int j = 0; j < 4; j++){
                ProblemPracticeNoteMapping problemPracticeNoteMapping = ProblemPracticeNoteMapping.from();
                Problem problem = problemList.get(i * 4 + j);

                problemPracticeNoteMapping.addMappingToProblemAndPractice(problem, practiceNote);

                problemPracticeNoteMappingRepository.save(problemPracticeNoteMapping);
            }

            practiceNoteList.add(practiceNote);
        }

        // problem 0번에 대해서만 practiceNote 1, 2번과 추가 매핑
        for(int i = 1; i < 3; i++){
            ProblemPracticeNoteMapping problemPracticeNoteMapping = ProblemPracticeNoteMapping.from();

            problemPracticeNoteMapping.addMappingToProblemAndPractice(problemList.get(0), practiceNoteList.get(i));

            problemPracticeNoteMappingRepository.save(problemPracticeNoteMapping);
        }

        // problem 1번에 대해서만 practiceNote 1번과 추가 매핑
        for(int i = 1; i < 2; i++){
            ProblemPracticeNoteMapping problemPracticeNoteMapping = ProblemPracticeNoteMapping.from();

            problemPracticeNoteMapping.addMappingToProblemAndPractice(problemList.get(1), practiceNoteList.get(i));

            problemPracticeNoteMappingRepository.save(problemPracticeNoteMapping);
        }
    }

    @AfterEach
    void tearDown() {
        problemList.clear();
        practiceNoteList.clear();

        problemImageDataRepository.deleteAll();
        problemRepository.deleteAll();
        practiceNoteRepository.deleteAll();
        problemPracticeNoteMappingRepository.deleteAll();
    }

    @Test
    @DisplayName("복습 노트 상세 정보 조회 테스트")
    void findPracticeNoteDetail() throws Exception{
        //given
        PracticeNote practiceNote = practiceNoteList.get(0);
        Long practiceNoteId = practiceNote.getId();

        // when
        MvcResult result = mockMvc.perform(get("/api/practiceNotes/{practiceNoteId}", practiceNoteId))
                .andExpect(status().isOk())
                .andReturn();

        String json = result.getResponse().getContentAsString();
        JsonNode root = objectMapper.readTree(json);
        JsonNode dataNode = root.get("data");
        PracticeNoteDetailResponseDto dto = objectMapper.treeToValue(dataNode, PracticeNoteDetailResponseDto.class);

        List<ProblemResponseDto> problemResponseDtoList = dto.problemResponseDtoList();

        // then
        assertThat(dto.practiceNoteId()).isEqualTo(practiceNoteId);
        assertThat(dto.practiceTitle()).isEqualTo(practiceNote.getTitle());
        assertThat(dto.practiceCount()).isEqualTo(practiceNote.getPracticeCount());
        assertThat(problemResponseDtoList.size()).isEqualTo(4);

        for(int i = 0; i < dto.problemResponseDtoList().size(); i++){
            assertThat(problemResponseDtoList.get(i).problemId()).isEqualTo(problemList.get(i).getId());
            assertThat(problemResponseDtoList.get(i).memo()).isEqualTo(problemList.get(i).getMemo());
            assertThat(problemResponseDtoList.get(i).reference()).isEqualTo(problemList.get(i).getReference());
            assertThat(problemResponseDtoList.get(i).createdAt()).isEqualTo(problemList.get(i).getCreatedAt());
            assertThat(problemResponseDtoList.get(i).imageUrlList().size()).isEqualTo(problemList.get(i).getProblemImageDataList().size());
        }
    }

    @Test
    @DisplayName("유저의 복습 노트 썸네일 리스트 조회 테스트")
    void findAllPracticeThumbnailsByUser() throws Exception{
        // given & when
        MvcResult result = mockMvc.perform(get("/api/practiceNotes/thumbnail"))
                .andExpect(status().isOk())
                .andReturn();

        String json = result.getResponse().getContentAsString();
        JsonNode root = objectMapper.readTree(json);
        JsonNode dataNode = root.get("data");

        // then
        List<PracticeNoteThumbnailResponseDto> dtoList =
                objectMapper.convertValue(
                        dataNode,
                        new TypeReference<List<PracticeNoteThumbnailResponseDto>>() {}
                );

        assertThat(dtoList.size()).isEqualTo(practiceNoteList.size());
        for(int i = 0; i < dtoList.size(); i++){
            PracticeNoteThumbnailResponseDto dto = dtoList.get(i);
            PracticeNote practiceNote = practiceNoteList.get(i);

            assertThat(dto.practiceNoteId()).isEqualTo(practiceNote.getId());
            assertThat(dto.practiceTitle()).isEqualTo(practiceNote.getTitle());
            assertThat(dto.practiceCount()).isEqualTo(practiceNote.getPracticeCount());
            assertThat(dto.lastSolvedAt()).isEqualTo(practiceNote.getLastSolvedAt());
        }
    }
}
