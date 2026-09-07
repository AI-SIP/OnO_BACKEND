package com.aisip.OnO.backend.util.fileupload.controller;

import com.aisip.OnO.backend.auth.exception.AuthErrorCase;
import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.problem.exception.ProblemErrorCase;
import com.aisip.OnO.backend.support.IntegrationTestSupport;
import com.aisip.OnO.backend.user.entity.User;
import com.aisip.OnO.backend.util.fileupload.dto.PresignedUrlResponse;
import com.aisip.OnO.backend.util.fileupload.exception.FileUploadErrorCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 이미지 업로드 API.
 *
 * <p>{@code FileUploadService} 는 {@link IntegrationTestSupport} 에서 목이라 실제 S3 로 나가지 않는다.
 * 서비스 내부 규칙(확장자 판별, 소유권 검증)은 {@code FileUploadServiceTest} 가 담당하고,
 * 여기서는 컨트롤러 계층의 계약만 본다.
 * <ul>
 *   <li>멀티파트 파라미터가 없거나 이름이 다를 때 500 이 아니라 4xx 로 나가는가</li>
 *   <li>서비스가 던진 에러 케이스가 상태코드/에러코드 그대로 전달되는가</li>
 *   <li>삭제 요청의 소유자가 요청 본문이 아니라 인증 주체로 결정되는가</li>
 * </ul>
 */
@DisplayName("파일 업로드 컨트롤러")
class FileUploadControllerTest extends IntegrationTestSupport {

    private static final String UPLOADED_URL =
            "https://test-bucket.s3.ap-northeast-2.amazonaws.com/image/2026/09/02/uploaded.jpg";

    private MockMultipartFile image(String partName, String fileName, byte[] content) {
        return new MockMultipartFile(partName, fileName, "image/jpeg", content);
    }

    private MockMultipartFile image(String partName) {
        return image(partName, "photo.jpg", "fake-image".getBytes(StandardCharsets.UTF_8));
    }

    @Nested
    @DisplayName("단일 이미지 업로드")
    class UploadSingleImage {

        @Test
        @DisplayName("업로드한 파일의 S3 URL 을 돌려준다")
        void returnsUploadedUrl() throws Exception {
            User user = fixtures.createUser();
            authenticateAs(user.getId());
            given(fileUploadService.uploadFileToS3(any())).willReturn(UPLOADED_URL);

            mockMvc.perform(multipart("/api/fileUpload/image").file(image("image")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").value(UPLOADED_URL))
                    .andExpect(jsonPath("$.errorCode").doesNotExist());

            ArgumentCaptor<MultipartFile> uploaded = ArgumentCaptor.forClass(MultipartFile.class);
            verify(fileUploadService).uploadFileToS3(uploaded.capture());
            assertThat(uploaded.getValue().getOriginalFilename()).isEqualTo("photo.jpg");
        }

        @Test
        @DisplayName("0바이트 파일도 서비스로 넘어간다 - 컨트롤러가 임의로 삼키지 않는다")
        void passesEmptyFileToService() throws Exception {
            User user = fixtures.createUser();
            authenticateAs(user.getId());
            given(fileUploadService.uploadFileToS3(any())).willReturn(UPLOADED_URL);

            mockMvc.perform(multipart("/api/fileUpload/image").file(image("image", "photo.jpg", new byte[0])))
                    .andExpect(status().isOk());

            ArgumentCaptor<MultipartFile> uploaded = ArgumentCaptor.forClass(MultipartFile.class);
            verify(fileUploadService).uploadFileToS3(uploaded.capture());
            assertThat(uploaded.getValue().isEmpty())
                    .as("빈 파일 여부 판단은 서비스 책임이다")
                    .isTrue();
        }

        @Test
        @DisplayName("확장자가 없는 파일은 400 + 2003 으로 거절된다")
        void rejectsFileWithoutExtension() throws Exception {
            User user = fixtures.createUser();
            authenticateAs(user.getId());
            willThrow(new ApplicationException(FileUploadErrorCase.INVALID_IMAGE_FILE))
                    .given(fileUploadService).uploadFileToS3(any());

            mockMvc.perform(multipart("/api/fileUpload/image").file(image("image", "photo", new byte[]{1})))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode")
                            .value(FileUploadErrorCase.INVALID_IMAGE_FILE.getErrorCode()))
                    .andExpect(jsonPath("$.message")
                            .value(FileUploadErrorCase.INVALID_IMAGE_FILE.getMessage()));
        }

        @Test
        @DisplayName("S3 업로드가 실패하면 400 + 2001 로 나간다")
        void mapsUploadFailure() throws Exception {
            User user = fixtures.createUser();
            authenticateAs(user.getId());
            willThrow(new ApplicationException(FileUploadErrorCase.FILE_UPLOAD_FAILED))
                    .given(fileUploadService).uploadFileToS3(any());

            mockMvc.perform(multipart("/api/fileUpload/image").file(image("image")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode")
                            .value(FileUploadErrorCase.FILE_UPLOAD_FAILED.getErrorCode()));
        }

        @Test
        @DisplayName("멀티파트에 image 파트가 없으면 500 이 아니라 400 이다")
        void rejectsMissingPart() throws Exception {
            User user = fixtures.createUser();
            authenticateAs(user.getId());

            mockMvc.perform(multipart("/api/fileUpload/image"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(fileUploadService);
        }

        @Test
        @DisplayName("파트 이름이 다르면(images 로 보냄) 400 이다")
        void rejectsWrongPartName() throws Exception {
            User user = fixtures.createUser();
            authenticateAs(user.getId());

            mockMvc.perform(multipart("/api/fileUpload/image").file(image("images")))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(fileUploadService);
        }

        @Test
        @DisplayName("인증 없이 업로드하면 401 이고 S3 를 건드리지 않는다")
        void rejectsAnonymousUpload() throws Exception {
            clearAuthentication();

            mockMvc.perform(multipart("/api/fileUpload/image").file(image("image")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value(AuthErrorCase.AUTHENTICATION_FAILED.getErrorCode()));

            verifyNoInteractions(fileUploadService);
        }
    }

    @Nested
    @DisplayName("다중 이미지 업로드")
    class UploadMultipleImages {

        @Test
        @DisplayName("보낸 순서대로 URL 목록을 돌려준다")
        void keepsUploadOrder() throws Exception {
            User user = fixtures.createUser();
            authenticateAs(user.getId());
            given(fileUploadService.uploadFileToS3(any()))
                    .willReturn(UPLOADED_URL + "#1", UPLOADED_URL + "#2");

            mockMvc.perform(multipart("/api/fileUpload/images")
                            .file(image("images", "first.jpg", new byte[]{1}))
                            .file(image("images", "second.jpg", new byte[]{2})))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.data[0]").value(UPLOADED_URL + "#1"))
                    .andExpect(jsonPath("$.data[1]").value(UPLOADED_URL + "#2"));

            verify(fileUploadService, org.mockito.Mockito.times(2)).uploadFileToS3(any());
        }

        @Test
        @DisplayName("한 장이라도 거절되면 전체 요청이 400 이다")
        void failsWholeRequestWhenOneFileIsInvalid() throws Exception {
            User user = fixtures.createUser();
            authenticateAs(user.getId());
            given(fileUploadService.uploadFileToS3(any()))
                    .willReturn(UPLOADED_URL)
                    .willThrow(new ApplicationException(FileUploadErrorCase.INVALID_IMAGE_FILE));

            mockMvc.perform(multipart("/api/fileUpload/images")
                            .file(image("images", "ok.jpg", new byte[]{1}))
                            .file(image("images", "broken", new byte[]{2})))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode")
                            .value(FileUploadErrorCase.INVALID_IMAGE_FILE.getErrorCode()));
        }

        @Test
        @DisplayName("images 파트가 없으면 400 이다")
        void rejectsMissingParts() throws Exception {
            User user = fixtures.createUser();
            authenticateAs(user.getId());

            mockMvc.perform(multipart("/api/fileUpload/images"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(fileUploadService);
        }

        @Test
        @DisplayName("인증 없이 호출하면 401 이다")
        void rejectsAnonymous() throws Exception {
            clearAuthentication();

            mockMvc.perform(multipart("/api/fileUpload/images").file(image("images")))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(fileUploadService);
        }
    }

    @Nested
    @DisplayName("Presigned URL 발급")
    class PresignedUrls {

        private void stubPresignedUrls() {
            given(fileUploadService.generatePresignedUrls(anyString(), anyInt()))
                    .willAnswer(invocation -> {
                        int count = invocation.getArgument(1);
                        return java.util.stream.IntStream.range(0, Math.max(0, count))
                                .mapToObj(i -> new PresignedUrlResponse("https://signed/" + i, UPLOADED_URL + i))
                                .toList();
                    });
        }

        @Test
        @DisplayName("파라미터 없이 부르면 jpeg 1건을 발급한다")
        void usesDefaults() throws Exception {
            User user = fixtures.createUser();
            authenticateAs(user.getId());
            stubPresignedUrls();

            mockMvc.perform(get("/api/fileUpload/presigned-urls"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].presignedUrl").value("https://signed/0"));

            verify(fileUploadService).generatePresignedUrls("image/jpeg", 1);
        }

        @Test
        @DisplayName("20건까지는 요청한 만큼 발급한다 - 상한 경계")
        void issuesUpToLimit() throws Exception {
            User user = fixtures.createUser();
            authenticateAs(user.getId());
            stubPresignedUrls();

            mockMvc.perform(get("/api/fileUpload/presigned-urls")
                            .param("count", "20")
                            .param("contentType", "image/png"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(20));

            verify(fileUploadService).generatePresignedUrls("image/png", 20);
        }

        @Test
        @DisplayName("20건을 넘겨 요청해도 20건으로 잘린다 - 서명 폭주 방지")
        void clampsCountToTwenty() throws Exception {
            User user = fixtures.createUser();
            authenticateAs(user.getId());
            stubPresignedUrls();

            mockMvc.perform(get("/api/fileUpload/presigned-urls").param("count", "10000"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(20));

            verify(fileUploadService).generatePresignedUrls("image/jpeg", 20);
            verify(fileUploadService, never()).generatePresignedUrls(anyString(), eq(10000));
        }

        @Test
        @DisplayName("음수 count 는 500 이 아니라 빈 목록으로 응답한다")
        void handlesNegativeCount() throws Exception {
            User user = fixtures.createUser();
            authenticateAs(user.getId());
            stubPresignedUrls();

            mockMvc.perform(get("/api/fileUpload/presigned-urls").param("count", "-5"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(0));
        }

        @Test
        @DisplayName("숫자가 아닌 count 는 400 이다")
        void rejectsNonNumericCount() throws Exception {
            User user = fixtures.createUser();
            authenticateAs(user.getId());

            mockMvc.perform(get("/api/fileUpload/presigned-urls").param("count", "twenty"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value(400));

            verifyNoInteractions(fileUploadService);
        }

        @Test
        @DisplayName("인증 없이 호출하면 401 이고 서명이 발급되지 않는다")
        void rejectsAnonymous() throws Exception {
            clearAuthentication();

            mockMvc.perform(get("/api/fileUpload/presigned-urls"))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(fileUploadService);
        }
    }

    @Nested
    @DisplayName("이미지 삭제")
    class DeleteImage {

        @Test
        @DisplayName("삭제 요청의 소유자는 인증 주체다")
        void deletesAsAuthenticatedUser() throws Exception {
            User owner = fixtures.createUser();
            User other = fixtures.createOtherUser();
            authenticateAs(owner.getId());

            mockMvc.perform(delete("/api/fileUpload/image").param("imageUrl", UPLOADED_URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").value("이미지 삭제가 완료되었습니다."));

            ArgumentCaptor<Long> requesterId = ArgumentCaptor.forClass(Long.class);
            verify(fileUploadService).deleteUserImageFile(eq(UPLOADED_URL), requesterId.capture());
            assertThat(requesterId.getValue())
                    .as("삭제 권한 검증에 넘어가는 userId 는 인증 주체여야 한다")
                    .isEqualTo(owner.getId())
                    .isNotEqualTo(other.getId());
        }

        @Test
        @DisplayName("남의 이미지를 지우려 하면 서비스가 던진 소유권 오류가 그대로 나간다")
        void propagatesOwnershipRejection() throws Exception {
            User user = fixtures.createUser();
            authenticateAs(user.getId());
            willThrow(new ApplicationException(ProblemErrorCase.PROBLEM_USER_UNMATCHED))
                    .given(fileUploadService).deleteUserImageFile(anyString(), anyLong());

            mockMvc.perform(delete("/api/fileUpload/image").param("imageUrl", UPLOADED_URL))
                    .andExpect(status().is(ProblemErrorCase.PROBLEM_USER_UNMATCHED.getHttpStatusCode()))
                    .andExpect(jsonPath("$.errorCode")
                            .value(ProblemErrorCase.PROBLEM_USER_UNMATCHED.getErrorCode()));
        }

        @Test
        @DisplayName("없는 이미지는 404 로 나간다")
        void mapsNotFound() throws Exception {
            User user = fixtures.createUser();
            authenticateAs(user.getId());
            willThrow(new ApplicationException(FileUploadErrorCase.FILE_NOT_FOUND))
                    .given(fileUploadService).deleteUserImageFile(anyString(), anyLong());

            mockMvc.perform(delete("/api/fileUpload/image").param("imageUrl", UPLOADED_URL))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode")
                            .value(FileUploadErrorCase.FILE_NOT_FOUND.getErrorCode()));
        }

        @Test
        @DisplayName("imageUrl 파라미터가 없으면 400 이다")
        void rejectsMissingImageUrl() throws Exception {
            User user = fixtures.createUser();
            authenticateAs(user.getId());

            mockMvc.perform(delete("/api/fileUpload/image"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value(400));

            verifyNoInteractions(fileUploadService);
        }

        @Test
        @DisplayName("인증 없이 삭제하면 401 이고 S3 를 건드리지 않는다")
        void rejectsAnonymousDelete() throws Exception {
            clearAuthentication();

            mockMvc.perform(delete("/api/fileUpload/image").param("imageUrl", UPLOADED_URL))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value(AuthErrorCase.AUTHENTICATION_FAILED.getErrorCode()));

            verifyNoInteractions(fileUploadService);
        }

        @Test
        @DisplayName("권한이 없는 역할이면 403 이다")
        void rejectsUnknownRole() throws Exception {
            User user = fixtures.createUser();
            authenticateAs(user.getId(), "ROLE_UNKNOWN");

            mockMvc.perform(delete("/api/fileUpload/image").param("imageUrl", UPLOADED_URL))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value(AuthErrorCase.ACCESS_DENIED.getErrorCode()));

            verifyNoInteractions(fileUploadService);
        }
    }
}
