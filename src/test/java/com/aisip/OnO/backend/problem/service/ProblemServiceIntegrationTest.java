package com.aisip.OnO.backend.problem.service;


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
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT) // 랜덤 포트로 애플리케이션 실행
@AutoConfigureMockMvc
public class ProblemServiceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProblemService problemService;

    @Mock
    private FileUploadService fileUploadService;

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
                        ProblemImageData.from(new ProblemImageDataRegisterDto(null, "url" + i + "_1", ProblemImageType.PROBLEM_IMAGE), problem),
                        ProblemImageData.from(new ProblemImageDataRegisterDto(null, "url" + i + "_2", ProblemImageType.ANSWER_IMAGE), problem)
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
    void test(){

    }
}
