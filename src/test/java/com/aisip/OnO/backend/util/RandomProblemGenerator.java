package com.aisip.OnO.backend.util;

import com.aisip.OnO.backend.folder.entity.Folder;
import com.aisip.OnO.backend.problem.dto.ProblemImageDataRegisterDto;
import com.aisip.OnO.backend.problem.dto.ProblemRegisterDto;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problem.entity.ProblemImageData;
import com.aisip.OnO.backend.problem.entity.ProblemImageType;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 테스트용 랜덤 Problem 생성기
 * 통합 테스트에서 고유한 Problem 엔티티를 쉽게 생성할 수 있도록 지원
 */
public class RandomProblemGenerator {

    private static final String DEFAULT_MEMO_PREFIX = "테스트메모";
    private static final String DEFAULT_REFERENCE_PREFIX = "테스트출처";
    private static final String DEFAULT_IMAGE_URL_PREFIX = "http://example.com/image";

    /**
     * 랜덤 memo와 reference를 가진 기본 테스트 Problem 생성
     * @param userId 문제를 소유할 사용자 ID
     * @return 생성된 Problem 엔티티
     */
    public static Problem createRandomProblem(Long userId) {
        String randomSuffix = UUID.randomUUID().toString().substring(0, 8);

        ProblemRegisterDto problemRegisterDto = new ProblemRegisterDto(
                null,
                DEFAULT_MEMO_PREFIX + "_" + randomSuffix,
                DEFAULT_REFERENCE_PREFIX + "_" + randomSuffix,
                null,
                LocalDateTime.now(),
                null
        );

        return Problem.from(problemRegisterDto, userId);
    }

    /**
     * 지정된 memo와 reference로 테스트 Problem 생성
     * @param memo 메모
     * @param reference 출처
     * @param userId 문제를 소유할 사용자 ID
     * @return 생성된 Problem 엔티티
     */
    public static Problem createRandomProblem(String memo, String reference, Long userId) {
        ProblemRegisterDto problemRegisterDto = new ProblemRegisterDto(
                null,
                memo,
                reference,
                null,
                LocalDateTime.now(),
                null
        );

        return Problem.from(problemRegisterDto, userId);
    }

    /**
     * 폴더에 속한 랜덤 Problem 생성 (폴더와 자동으로 연결)
     * @param folder 문제가 속할 폴더
     * @param userId 문제를 소유할 사용자 ID
     * @return 생성된 Problem 엔티티
     */
    public static Problem createRandomProblemWithFolder(Folder folder, Long userId) {
        String randomSuffix = UUID.randomUUID().toString().substring(0, 8);

        ProblemRegisterDto problemRegisterDto = new ProblemRegisterDto(
                null,
                DEFAULT_MEMO_PREFIX + "_" + randomSuffix,
                DEFAULT_REFERENCE_PREFIX + "_" + randomSuffix,
                folder.getId(),
                LocalDateTime.now(),
                null
        );

        Problem problem = Problem.from(problemRegisterDto, userId);
        problem.updateFolder(folder);
        return problem;
    }

    /**
     * 지정된 솔브 시간을 가진 랜덤 Problem 생성
     * @param solvedAt 문제를 푼 시간
     * @param userId 문제를 소유할 사용자 ID
     * @return 생성된 Problem 엔티티
     */
    public static Problem createRandomProblem(LocalDateTime solvedAt, Long userId) {
        String randomSuffix = UUID.randomUUID().toString().substring(0, 8);

        ProblemRegisterDto problemRegisterDto = new ProblemRegisterDto(
                null,
                DEFAULT_MEMO_PREFIX + "_" + randomSuffix,
                DEFAULT_REFERENCE_PREFIX + "_" + randomSuffix,
                null,
                solvedAt,
                null
        );

        return Problem.from(problemRegisterDto, userId);
    }

    /**
     * 이미지 데이터와 함께 랜덤 Problem 생성
     * @param imageCount 생성할 이미지 개수 (1-3)
     * @param userId 문제를 소유할 사용자 ID
     * @return 생성된 Problem 엔티티 (이미지는 연결만 되고 저장되지 않음)
     */
    public static Problem createRandomProblemWithImages(int imageCount, Long userId) {
        if (imageCount < 1 || imageCount > 3) {
            throw new IllegalArgumentException("이미지 개수는 1-3 사이여야 합니다.");
        }

        String randomSuffix = UUID.randomUUID().toString().substring(0, 8);

        ProblemRegisterDto problemRegisterDto = new ProblemRegisterDto(
                null,
                DEFAULT_MEMO_PREFIX + "_" + randomSuffix,
                DEFAULT_REFERENCE_PREFIX + "_" + randomSuffix,
                null,
                LocalDateTime.now(),
                null
        );

        Problem problem = Problem.from(problemRegisterDto, userId);

        return problem;
    }

    /**
     * 랜덤 ProblemImageData 생성 (Problem과 연결하지 않음)
     * @param problemId 이미지가 속할 문제 ID
     * @param problemImageType 이미지 타입
     * @return 생성된 ProblemImageData 엔티티
     */
    public static ProblemImageData createRandomProblemImageData(Long problemId, ProblemImageType problemImageType) {
        String randomSuffix = UUID.randomUUID().toString().substring(0, 8);

        ProblemImageDataRegisterDto imageDataRegisterDto = new ProblemImageDataRegisterDto(
                problemId,
                DEFAULT_IMAGE_URL_PREFIX + "_" + randomSuffix,
                problemImageType
        );

        return ProblemImageData.from(imageDataRegisterDto);
    }

    /**
     * 여러 개의 랜덤 ProblemImageData 생성
     * @param problemId 이미지가 속할 문제 ID
     * @param imageTypes 생성할 이미지 타입 리스트
     * @return 생성된 ProblemImageData 엔티티 리스트
     */
    public static List<ProblemImageData> createRandomProblemImageDataList(Long problemId, List<ProblemImageType> imageTypes) {
        List<ProblemImageData> imageDataList = new ArrayList<>();

        for (ProblemImageType imageType : imageTypes) {
            imageDataList.add(createRandomProblemImageData(problemId, imageType));
        }

        return imageDataList;
    }

    /**
     * 기본 3가지 타입의 이미지 데이터 생성 (PROBLEM_IMAGE, ANSWER_IMAGE, SOLVE_IMAGE)
     * @param problemId 이미지가 속할 문제 ID
     * @return 생성된 ProblemImageData 엔티티 리스트 (3개)
     */
    public static List<ProblemImageData> createDefaultProblemImageDataList(Long problemId) {
        return createRandomProblemImageDataList(
                problemId,
                List.of(
                        ProblemImageType.PROBLEM_IMAGE,
                        ProblemImageType.ANSWER_IMAGE,
                        ProblemImageType.SOLVE_IMAGE
                )
        );
    }
}