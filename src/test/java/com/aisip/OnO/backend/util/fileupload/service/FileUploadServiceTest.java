package com.aisip.OnO.backend.util.fileupload.service;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problem.entity.ProblemImageData;
import com.aisip.OnO.backend.problem.exception.ProblemErrorCase;
import com.aisip.OnO.backend.problem.repository.ProblemImageDataRepository;
import com.aisip.OnO.backend.problemsolve.entity.ProblemSolve;
import com.aisip.OnO.backend.problemsolve.entity.ProblemSolveImageData;
import com.aisip.OnO.backend.problemsolve.exception.ProblemSolveErrorCase;
import com.aisip.OnO.backend.problemsolve.repository.ProblemSolveImageDataRepository;
import com.aisip.OnO.backend.studyroom.repository.StudyRoomRepository;
import com.aisip.OnO.backend.user.repository.UserRepository;
import com.aisip.OnO.backend.util.fileupload.dto.PresignedUrlResponse;
import com.aisip.OnO.backend.util.fileupload.exception.FileUploadErrorCase;
import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.DeleteObjectRequest;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * S3 업로드/삭제 서비스 단위 테스트.
 *
 * <p>S3 클라이언트는 목이며 실제 버킷에 접근하지 않는다.
 * 이미지 삭제는 소유권 검증이 붙는 경로라 남의 이미지를 지울 수 없는지까지 확인한다.
 */
@DisplayName("파일 업로드 서비스")
class FileUploadServiceTest {

    private static final String BUCKET = "test-ono-bucket";
    private static final String URL_PREFIX = "https://" + BUCKET + ".s3.ap-northeast-2.amazonaws.com/";
    private static final Long USER_ID = 7L;

    private AmazonS3Client amazonS3Client;
    private ProblemImageDataRepository problemImageDataRepository;
    private ProblemSolveImageDataRepository problemSolveImageDataRepository;
    private MeterRegistry meterRegistry;
    private FileUploadService fileUploadService;

    @BeforeEach
    void setUp() {
        amazonS3Client = mock(AmazonS3Client.class);
        problemImageDataRepository = mock(ProblemImageDataRepository.class);
        problemSolveImageDataRepository = mock(ProblemSolveImageDataRepository.class);
        meterRegistry = new SimpleMeterRegistry();
        ImageUrlOwnershipService imageUrlOwnershipService = new ImageUrlOwnershipService(
                problemImageDataRepository, problemSolveImageDataRepository,
                mock(UserRepository.class), mock(StudyRoomRepository.class));
        ReflectionTestUtils.setField(imageUrlOwnershipService, "bucket", BUCKET);
        fileUploadService = new FileUploadService(
                amazonS3Client, meterRegistry, problemImageDataRepository, problemSolveImageDataRepository,
                imageUrlOwnershipService);
        ReflectionTestUtils.setField(fileUploadService, "bucket", BUCKET);
    }

    @AfterEach
    void tearDown() {
        meterRegistry.close();
    }

    private MultipartFile imageFile(String filename) {
        return new MockMultipartFile("image", filename, "image/jpeg", "fake-image-bytes".getBytes());
    }

    @Nested
    @DisplayName("업로드")
    class Upload {

        @Test
        @DisplayName("업로드한 파일의 공개 URL 을 돌려준다")
        void returnsPublicUrl() {
            String url = fileUploadService.uploadFileToS3(imageFile("photo.jpg"), USER_ID);

            assertThat(url).startsWith(URL_PREFIX + "image/");
            assertThat(url).endsWith(".jpg");
        }

        @Test
        @DisplayName("올린 사용자의 id 를 key 에 넣는다")
        void putsUserIdIntoKey() {
            String url = fileUploadService.uploadFileToS3(imageFile("photo.jpg"), USER_ID);

            assertThat(url).matches(java.util.regex.Pattern.quote(URL_PREFIX)
                    + "image/users/7/\\d{4}/\\d{2}/\\d{2}/[0-9a-f-]{36}\\.jpg");
        }

        @Test
        @DisplayName("원본 확장자를 유지한 키로 S3 에 올린다")
        void keepsOriginalExtension() {
            String url = fileUploadService.uploadFileToS3(imageFile("스크린샷.PNG"), USER_ID);

            ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
            verify(amazonS3Client).putObject(
                    org.mockito.ArgumentMatchers.eq(BUCKET), key.capture(), any(InputStream.class), any(ObjectMetadata.class));
            assertThat(key.getValue()).endsWith(".PNG");
            assertThat(url).endsWith(key.getValue());
        }

        @Test
        @DisplayName("파일 크기와 컨텐츠 타입을 메타데이터로 함께 올린다")
        void sendsMetadata() {
            MultipartFile file = imageFile("photo.jpg");

            fileUploadService.uploadFileToS3(file, USER_ID);

            ArgumentCaptor<ObjectMetadata> metadata = ArgumentCaptor.forClass(ObjectMetadata.class);
            verify(amazonS3Client).putObject(anyString(), anyString(), any(InputStream.class), metadata.capture());
            assertThat(metadata.getValue().getContentLength()).isEqualTo(file.getSize());
            assertThat(metadata.getValue().getContentType()).isEqualTo("image/jpeg");
        }

        @Test
        @DisplayName("업로드 성공은 success 태그로 계측된다")
        void recordsSuccessMetric() {
            fileUploadService.uploadFileToS3(imageFile("photo.jpg"), USER_ID);

            Timer timer = meterRegistry.find("ono.external.requests")
                    .tags("dependency", "s3", "operation", "upload", "outcome", "success")
                    .timer();
            assertThat(timer).isNotNull();
            assertThat(timer.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("파일 스트림을 열 수 없으면 400 으로 거절하고 failure 로 계측한다")
        void translatesIoException() {
            MultipartFile brokenFile = brokenMultipartFile();

            assertThatThrownBy(() -> fileUploadService.uploadFileToS3(brokenFile, USER_ID))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(exception -> ((ApplicationException) exception).getErrorCase())
                    .isEqualTo(FileUploadErrorCase.FILE_UPLOAD_FAILED);

            Timer timer = meterRegistry.find("ono.external.requests")
                    .tags("dependency", "s3", "operation", "upload", "outcome", "failure")
                    .timer();
            assertThat(timer).isNotNull();
        }

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {"noextension", "trailingdot.", ""})
        @DisplayName("확장자 없는 파일명은 500 이 아니라 400 으로 거절한다")
        void rejectsFilenameWithoutExtension(String filename) {
            MultipartFile file = imageFile(filename);

            assertThatThrownBy(() -> fileUploadService.uploadFileToS3(file, USER_ID))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(exception -> ((ApplicationException) exception).getErrorCase())
                    .isEqualTo(FileUploadErrorCase.INVALID_IMAGE_FILE);

            verifyNoInteractions(amazonS3Client);
        }

        @Test
        @DisplayName("파일명 없는 파트도 400 으로 거절한다")
        void rejectsPartWithoutFilename() {
            MultipartFile fileWithoutName = mock(MultipartFile.class);
            when(fileWithoutName.getOriginalFilename()).thenReturn(null);

            assertThatThrownBy(() -> fileUploadService.uploadFileToS3(fileWithoutName, USER_ID))
                    .as("filename 헤더가 없는 멀티파트 파트가 500 이 되면 안 된다")
                    .isInstanceOf(ApplicationException.class)
                    .extracting(exception -> ((ApplicationException) exception).getErrorCase())
                    .isEqualTo(FileUploadErrorCase.INVALID_IMAGE_FILE);

            verifyNoInteractions(amazonS3Client);
        }

        @Test
        @DisplayName("확장자 검증에 걸리면 S3 호출 없이 끝난다")
        void doesNotCallS3WhenFilenameIsInvalid() {
            assertThatThrownBy(() -> fileUploadService.uploadFileToS3(imageFile("no-extension"), USER_ID))
                    .isInstanceOf(ApplicationException.class);

            verify(amazonS3Client, never()).putObject(anyString(), anyString(), any(InputStream.class), any());
        }
    }

    @Nested
    @DisplayName("Presigned URL 발급")
    class PresignedUrl {

        @BeforeEach
        void stubPresignedUrl() throws Exception {
            when(amazonS3Client.generatePresignedUrl(any(GeneratePresignedUrlRequest.class)))
                    .thenReturn(new URL("https://" + BUCKET + ".s3.ap-northeast-2.amazonaws.com/put?signature=abc"));
        }

        @Test
        @DisplayName("요청한 개수만큼 발급한다")
        void issuesRequestedCount() {
            List<PresignedUrlResponse> responses = fileUploadService.generatePresignedUrls("image/jpeg", 3, USER_ID);

            assertThat(responses).hasSize(3);
            assertThat(responses).allSatisfy(response -> {
                assertThat(response.presignedUrl()).contains("signature=abc");
                assertThat(response.fileUrl()).startsWith(URL_PREFIX + "image/users/7/");
            });
        }

        @Test
        @DisplayName("0개를 요청하면 빈 목록을 준다")
        void issuesNothingForZeroCount() {
            assertThat(fileUploadService.generatePresignedUrls("image/jpeg", 0, USER_ID)).isEmpty();
            verifyNoInteractions(amazonS3Client);
        }

        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource({
                "image/png, .png",
                "image/gif, .gif",
                "image/webp, .webp",
                "image/heic, .heic",
                "image/heif, .heic",
                "image/jpeg, .jpg",
                "application/octet-stream, .jpg"
        })
        @DisplayName("컨텐츠 타입에 맞는 확장자로 키를 만든다")
        void mapsContentTypeToExtension(String contentType, String expectedExtension) {
            List<PresignedUrlResponse> responses = fileUploadService.generatePresignedUrls(contentType, 1, USER_ID);

            assertThat(responses.get(0).fileUrl()).endsWith(expectedExtension);
        }

        @Test
        @DisplayName("PUT 메서드와 만료시간이 붙은 요청으로 서명한다")
        void signsPutRequestWithExpiration() {
            long before = System.currentTimeMillis();

            fileUploadService.generatePresignedUrls("image/jpeg", 1, USER_ID);

            ArgumentCaptor<GeneratePresignedUrlRequest> request =
                    ArgumentCaptor.forClass(GeneratePresignedUrlRequest.class);
            verify(amazonS3Client).generatePresignedUrl(request.capture());
            assertThat(request.getValue().getMethod()).isEqualTo(HttpMethod.PUT);
            assertThat(request.getValue().getBucketName()).isEqualTo(BUCKET);
            assertThat(request.getValue().getContentType()).isEqualTo("image/jpeg");

            Date expiration = request.getValue().getExpiration();
            assertThat(expiration.getTime())
                    .as("서명 URL 이 영구히 유효하면 안 된다")
                    .isBetween(before, before + 11 * 60 * 1000L);
        }
    }

    @Nested
    @DisplayName("이미지 삭제 - 소유권")
    class DeleteWithOwnership {

        private static final String IMAGE_URL = URL_PREFIX + "image/users/1/2026/03/01/0b8f4a52-7c1e-4d33-9a51-8e8d0f6f2a10.jpg";
        private static final String LEGACY_URL = URL_PREFIX + "image/2025/03/01/0b8f4a52-7c1e-4d33-9a51-8e8d0f6f2a10.jpg";

        @Test
        @DisplayName("본인 문제 이미지는 S3 와 DB 에서 함께 지운다")
        void deletesOwnProblemImage() {
            ProblemImageData imageData = problemImageData(1L);
            when(problemImageDataRepository.findAllByImageUrlAndUserId(IMAGE_URL, 1L)).thenReturn(List.of(imageData));

            fileUploadService.deleteUserImageFile(IMAGE_URL, 1L);

            verify(amazonS3Client).deleteObject(any(DeleteObjectRequest.class));
            verify(problemImageDataRepository).deleteAll(List.of(imageData));
        }

        @Test
        @DisplayName("예전 형식 이미지를 다른 사용자의 행도 쓰고 있으면 내 행만 지우고 S3 파일은 남긴다")
        void keepsS3FileUsedByOtherUser() {
            ProblemImageData imageData = problemImageData(1L);
            when(problemImageDataRepository.findAllByImageUrlAndUserId(LEGACY_URL, 1L)).thenReturn(List.of(imageData));
            when(problemImageDataRepository.findImageUrlsUsedByOtherUsers(List.of(LEGACY_URL), 1L))
                    .thenReturn(List.of(LEGACY_URL));

            fileUploadService.deleteUserImageFile(LEGACY_URL, 1L);

            verify(amazonS3Client, never()).deleteObject(any(DeleteObjectRequest.class));
            verify(problemImageDataRepository).deleteAll(List.of(imageData));
        }

        @Test
        @DisplayName("다른 사용자의 문제 이미지는 지울 수 없다")
        void rejectsOtherUsersProblemImage() {
            when(problemImageDataRepository.existsByImageUrl(IMAGE_URL)).thenReturn(true);

            assertThatThrownBy(() -> fileUploadService.deleteUserImageFile(IMAGE_URL, 2L))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(exception -> ((ApplicationException) exception).getErrorCase())
                    .isEqualTo(ProblemErrorCase.PROBLEM_USER_UNMATCHED);

            verify(amazonS3Client, never()).deleteObject(any(DeleteObjectRequest.class));
            verify(problemImageDataRepository, never()).deleteAll(any());
        }

        @Test
        @DisplayName("본인 복습 기록 이미지도 지울 수 있다")
        void deletesOwnProblemSolveImage() {
            ProblemSolveImageData imageData = problemSolveImageData(1L);
            when(problemSolveImageDataRepository.findAllByImageUrlAndUserId(IMAGE_URL, 1L)).thenReturn(List.of(imageData));

            fileUploadService.deleteUserImageFile(IMAGE_URL, 1L);

            verify(amazonS3Client).deleteObject(any(DeleteObjectRequest.class));
            verify(problemSolveImageDataRepository).deleteAll(List.of(imageData));
        }

        @Test
        @DisplayName("다른 사용자의 복습 기록 이미지는 지울 수 없다")
        void rejectsOtherUsersProblemSolveImage() {
            when(problemSolveImageDataRepository.existsByImageUrl(IMAGE_URL)).thenReturn(true);

            assertThatThrownBy(() -> fileUploadService.deleteUserImageFile(IMAGE_URL, 2L))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(exception -> ((ApplicationException) exception).getErrorCase())
                    .isEqualTo(ProblemSolveErrorCase.PROBLEM_SOLVE_USER_UNMATCHED);

            verify(amazonS3Client, never()).deleteObject(any(DeleteObjectRequest.class));
        }

        @Test
        @DisplayName("어느 쪽에도 없는 이미지는 404 로 거절한다")
        void rejectsUnknownImage() {
            assertThatThrownBy(() -> fileUploadService.deleteUserImageFile(IMAGE_URL, 1L))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(exception -> ((ApplicationException) exception).getErrorCase())
                    .isEqualTo(FileUploadErrorCase.FILE_NOT_FOUND);

            verifyNoInteractions(amazonS3Client);
        }
    }

    @Nested
    @DisplayName("S3 직접 삭제")
    class DeleteFromS3 {

        @Test
        @DisplayName("URL 에서 버킷 키만 뽑아 삭제 요청한다")
        void extractsObjectKeyFromUrl() {
            fileUploadService.deleteImageFileFromS3(URL_PREFIX + "image/2026/03/01/uuid.jpg");

            ArgumentCaptor<DeleteObjectRequest> request = ArgumentCaptor.forClass(DeleteObjectRequest.class);
            verify(amazonS3Client).deleteObject(request.capture());
            assertThat(request.getValue().getBucketName()).isEqualTo(BUCKET);
            assertThat(request.getValue().getKey()).isEqualTo("image/2026/03/01/uuid.jpg");
        }

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {
                "https://example.com/image/2026/03/01/uuid.jpg",
                "https://other-bucket.s3.ap-northeast-2.amazonaws.com/image/2026/03/01/uuid.jpg",
                "https://example.com/" + "https://test-ono-bucket.s3.ap-northeast-2.amazonaws.com/x.com/image.jpg",
                "https://test-ono-bucket.s3.ap-northeast-2.amazonaws.com/",
                "https://test-ono-bucket.s3.ap-northeast-2.amazonaws.com/image/../secret.jpg"
        })
        @DisplayName("우리 버킷 URL 이 아니면 S3 를 부르지 않는다")
        void skipsForeignUrl(String url) {
            assertThatCode(() -> fileUploadService.deleteImageFileFromS3(url)).doesNotThrowAnyException();

            verifyNoInteractions(amazonS3Client);
        }

        @Test
        @DisplayName("삭제 실패는 그대로 전파되고 failure 로 계측된다")
        void propagatesDeleteFailure() {
            org.mockito.Mockito.doThrow(new IllegalStateException("s3 down"))
                    .when(amazonS3Client).deleteObject(any(DeleteObjectRequest.class));

            assertThatThrownBy(() -> fileUploadService.deleteImageFileFromS3(URL_PREFIX + "image/uuid.jpg"))
                    .isInstanceOf(IllegalStateException.class);

            Timer timer = meterRegistry.find("ono.external.requests")
                    .tags("dependency", "s3", "operation", "delete", "outcome", "failure")
                    .timer();
            assertThat(timer).isNotNull();
        }
    }

    private ProblemImageData problemImageData(Long ownerId) {
        Problem problem = mock(Problem.class);
        org.mockito.Mockito.lenient().when(problem.getUserId()).thenReturn(ownerId);
        ProblemImageData imageData = mock(ProblemImageData.class);
        org.mockito.Mockito.lenient().when(imageData.getProblem()).thenReturn(problem);
        return imageData;
    }

    private ProblemSolveImageData problemSolveImageData(Long ownerId) {
        ProblemSolve problemSolve = mock(ProblemSolve.class);
        org.mockito.Mockito.lenient().when(problemSolve.getUserId()).thenReturn(ownerId);
        ProblemSolveImageData imageData = mock(ProblemSolveImageData.class);
        org.mockito.Mockito.lenient().when(imageData.getProblemSolve()).thenReturn(problemSolve);
        return imageData;
    }

    private MultipartFile brokenMultipartFile() {
        return new MockMultipartFile("image", "photo.jpg", "image/jpeg", new byte[0]) {
            @Override
            public InputStream getInputStream() throws IOException {
                throw new IOException("stream closed");
            }
        };
    }
}
