package com.aisip.OnO.backend.folder.support;

import com.aisip.OnO.backend.folder.entity.Folder;
import com.aisip.OnO.backend.folder.repository.FolderRepository;
import com.aisip.OnO.backend.folder.service.FolderService;
import com.aisip.OnO.backend.problem.dto.ProblemRegisterDto;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problem.repository.ProblemRepository;
import com.aisip.OnO.backend.support.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * folder 도메인 테스트의 공통 베이스.
 *
 * <p>{@link IntegrationTestSupport} 의 애노테이션 조합을 그대로 물려받아 스프링 컨텍스트를
 * 다른 도메인 테스트와 공유한다. {@code @MockBean} 은 컨텍스트를 갈라놓으므로 여기에 추가하지 않는다.
 * S3 업로드/삭제는 이미 베이스에서 {@code FileUploadService} 목으로 막혀 있다.
 *
 * <p>기존 folder 테스트는 폴더 ID 를 하드코딩(200L, 999L)하거나 {@code folderList} 의 인덱스
 * 순서에 기대어, 다른 테스트가 남긴 행이나 auto_increment 값에 따라 결과가 바뀌었다.
 * 여기 헬퍼는 항상 픽스처가 만든 엔티티를 되돌려 주고, 존재하지 않는 ID 도 실제 최대 ID 기준으로
 * 계산해 준다.
 */
public abstract class FolderTestSupport extends IntegrationTestSupport {

    private static final AtomicLong PROBLEM_SEQUENCE = new AtomicLong();

    @Autowired
    protected FolderService folderService;

    @Autowired
    protected FolderRepository folderRepository;

    @Autowired
    protected ProblemRepository problemRepository;

    @Autowired
    protected TransactionTemplate transactionTemplate;

    @PersistenceContext
    protected EntityManager entityManager;

    /**
     * 폴더 트리 픽스처.
     *
     * <pre>
     *            root
     *          /      \
     *      notebookA  notebookB
     *      /     \        \
     *  leafA1  leafA2    leafB1
     * </pre>
     */
    public record FolderTree(
            Folder root,
            Folder notebookA,
            Folder notebookB,
            Folder leafA1,
            Folder leafA2,
            Folder leafB1
    ) {
        public List<Folder> all() {
            return List.of(root, notebookA, notebookB, leafA1, leafA2, leafB1);
        }

        /** notebookA 서브트리(자기 자신 포함). 삭제 검증에 쓴다. */
        public List<Folder> notebookASubTree() {
            return List.of(notebookA, leafA1, leafA2);
        }
    }

    protected FolderTree createFolderTree(Long userId) {
        Folder root = fixtures.createRootFolder(userId);
        Folder notebookA = fixtures.createFolder(userId, "공책 A", root);
        Folder notebookB = fixtures.createFolder(userId, "공책 B", root);
        Folder leafA1 = fixtures.createFolder(userId, "단원 A-1", notebookA);
        Folder leafA2 = fixtures.createFolder(userId, "단원 A-2", notebookA);
        Folder leafB1 = fixtures.createFolder(userId, "단원 B-1", notebookB);

        return new FolderTree(root, notebookA, notebookB, leafA1, leafA2, leafB1);
    }

    /** 폴더마다 문제 {@code count}개를 넣는다. 문제 수 집계·폴더 삭제 검증에 쓴다. */
    protected List<Problem> saveProblems(Long userId, Folder folder, int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> saveProblem(userId, folder))
                .toList();
    }

    protected Problem saveProblem(Long userId, Folder folder) {
        long seq = PROBLEM_SEQUENCE.incrementAndGet();
        Problem problem = Problem.from(
                new ProblemRegisterDto(null, "메모" + seq, "출처" + seq, folder.getId(), LocalDateTime.now()),
                userId
        );
        problem.updateFolder(folder);
        return problemRepository.save(problem);
    }

    /**
     * 어떤 폴더에도 부여되지 않은 ID.
     *
     * <p>DatabaseCleaner 는 DELETE 로 테이블을 비우므로 auto_increment 가 되돌아가지 않는다.
     * "999L 은 없는 폴더" 같은 가정은 스위트가 커지면 바로 깨진다.
     */
    protected Long nonExistentFolderId() {
        return folderRepository.findAll().stream()
                .mapToLong(Folder::getId)
                .max()
                .orElse(0L) + 1_000L;
    }

    protected List<Long> idsOf(List<Folder> folders) {
        return folders.stream().map(Folder::getId).toList();
    }

    /** 테스트 본문은 트랜잭션 밖이라 지연 로딩이 열리지 않는다. 필요한 경우만 이 안에서 확인한다. */
    protected void inTransaction(Runnable action) {
        transactionTemplate.executeWithoutResult(status -> action.run());
    }
}
