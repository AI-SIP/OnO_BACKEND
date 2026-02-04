package com.aisip.OnO.backend.util;

import com.aisip.OnO.backend.folder.dto.FolderRegisterDto;
import com.aisip.OnO.backend.folder.entity.Folder;
import com.aisip.OnO.backend.folder.repository.FolderRepository;
import com.aisip.OnO.backend.practicenote.entity.PracticeNote;
import com.aisip.OnO.backend.practicenote.entity.ProblemPracticeNoteMapping;
import com.aisip.OnO.backend.practicenote.repository.PracticeNoteRepository;
import com.aisip.OnO.backend.practicenote.repository.ProblemPracticeNoteMappingRepository;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problem.entity.ProblemImageData;
import com.aisip.OnO.backend.problem.entity.ProblemImageType;
import com.aisip.OnO.backend.problem.repository.ProblemImageDataRepository;
import com.aisip.OnO.backend.problem.repository.ProblemRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 대량 테스트 데이터 생성기
 * 특정 유저에 대해 Folder, Problem, PracticeNote 등을 대량으로 생성
 * 성능 테스트 및 대량 데이터 환경에서의 동작 검증에 사용
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BulkDataGenerator {

    private final FolderRepository folderRepository;
    private final ProblemRepository problemRepository;
    private final ProblemImageDataRepository problemImageDataRepository;
    private final PracticeNoteRepository practiceNoteRepository;
    private final ProblemPracticeNoteMappingRepository problemPracticeNoteMappingRepository;

    private final Random random = new Random();

    /**
     * 특정 유저에 대해 모든 타입의 데이터를 대량으로 생성
     * @param userId 대상 유저 ID
     * @param folderCount 생성할 폴더 개수
     * @param problemsPerFolder 폴더당 문제 개수
     * @param practiceNoteCount 생성할 복습노트 개수
     * @return 생성 결과 통계
     */
    @Transactional
    public BulkDataResult generateBulkData(
            Long userId,
            int folderCount,
            int problemsPerFolder,
            int practiceNoteCount) {

        log.info("=== 대량 데이터 생성 시작 ===");
        log.info("유저 ID: {}", userId);
        log.info("폴더 개수: {}, 폴더당 문제 개수: {}, 복습노트 개수: {}",
                folderCount, problemsPerFolder, practiceNoteCount);

        long startTime = System.currentTimeMillis();

        // 1. 폴더 생성
        List<Folder> folders = generateFolders(userId, folderCount);
        //List<Folder> folders = folderRepository.findAllByUserId(userId);
        //log.info("폴더 {} 개 생성 완료", folders.size());

        // 2. 문제 생성 (이미지 포함)
        List<Problem> problems = generateProblems(userId, folders, problemsPerFolder);
        //List<Problem> problems = problemRepository.findAllByUserId(userId);
        //log.info("문제 {} 개 생성 완료", problems.size());

        // 3. 복습노트 생성
        List<PracticeNote> practiceNotes = generatePracticeNotes(userId, problems, practiceNoteCount);
        log.info("복습노트 {} 개 생성 완료", practiceNotes.size());

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        BulkDataResult result = new BulkDataResult(
                folders.size(),
                problems.size(),
                countImages(problems),
                practiceNotes.size(),
                duration
        );

        log.info("=== 대량 데이터 생성 완료 ===");
        log.info("소요 시간: {} ms", duration);
        log.info(result.toString());

        return result;
    }

    /**
     * 폴더 대량 생성 (지정된 루트 폴더 하위에 생성)
     * @param userId 유저 ID
     * @param count 생성할 폴더 개수
     */
    private List<Folder> generateFolders(Long userId, int count) {
        List<Folder> folders = new ArrayList<>();

        Optional<Folder> optionalRootFolder = folderRepository.findRootFolder(userId);
        Folder rootFolder;

        if(optionalRootFolder.isEmpty()) {
            FolderRegisterDto folderRegisterDto = new FolderRegisterDto(
                    "책장",
                    null,
                    null
            );
            rootFolder = folderRepository.save(Folder.from(folderRegisterDto, userId));
        } else {
            rootFolder = optionalRootFolder.get();
        }

        // 모든 폴더를 루트 폴더의 자식으로 생성 (70%)과 그 하위 폴더(30%)
        int directChildCount = (int) (count * 0.7);

        // 루트 폴더의 직접 자식 폴더 생성
        for (int i = 0; i < directChildCount; i++) {
            Folder subFolder = RandomFolderGenerator.createRandomSubFolder(rootFolder, userId);
            folders.add(folderRepository.save(subFolder));
        }

        // 생성된 폴더들의 자식 폴더 생성
        int deepChildCount = count - directChildCount;
        for (int i = 0; i < deepChildCount; i++) {
            if (!folders.isEmpty()) {
                Folder parentFolder = folders.get(random.nextInt(folders.size()));
                Folder subFolder = RandomFolderGenerator.createRandomSubFolder(parentFolder, userId);
                folders.add(folderRepository.save(subFolder));
            }
        }

        return folders;
    }

    /**
     * 문제 대량 생성 (이미지 포함)
     */
    private List<Problem> generateProblems(Long userId, List<Folder> folders, int problemsPerFolder) {
        List<Problem> problems = new ArrayList<>();

        for (Folder folder : folders) {
            for (int i = 0; i < problemsPerFolder; i++) {
                // 문제 생성
                Problem problem = RandomProblemGenerator.createRandomProblemWithFolder(folder, userId);
                Problem savedProblem = problemRepository.save(problem);
                problems.add(savedProblem);

                // 이미지 생성 (50% 확률로 1-4개 이미지)
                if (random.nextBoolean()) {
                    int imageCount = random.nextInt(4) + 1; // 1-4개
                    generateImagesForProblem(savedProblem);
                }
            }
        }

        return problems;
    }

    /**
     * 문제에 대한 이미지 생성
     */
    private void generateImagesForProblem(Problem problem) {
        List<ProblemImageType> types = List.of(
                ProblemImageType.PROBLEM_IMAGE,
                ProblemImageType.ANSWER_IMAGE,
                ProblemImageType.SOLVE_IMAGE,
                ProblemImageType.PROCESS_IMAGE
        );

        // 1-4개의 랜덤 이미지 생성
        int imageCount = random.nextInt(4) + 1;
        for (int i = 0; i < imageCount && i < types.size(); i++) {
            ProblemImageData imageData = RandomProblemGenerator.createRandomProblemImageData(
                    problem.getId(),
                    types.get(i)
            );
            imageData.updateProblem(problem);
            problemImageDataRepository.save(imageData);
        }
    }

    /**
     * 복습노트 대량 생성
     */
    private List<PracticeNote> generatePracticeNotes(Long userId, List<Problem> problems, int count) {
        List<PracticeNote> practiceNotes = new ArrayList<>();

        // 문제가 없으면 복습노트를 생성할 수 없음
        if (problems.isEmpty()) {
            log.warn("문제가 없어서 복습노트를 생성할 수 없습니다.");
            return practiceNotes;
        }

        for (int i = 0; i < count; i++) {
            // 랜덤하게 20-30개 문제 선택 (문제가 적으면 그만큼만 선택)
            int targetProblemCount = random.nextInt(10) + 20; // 5-15개
            int actualProblemCount = Math.min(targetProblemCount, problems.size());

            // 중복 없이 문제 선택
            List<Long> problemIds = new ArrayList<>();
            List<Problem> shuffledProblems = new ArrayList<>(problems);
            java.util.Collections.shuffle(shuffledProblems, random);

            for (int j = 0; j < actualProblemCount; j++) {
                problemIds.add(shuffledProblems.get(j).getId());
            }

            RandomPracticeNoteGenerator.createRandomPracticeNoteWithProblems(problemIds, userId);
            // 복습노트 생성
            PracticeNote practiceNote = RandomPracticeNoteGenerator.createRandomPracticeNoteWithProblems(
                    problemIds,
                    userId
            );
            practiceNoteRepository.save(practiceNote);
            practiceNotes.add(practiceNote);

            for(Long problemId : problemIds) {
                problemRepository.findById(problemId).ifPresent( problem -> {
                        ProblemPracticeNoteMapping mapping = ProblemPracticeNoteMapping.from();
                        mapping.addMappingToProblemAndPractice(problem, practiceNote);
                        problemPracticeNoteMappingRepository.save(mapping);
                    }
                );
            }
        }

        return practiceNotes;
    }

    /**
     * 전체 이미지 개수 계산
     */
    private int countImages(List<Problem> problems) {
        return problems.stream()
                .mapToInt(p -> p.getProblemImageDataList() != null ? p.getProblemImageDataList().size() : 0)
                .sum();
    }

    /**
     * 대량 데이터 생성 결과
     */
    public record BulkDataResult(
            int folderCount,
            int problemCount,
            int imageCount,
            int practiceNoteCount,
            long durationMs
    ) {
        @Override
        public String toString() {
            return String.format(
                    "생성 결과: 폴더 %d개, 문제 %d개, 이미지 %d개, 복습노트 %d개 (소요시간: %dms)",
                    folderCount, problemCount, imageCount, practiceNoteCount, durationMs
            );
        }
    }

    /**
     * 특정 루트 폴더 하위에 데이터를 대량으로 생성
     * @param userId 대상 유저 ID
     * @param rootFolderId 루트 폴더 ID (모든 폴더가 이 폴더 하위에 생성됨)
     * @param folderCount 생성할 폴더 개수
     * @param problemsPerFolder 폴더당 문제 개수
     * @param practiceNoteCount 생성할 복습노트 개수
     * @return 생성 결과 통계
     */
    @Transactional
    public BulkDataResult generateBulkDataUnderRootFolder(
            Long userId,
            Long rootFolderId,
            int folderCount,
            int problemsPerFolder,
            int practiceNoteCount) {

        log.info("=== 대량 데이터 생성 시작 (루트 폴더 지정) ===");
        log.info("유저 ID: {}, 루트 폴더 ID: {}", userId, rootFolderId);
        log.info("폴더 개수: {}, 폴더당 문제 개수: {}, 복습노트 개수: {}",
                folderCount, problemsPerFolder, practiceNoteCount);

        long startTime = System.currentTimeMillis();

        // 1. 루트 폴더 하위에 폴더 생성
        List<Folder> folders = generateFolders(userId, folderCount);
        log.info("폴더 {} 개 생성 완료 (루트 폴더 {} 하위)", folders.size(), rootFolderId);

        // 2. 문제 생성 (이미지 포함)
        List<Problem> problems = generateProblems(userId, folders, problemsPerFolder);
        log.info("문제 {} 개 생성 완료", problems.size());

        // 3. 복습노트 생성
        List<PracticeNote> practiceNotes = generatePracticeNotes(userId, problems, practiceNoteCount);
        log.info("복습노트 {} 개 생성 완료", practiceNotes.size());

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        BulkDataResult result = new BulkDataResult(
                folders.size(),
                problems.size(),
                countImages(problems),
                practiceNotes.size(),
                duration
        );

        log.info("=== 대량 데이터 생성 완료 ===");
        log.info("소요 시간: {} ms", duration);
        log.info(result.toString());

        return result;
    }

    /**
     * 빠른 대량 생성 (기본값 사용)
     * - 100개 폴더
     * - 폴더당 50개 문제
     * - 50개 복습노트
     */
    @Transactional
    public BulkDataResult generateDefaultBulkData(Long userId) {
        return generateBulkData(userId, 100, 50, 50);
    }

    /**
     * 특정 루트 폴더 하위에 기본값으로 대량 생성
     * - 100개 폴더
     * - 폴더당 50개 문제
     * - 50개 복습노트
     */
    @Transactional
    public BulkDataResult generateDefaultBulkDataUnderRootFolder(Long userId, Long rootFolderId) {
        return generateBulkDataUnderRootFolder(userId, rootFolderId, 100, 50, 50);
    }

    /**
     * 소량 테스트 데이터 생성
     * - 10개 폴더
     * - 폴더당 10개 문제
     * - 10개 복습노트
     */
    @Transactional
    public BulkDataResult generateSmallBulkData(Long userId) {
        return generateBulkData(userId, 10, 10, 10);
    }

    /**
     * 특정 루트 폴더 하위에 소량 테스트 데이터 생성
     * - 10개 폴더
     * - 폴더당 10개 문제
     * - 10개 복습노트
     */
    @Transactional
    public BulkDataResult generateSmallBulkDataUnderRootFolder(Long userId, Long rootFolderId) {
        return generateBulkDataUnderRootFolder(userId, rootFolderId, 10, 10, 10);
    }

    /**
     * 대량 테스트 데이터 생성 (성능 테스트용)
     * - 500개 폴더
     * - 폴더당 100개 문제
     * - 200개 복습노트
     */
    @Transactional
    public BulkDataResult generateLargeBulkData(Long userId) {
        return generateBulkData(userId, 500, 100, 200);
    }

    /**
     * 특정 루트 폴더 하위에 대량 테스트 데이터 생성 (성능 테스트용)
     * - 500개 폴더
     * - 폴더당 100개 문제
     * - 200개 복습노트
     */
    @Transactional
    public BulkDataResult generateLargeBulkDataUnderRootFolder(Long userId, Long rootFolderId) {
        return generateBulkDataUnderRootFolder(userId, rootFolderId, 500, 100, 200);
    }
}