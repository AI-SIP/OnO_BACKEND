package com.aisip.OnO.backend.problem.service;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.common.exception.ErrorCase;
import com.aisip.OnO.backend.folder.entity.Folder;
import com.aisip.OnO.backend.problem.dto.AddProblemImageUrlsRequest;
import com.aisip.OnO.backend.problem.dto.ProblemRegisterV2BatchDto;
import com.aisip.OnO.backend.problem.dto.ProblemRegisterV2Dto;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problem.entity.ProblemImageType;
import com.aisip.OnO.backend.problem.support.ProblemTestSupport;
import com.aisip.OnO.backend.studyroom.entity.StudyRoom;
import com.aisip.OnO.backend.studyroom.repository.StudyRoomRepository;
import com.aisip.OnO.backend.user.entity.User;
import com.aisip.OnO.backend.user.repository.UserRepository;
import com.aisip.OnO.backend.util.fileupload.exception.FileUploadErrorCase;
import com.aisip.OnO.backend.util.fileupload.service.ImageUrlOwnershipService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 문제 이미지 URL 은 그 사용자가 올린 파일만 받고, 지울 때는 다른 사용자가 쓰는 행과 파일을 남기는지 확인한다.
 *
 * <p>S3 삭제 메시지는 커밋 뒤에 {@code S3DeleteProducer} 로 나간다. 통합 테스트 베이스의 목으로 보낸 URL 을 확인한다.
 */
@DisplayName("문제 이미지 URL 의 주인 확인")
class ProblemImageUrlOwnerTest extends ProblemTestSupport {

    @Autowired
    private ProblemService problemService;

    @Autowired
    private ImageUrlOwnershipService imageUrlOwnershipService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudyRoomRepository studyRoomRepository;

    private User owner;
    private User other;
    private Folder ownerRoot;
    private Folder otherRoot;

    @BeforeEach
    void setUpUsers() {
        owner = fixtures.createUser();
        other = fixtures.createOtherUser();
        ownerRoot = fixtures.createRootFolder(owner.getId());
        otherRoot = fixtures.createRootFolder(other.getId());
    }

    private static ErrorCase errorCaseOf(Throwable throwable) {
        return ((ApplicationException) throwable).getErrorCase();
    }

    private ProblemRegisterV2Dto v2Dto(List<String> problemImageUrls, List<String> answerImageUrls) {
        return new ProblemRegisterV2Dto(null, "메모", null, ownerRoot.getId(), null, problemImageUrls, answerImageUrls);
    }

    /** 커밋 뒤에 S3DeleteProducer 목으로 보낸 삭제 메시지의 URL. */
    private List<String> sentS3DeleteUrls() {
        ArgumentCaptor<String> urls = ArgumentCaptor.forClass(String.class);
        verify(s3DeleteProducer, atLeast(0)).sendDeleteMessage(urls.capture(), any());
        return urls.getAllValues();
    }

    @Nested
    @DisplayName("등록")
    class Register {

        @Test
        @DisplayName("내가 올린 URL 은 그대로 등록된다")
        void acceptsOwnUploadedUrl() {
            String problemUrl = fixtures.uploadedImageUrl(owner.getId());
            String answerUrl = fixtures.uploadedImageUrl(owner.getId());

            Long problemId = problemService.registerProblemV2(v2Dto(List.of(problemUrl), List.of(answerUrl)), owner.getId());

            assertThat(problemImageDataRepository.findAllByProblemId(problemId))
                    .extracting(image -> image.getImageUrl())
                    .containsExactlyInAnyOrder(problemUrl, answerUrl);
        }

        @Test
        @DisplayName("다른 사용자가 올린 URL 이 섞이면 문제를 만들지 않는다 - 403 + 2006")
        void rejectsUrlUploadedByOtherUser() {
            List<String> problemUrls = List.of(fixtures.uploadedImageUrl(owner.getId()));
            List<String> answerUrls = List.of(fixtures.uploadedImageUrl(other.getId()));

            assertThatThrownBy(() -> problemService.registerProblemV2(v2Dto(problemUrls, answerUrls), owner.getId()))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(ProblemImageUrlOwnerTest::errorCaseOf)
                    .isEqualTo(FileUploadErrorCase.IMAGE_USER_UNMATCHED);

            assertThat(problemRepository.findAllByUserId(owner.getId())).isEmpty();
        }

        @Test
        @DisplayName("우리 버킷이 아닌 URL 이나 업로드 경로가 아닌 key 는 400 + 2003")
        void rejectsUrlOutsideUploadPath() {
            List<String> rejected = List.of(
                    "https://example.com/image/2026/10/29/0b8f4a52-7c1e-4d33-9a51-8e8d0f6f2a10.png",
                    "http://" + imageUrlOwnershipService.urlPrefix().substring("https://".length())
                            + "image/2026/10/29/0b8f4a52-7c1e-4d33-9a51-8e8d0f6f2a10.png",
                    imageUrlOwnershipService.urlPrefix() + "cosmetic/hat.png",
                    imageUrlOwnershipService.urlPrefix() + "image/users/" + owner.getId() + "/../x.png",
                    // 확장자 자리에 인코딩된 글자가 오면 같은 파일을 다른 문자열로 가리킬 수 있다
                    imageUrlOwnershipService.urlPrefix() + "image/2026/10/29/0b8f4a52-7c1e-4d33-9a51-8e8d0f6f2a10.%6Apg");

            for (String url : rejected) {
                assertThatThrownBy(() -> problemService.registerProblemV2(v2Dto(List.of(url), null), owner.getId()))
                        .as(url)
                        .isInstanceOf(ApplicationException.class)
                        .extracting(ProblemImageUrlOwnerTest::errorCaseOf)
                        .isEqualTo(FileUploadErrorCase.INVALID_IMAGE_FILE);
            }
            assertThat(problemRepository.findAllByUserId(owner.getId())).isEmpty();
        }

        @Test
        @DisplayName("일괄 등록에서 한 건이라도 다른 사용자의 URL 이면 전부 등록하지 않는다")
        void rejectsWholeBatchWhenOneUrlIsOthers() {
            ProblemRegisterV2BatchDto batch = new ProblemRegisterV2BatchDto(List.of(
                    v2Dto(List.of(fixtures.uploadedImageUrl(owner.getId())), null),
                    v2Dto(List.of(fixtures.uploadedImageUrl(other.getId())), null)));

            assertThatThrownBy(() -> problemService.registerProblemsV2(batch, owner.getId()))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(ProblemImageUrlOwnerTest::errorCaseOf)
                    .isEqualTo(FileUploadErrorCase.IMAGE_USER_UNMATCHED);

            assertThat(problemRepository.findAllByUserId(owner.getId())).isEmpty();
        }

        @Test
        @DisplayName("예전 형식 URL 은 아무도 쓰지 않으면 받는다 - 배포 직전에 발급받은 업로드")
        void acceptsUnusedLegacyUrl() {
            String legacyUrl = fixtures.legacyImageUrl();

            Long problemId = problemService.registerProblemV2(v2Dto(List.of(legacyUrl), null), owner.getId());

            assertThat(problemImageDataRepository.findAllByProblemId(problemId))
                    .extracting(image -> image.getImageUrl())
                    .containsExactly(legacyUrl);
        }

        @Test
        @DisplayName("예전 형식 URL 을 다른 사용자의 문제가 쓰고 있으면 거절한다")
        void rejectsLegacyUrlUsedByOtherUsersProblem() {
            String legacyUrl = fixtures.legacyImageUrl();
            saveImageData(saveProblem(other.getId(), otherRoot), legacyUrl, ProblemImageType.PROBLEM_IMAGE);

            assertThatThrownBy(() -> problemService.registerProblemV2(v2Dto(List.of(legacyUrl), null), owner.getId()))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(ProblemImageUrlOwnerTest::errorCaseOf)
                    .isEqualTo(FileUploadErrorCase.IMAGE_USER_UNMATCHED);
        }

        @Test
        @DisplayName("예전 형식 URL 을 다른 사용자의 프로필이나 스터디룸 썸네일이 쓰고 있어도 거절한다")
        void rejectsLegacyUrlUsedAsOthersProfileOrThumbnail() {
            String profileUrl = fixtures.legacyImageUrl();
            String thumbnailUrl = fixtures.legacyImageUrl();
            User otherUser = userRepository.findById(other.getId()).orElseThrow();
            otherUser.updateProfileImageUrl(profileUrl);
            userRepository.save(otherUser);
            StudyRoom room = StudyRoom.create("방", other.getId());
            room.updateThumbnailUrl(thumbnailUrl);
            studyRoomRepository.save(room);

            for (String url : List.of(profileUrl, thumbnailUrl)) {
                assertThatThrownBy(() -> problemService.registerProblemV2(v2Dto(List.of(url), null), owner.getId()))
                        .isInstanceOf(ApplicationException.class)
                        .extracting(ProblemImageUrlOwnerTest::errorCaseOf)
                        .isEqualTo(FileUploadErrorCase.IMAGE_USER_UNMATCHED);
            }
        }
    }

    @Nested
    @DisplayName("기존 문제에 이미지 추가")
    class AddToExistingProblem {

        @Test
        @DisplayName("이미 이 문제에 붙어 있는 URL 은 예전 key 형식이어도 다시 보내면 통과한다")
        void keepsUrlAlreadyAttachedToProblem() {
            Problem problem = saveProblem(owner.getId(), ownerRoot);
            String oldFormatUrl = imageUrlOwnershipService.urlPrefix() + "2024-08-28T10:00/문제 이미지_photo.jpg";
            saveImageData(problem, oldFormatUrl, ProblemImageType.PROBLEM_IMAGE);
            String newUrl = fixtures.uploadedImageUrl(owner.getId());

            assertThatCode(() -> problemService.addImageDataUrls(problem.getId(), owner.getId(),
                    new AddProblemImageUrlsRequest(List.of(
                            new AddProblemImageUrlsRequest.ImageUrlItem(oldFormatUrl, "PROBLEM_IMAGE"),
                            new AddProblemImageUrlsRequest.ImageUrlItem(newUrl, "ANSWER_IMAGE")))))
                    .doesNotThrowAnyException();

            assertThat(problemImageDataRepository.findAllByProblemId(problem.getId()))
                    .extracting(image -> image.getImageUrl())
                    .contains(oldFormatUrl, newUrl);
        }

        @Test
        @DisplayName("다른 사용자가 올린 URL 은 추가하지 않는다")
        void rejectsUrlUploadedByOtherUser() {
            Problem problem = saveProblem(owner.getId(), ownerRoot);

            assertThatThrownBy(() -> problemService.addImageDataUrls(problem.getId(), owner.getId(),
                    new AddProblemImageUrlsRequest(List.of(
                            new AddProblemImageUrlsRequest.ImageUrlItem(fixtures.uploadedImageUrl(other.getId()), "PROBLEM_IMAGE")))))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(ProblemImageUrlOwnerTest::errorCaseOf)
                    .isEqualTo(FileUploadErrorCase.IMAGE_USER_UNMATCHED);

            assertThat(problemImageDataRepository.findAllByProblemId(problem.getId())).isEmpty();
        }
    }

    @Nested
    @DisplayName("이미지 한 장 삭제")
    class DeleteOneImage {

        @Test
        @DisplayName("같은 URL 을 다른 사용자의 문제도 쓰면 내 행만 지우고 그 행과 S3 파일은 남긴다")
        void keepsOtherUsersRowAndFile() {
            String sharedUrl = fixtures.legacyImageUrl();
            Problem mine = saveProblem(owner.getId(), ownerRoot);
            Problem theirs = saveProblem(other.getId(), otherRoot);
            saveImageData(mine, sharedUrl, ProblemImageType.PROBLEM_IMAGE);
            saveImageData(theirs, sharedUrl, ProblemImageType.PROBLEM_IMAGE);

            problemService.deleteProblemImageData(sharedUrl, owner.getId());

            assertThat(problemImageDataRepository.findAllByProblemId(mine.getId())).isEmpty();
            assertThat(problemImageDataRepository.findAllByProblemId(theirs.getId()))
                    .extracting(image -> image.getImageUrl())
                    .containsExactly(sharedUrl);
            assertThat(sentS3DeleteUrls()).isEmpty();
        }

        @Test
        @DisplayName("내 문제에만 있는 예전 형식 URL 은 S3 파일까지 지운다")
        void deletesFileUsedOnlyByMe() {
            String legacyUrl = fixtures.legacyImageUrl();
            Problem mine = saveProblem(owner.getId(), ownerRoot);
            saveImageData(mine, legacyUrl, ProblemImageType.PROBLEM_IMAGE);

            problemService.deleteProblemImageData(legacyUrl, owner.getId());

            assertThat(problemImageDataRepository.existsByImageUrl(legacyUrl)).isFalse();
            assertThat(sentS3DeleteUrls()).containsExactly(legacyUrl);
        }
    }

    @Nested
    @DisplayName("문제 삭제")
    class DeleteProblem {

        @Test
        @DisplayName("다른 사용자가 같이 쓰는 파일은 S3 삭제 메시지를 보내지 않고, 내 파일만 보낸다")
        void sendsDeleteOnlyForFilesNotUsedByOthers() {
            String ownUrl = fixtures.uploadedImageUrl(owner.getId());
            String sharedLegacyUrl = fixtures.legacyImageUrl();
            String othersKeyUrl = fixtures.uploadedImageUrl(other.getId());
            String foreignUrl = "https://example.com/image/2026/10/29/0b8f4a52-7c1e-4d33-9a51-8e8d0f6f2a10.png";
            Problem mine = saveProblem(owner.getId(), ownerRoot);
            saveImageData(mine, ownUrl, ProblemImageType.PROBLEM_IMAGE);
            saveImageData(mine, sharedLegacyUrl, ProblemImageType.PROBLEM_IMAGE);
            saveImageData(mine, othersKeyUrl, ProblemImageType.ANSWER_IMAGE);
            saveImageData(mine, foreignUrl, ProblemImageType.ANSWER_IMAGE);
            Problem theirs = saveProblem(other.getId(), otherRoot);
            saveImageData(theirs, sharedLegacyUrl, ProblemImageType.PROBLEM_IMAGE);

            problemService.deleteProblem(mine.getId(), owner.getId());

            List<String> sent = sentS3DeleteUrls();
            assertThat(sent).contains(ownUrl);
            assertThat(sent).doesNotContain(sharedLegacyUrl, othersKeyUrl, foreignUrl);
            assertThat(problemImageDataRepository.findAllByProblemId(theirs.getId()))
                    .extracting(image -> image.getImageUrl())
                    .containsExactly(sharedLegacyUrl);
        }

        @Test
        @DisplayName("전체 삭제도 다른 사용자가 같이 쓰는 파일은 남긴다")
        void deleteAllKeepsFilesUsedByOthers() {
            String ownLegacyUrl = fixtures.legacyImageUrl();
            String sharedLegacyUrl = fixtures.legacyImageUrl();
            saveImageData(saveProblem(owner.getId(), ownerRoot), ownLegacyUrl, ProblemImageType.PROBLEM_IMAGE);
            saveImageData(saveProblem(owner.getId(), ownerRoot), sharedLegacyUrl, ProblemImageType.PROBLEM_IMAGE);
            saveImageData(saveProblem(other.getId(), otherRoot), sharedLegacyUrl, ProblemImageType.PROBLEM_IMAGE);

            problemService.deleteAllUserProblems(owner.getId());

            List<String> sent = sentS3DeleteUrls();
            assertThat(sent).contains(ownLegacyUrl);
            assertThat(sent).doesNotContain(sharedLegacyUrl);
        }
    }
}
