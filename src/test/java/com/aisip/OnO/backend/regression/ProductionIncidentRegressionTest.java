package com.aisip.OnO.backend.regression;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.folder.entity.Folder;
import com.aisip.OnO.backend.problem.dto.ProblemRegisterDto;
import com.aisip.OnO.backend.problem.service.ProblemService;
import com.aisip.OnO.backend.support.IntegrationTestSupport;
import com.aisip.OnO.backend.tag.dto.TagCreateRequestDto;
import com.aisip.OnO.backend.tag.service.TagService;
import com.aisip.OnO.backend.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 프로덕션에서 실제로 발생한 장애를 그대로 재현하는 회귀 테스트.
 *
 * <p>각 테스트는 Sentry 이슈 하나에 대응한다. 여기가 초록이면 그 장애는 다시 나지 않는다.
 * H2에서는 재현되지 않던 것들이라, 이 테스트들은 Testcontainers MySQL 위에서만 의미가 있다.
 */
@DisplayName("프로덕션 장애 회귀")
class ProductionIncidentRegressionTest extends IntegrationTestSupport {

    @Autowired
    private TagService tagService;

    @Autowired
    private ProblemService problemService;

    @Nested
    @DisplayName("JAVA-SPRING-BOOT-4Z: POST /api/tags 태그 중복 생성 시 500")
    class TagDuplicateIncident {

        /**
         * Sentry: Duplicate entry '477-발상 부족' for key 'tag.idx_tag_user_normalized' (24건)
         *
         * <p>TagService.createTag 는 findByUserIdAndNormalizedName 조회 후 없으면 save 하는
         * check-then-act 구조라, 같은 사용자가 같은 태그를 동시에 보내면 두 트랜잭션이
         * 모두 "없음"을 보고 insert 해 유니크 인덱스에 걸린다.
         */
        @Test
        @DisplayName("같은 태그명을 동시에 생성해도 500이 아니라 같은 태그 하나로 수렴한다")
        void createsSingleTagUnderConcurrentRequests() throws InterruptedException {
            User user = fixtures.createUser();
            String tagName = "발상 부족";

            int threadCount = 8;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch ready = new CountDownLatch(threadCount);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(threadCount);
            List<Throwable> failures = new ArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        tagService.createTag(user.getId(), new TagCreateRequestDto(tagName));
                    } catch (Throwable e) {
                        synchronized (failures) {
                            failures.add(e);
                        }
                    } finally {
                        done.countDown();
                    }
                });
            }

            ready.await(5, TimeUnit.SECONDS);
            start.countDown();
            done.await(20, TimeUnit.SECONDS);
            executor.shutdownNow();

            assertThat(failures)
                    .as("동시 요청 중 예외가 발생하면 사용자에게 500이 나간다")
                    .isEmpty();
            assertThat(tagService.getUserTags(user.getId()))
                    .as("같은 이름의 태그는 하나만 남아야 한다")
                    .hasSize(1);
        }

        @Test
        @DisplayName("이미 있는 태그를 다시 생성하면 기존 태그를 그대로 돌려준다")
        void returnsExistingTagOnDuplicate() {
            User user = fixtures.createUser();

            Long firstId = tagService.createTag(user.getId(), new TagCreateRequestDto("발상 부족")).tagId();
            Long secondId = tagService.createTag(user.getId(), new TagCreateRequestDto("발상 부족")).tagId();

            assertThat(secondId).isEqualTo(firstId);
        }

        @Test
        @DisplayName("대소문자만 다른 태그도 같은 태그로 취급한다")
        void treatsCaseVariantsAsSameTag() {
            User user = fixtures.createUser();

            Long lower = tagService.createTag(user.getId(), new TagCreateRequestDto("algebra")).tagId();
            Long upper = tagService.createTag(user.getId(), new TagCreateRequestDto("ALGEBRA")).tagId();

            assertThat(upper).isEqualTo(lower);
        }

        @Test
        @DisplayName("다른 사용자는 같은 이름의 태그를 각자 가질 수 있다")
        void allowsSameTagNameAcrossUsers() {
            User user = fixtures.createUser();
            User other = fixtures.createOtherUser();

            Long mine = tagService.createTag(user.getId(), new TagCreateRequestDto("발상 부족")).tagId();
            Long theirs = tagService.createTag(other.getId(), new TagCreateRequestDto("발상 부족")).tagId();

            assertThat(theirs).isNotEqualTo(mine);
            assertThat(tagService.getUserTags(user.getId())).hasSize(1);
            assertThat(tagService.getUserTags(other.getId())).hasSize(1);
        }
    }

    @Nested
    @DisplayName("JAVA-SPRING-BOOT-5A: POST /api/problems 긴 메모 저장 시 500")
    class ProblemMemoTruncationIncident {

        /**
         * Sentry: Data truncation: Data too long for column 'memo' at row 1 (3건)
         *
         * <p>Problem.memo 에 길이 제약이 없어 MySQL에서 varchar(255) 로 생성되는데,
         * 앱은 1000자까지 입력을 허용한다. 백엔드에는 길이 검증이 없어서
         * 256자 이상이 들어오면 DataIntegrityViolationException 이 그대로 500으로 나간다.
         */
        @Test
        @DisplayName("앱이 허용하는 1000자 메모가 500 없이 저장된다")
        void savesMemoUpToClientLimit() {
            User user = fixtures.createUser();
            Folder folder = fixtures.createRootFolder(user.getId());
            String memo = "가".repeat(1000);

            AtomicReference<Long> problemId = new AtomicReference<>();

            assertThatCode(() -> problemId.set(problemService.registerProblem(
                    new ProblemRegisterDto(null, memo, "출처", folder.getId(), null),
                    user.getId()
            ))).doesNotThrowAnyException();

            assertThat(problemService.findProblem(problemId.get(), user.getId()).memo())
                    .as("잘리지 않고 원문 그대로 저장돼야 한다")
                    .isEqualTo(memo);
        }

        @Test
        @DisplayName("256자 메모 - varchar(255) 경계를 넘는 순간 터지던 지점")
        void savesMemoJustOverLegacyColumnLimit() {
            User user = fixtures.createUser();
            Folder folder = fixtures.createRootFolder(user.getId());
            String memo = "a".repeat(256);

            Long problemId = problemService.registerProblem(
                    new ProblemRegisterDto(null, memo, null, folder.getId(), null),
                    user.getId()
            );

            assertThat(problemService.findProblem(problemId, user.getId()).memo()).hasSize(256);
        }

        @Test
        @DisplayName("허용 한도를 넘는 메모는 500이 아니라 400으로 거절한다")
        void rejectsMemoOverLimitWithBadRequest() {
            User user = fixtures.createUser();
            Folder folder = fixtures.createRootFolder(user.getId());
            String memo = "가".repeat(1001);

            assertThatCode(() -> problemService.registerProblem(
                    new ProblemRegisterDto(null, memo, null, folder.getId(), null),
                    user.getId()
            ))
                    .as("한도 초과는 서버 오류가 아니라 클라이언트 입력 오류다")
                    .isInstanceOf(ApplicationException.class);
        }
    }

    @Nested
    @DisplayName("folderId 누락 요청이 500으로 나가던 지점")
    class MissingFolderIdIncident {

        /**
         * registerProblem 은 folderId 를 그대로 findById 에 넘긴다.
         * null 이면 FOLDER_NOT_FOUND(404) 가 아니라 스프링의
         * InvalidDataAccessApiUsageException 이 터져 500 으로 나간다.
         */
        @Test
        @DisplayName("folderId 가 null 이면 400/404 계열로 거절한다")
        void rejectsNullFolderIdWithClientError() {
            User user = fixtures.createUser();

            assertThatCode(() -> problemService.registerProblem(
                    new ProblemRegisterDto(null, "메모", null, null, null),
                    user.getId()
            ))
                    .as("입력 누락은 서버 오류가 아니다")
                    .isInstanceOf(ApplicationException.class);
        }

        @Test
        @DisplayName("남의 폴더에 문제를 등록할 수 없다")
        void rejectsOtherUsersFolder() {
            User user = fixtures.createUser();
            User other = fixtures.createOtherUser();
            Folder othersFolder = fixtures.createRootFolder(other.getId());

            assertThatCode(() -> problemService.registerProblem(
                    new ProblemRegisterDto(null, "메모", null, othersFolder.getId(), null),
                    user.getId()
            )).isInstanceOf(ApplicationException.class);
        }
    }
}
