package com.aisip.OnO.backend.studyroom.service;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.common.exception.ErrorCase;
import com.aisip.OnO.backend.studyroom.dto.StudyRoomDtos.StudyRoomThumbnailUpdateResponse;
import com.aisip.OnO.backend.studyroom.support.StudyRoomTestSupport;
import com.aisip.OnO.backend.util.fileupload.exception.FileUploadErrorCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * 스터디룸 썸네일 업로드 검증.
 *
 * <p>확장자만 믿으면 실행 파일을 {@code .png} 로 바꿔 올릴 수 있으므로, 서비스는 확장자와
 * Content-Type 이 서로 맞는지 본 뒤 파일 앞부분의 시그니처까지 확인한다. 셋 중 하나만 어긋나도
 * 거절해야 하는데, 지금까지는 PNG 정상 경로와 확실히 틀린 한두 경우만 확인되고 있었다.
 *
 * <p>업로드 자체는 S3 호출이라 베이스에서 목으로 막혀 있다. 여기서 보는 것은 "무엇을 S3 로
 * 넘기기 전에 걸러내는가"다.
 */
@DisplayName("스터디룸 썸네일 검증")
class StudyRoomThumbnailValidationTest extends StudyRoomTestSupport {

    private static final String UPLOADED_URL = "https://cdn.example.com/thumbnail.png";
    private static final long MAX_THUMBNAIL_SIZE_BYTES = 5 * 1024 * 1024;

    @Autowired
    private StudyRoomService studyRoomService;

    private RoomFixture fixture;

    @BeforeEach
    void setUpRoom() {
        fixture = createRoomWithMemberAndOutsider();
    }

    private static ErrorCase errorCaseOf(Throwable throwable) {
        return ((ApplicationException) throwable).getErrorCase();
    }

    private static byte[] jpegBytes() {
        return new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0, 0, 0, 0, 0};
    }

    private static byte[] webpBytes() {
        return new byte[]{
                0x52, 0x49, 0x46, 0x46,   // RIFF
                0x00, 0x00, 0x00, 0x00,   // 파일 크기(검증하지 않는다)
                0x57, 0x45, 0x42, 0x50    // WEBP
        };
    }

    private static byte[] bytesOf(String extension) {
        return switch (extension) {
            case "png" -> new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
            case "webp" -> webpBytes();
            default -> jpegBytes();
        };
    }

    private StudyRoomThumbnailUpdateResponse update(MultipartFile thumbnail) {
        return studyRoomService.updateThumbnail(fixture.roomId(), fixture.host().getId(), thumbnail);
    }

    private void assertRejected(MultipartFile thumbnail, ErrorCase expected) {
        assertThatThrownBy(() -> update(thumbnail))
                .isInstanceOf(ApplicationException.class)
                .extracting(StudyRoomThumbnailValidationTest::errorCaseOf)
                .isEqualTo(expected);
    }

    @Nested
    @DisplayName("허용되는 이미지")
    class AllowedImages {

        @BeforeEach
        void stubUpload() {
            given(fileUploadService.uploadFileToS3(any())).willReturn(UPLOADED_URL);
        }

        @ParameterizedTest(name = "{0} / {1}")
        @CsvSource({
                "t.png, image/png, png",
                "t.jpg, image/jpeg, jpg",
                "t.jpeg, image/jpeg, jpg",
                "t.jpg, image/jpg, jpg",
                "t.webp, image/webp, webp",
                "T.PNG, image/png, png"
        })
        @DisplayName("확장자·Content-Type·시그니처가 모두 맞으면 업로드된다")
        void acceptsMatchingImage(String filename, String contentType, String signature) {
            StudyRoomThumbnailUpdateResponse response =
                    update(new MockMultipartFile("thumbnail", filename, contentType, bytesOf(signature)));

            assertThat(response.thumbnailUrl()).isEqualTo(UPLOADED_URL);
            assertThat(roomRepository.findById(fixture.roomId()).orElseThrow().getThumbnailUrl())
                    .isEqualTo(UPLOADED_URL);
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource({"t.png, png", "t.jpg, jpg", "t.webp, webp"})
        @DisplayName("Content-Type 이 application/octet-stream 이면 확장자로 판단한다")
        void normalizesOctetStream(String filename, String signature) {
            StudyRoomThumbnailUpdateResponse response = update(new MockMultipartFile(
                    "thumbnail", filename, "application/octet-stream", bytesOf(signature)));

            assertThat(response.thumbnailUrl())
                    .as("일부 클라이언트는 Content-Type 을 octet-stream 으로 보낸다")
                    .isEqualTo(UPLOADED_URL);
        }

        @Test
        @DisplayName("Content-Type 이 비어 있어도 확장자로 판단한다")
        void normalizesMissingContentType() {
            StudyRoomThumbnailUpdateResponse response =
                    update(new MockMultipartFile("thumbnail", "t.png", null, bytesOf("png")));

            assertThat(response.thumbnailUrl()).isEqualTo(UPLOADED_URL);
        }

        @Test
        @DisplayName("정확히 5MB 인 파일은 통과한다")
        void acceptsExactlyMaxSize() {
            byte[] content = new byte[(int) MAX_THUMBNAIL_SIZE_BYTES];
            System.arraycopy(bytesOf("png"), 0, content, 0, 12);

            StudyRoomThumbnailUpdateResponse response =
                    update(new MockMultipartFile("thumbnail", "t.png", "image/png", content));

            assertThat(response.thumbnailUrl()).as("경계값은 허용해야 한다").isEqualTo(UPLOADED_URL);
        }
    }

    @Nested
    @DisplayName("거절되는 이미지")
    class RejectedImages {

        @Test
        @DisplayName("파일이 비어 있으면 거절한다")
        void rejectsEmptyFile() {
            assertRejected(new MockMultipartFile("thumbnail", "t.png", "image/png", new byte[0]),
                    FileUploadErrorCase.INVALID_IMAGE_FILE);
        }

        @Test
        @DisplayName("5MB 를 1바이트라도 넘으면 거절한다")
        void rejectsOversizedFile() {
            byte[] content = new byte[(int) MAX_THUMBNAIL_SIZE_BYTES + 1];
            System.arraycopy(bytesOf("png"), 0, content, 0, 12);

            assertRejected(new MockMultipartFile("thumbnail", "t.png", "image/png", content),
                    FileUploadErrorCase.FILE_SIZE_EXCEEDED);
        }

        @ParameterizedTest(name = "filename={0}")
        @CsvSource({"thumbnail", "thumbnail."})
        @DisplayName("확장자가 없으면 거절한다")
        void rejectsMissingExtension(String filename) {
            assertRejected(new MockMultipartFile("thumbnail", filename, "image/png", bytesOf("png")),
                    FileUploadErrorCase.INVALID_IMAGE_FILE);
        }

        @ParameterizedTest(name = "{0} / {1}")
        @CsvSource({
                "t.png, image/jpeg",
                "t.jpg, image/png",
                "t.webp, image/png",
                "t.png, image/webp"
        })
        @DisplayName("확장자와 Content-Type 이 어긋나면 거절한다")
        void rejectsMismatchedExtension(String filename, String contentType) {
            assertRejected(new MockMultipartFile("thumbnail", filename, contentType, bytesOf("png")),
                    FileUploadErrorCase.INVALID_IMAGE_FILE);
        }

        @ParameterizedTest(name = "{0} / {1}")
        @CsvSource({
                "t.gif, image/gif",
                "t.bmp, application/octet-stream",
                "t.svg, image/svg+xml",
                "t.pdf, application/pdf"
        })
        @DisplayName("허용 목록에 없는 형식은 거절한다")
        void rejectsUnsupportedType(String filename, String contentType) {
            assertRejected(new MockMultipartFile("thumbnail", filename, contentType, bytesOf("png")),
                    FileUploadErrorCase.INVALID_IMAGE_FILE);
        }

        @ParameterizedTest(name = "{0} 확장자에 {1} 시그니처")
        @CsvSource({
                "t.png, jpg, image/png",
                "t.jpg, png, image/jpeg",
                "t.webp, png, image/webp"
        })
        @DisplayName("확장자를 바꿔 달아도 시그니처가 다르면 거절한다")
        void rejectsForgedExtension(String filename, String signature, String contentType) {
            assertRejected(new MockMultipartFile("thumbnail", filename, contentType, bytesOf(signature)),
                    FileUploadErrorCase.INVALID_IMAGE_FILE);
        }

        @ParameterizedTest(name = "{0} / {1}")
        @CsvSource({"t.png, image/png", "t.jpg, image/jpeg", "t.webp, image/webp"})
        @DisplayName("시그니처를 읽을 만큼 길지 않은 파일은 거절한다")
        void rejectsTruncatedSignature(String filename, String contentType) {
            assertRejected(new MockMultipartFile("thumbnail", filename, contentType, new byte[]{0x52, 0x49}),
                    FileUploadErrorCase.INVALID_IMAGE_FILE);
        }

        @Test
        @DisplayName("업로드 스트림을 읽지 못하면 FILE_UPLOAD_FAILED 로 끝난다")
        void rejectsUnreadableStream() {
            MultipartFile broken = new MockMultipartFile("thumbnail", "t.png", "image/png", bytesOf("png")) {
                @Override
                public InputStream getInputStream() throws IOException {
                    throw new IOException("stream closed");
                }
            };

            assertRejected(broken, FileUploadErrorCase.FILE_UPLOAD_FAILED);
        }

        @Test
        @DisplayName("거절된 요청은 S3 로 올라가지도, 방에 반영되지도 않는다")
        void rejectedFileNeverReachesS3() {
            assertRejected(new MockMultipartFile("thumbnail", "t.gif", "image/gif", bytesOf("png")),
                    FileUploadErrorCase.INVALID_IMAGE_FILE);

            assertThat(roomRepository.findById(fixture.roomId()).orElseThrow().getThumbnailUrl()).isNull();
        }
    }
}
