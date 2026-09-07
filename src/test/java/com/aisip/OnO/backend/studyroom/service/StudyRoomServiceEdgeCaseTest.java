package com.aisip.OnO.backend.studyroom.service;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.common.exception.ErrorCase;
import com.aisip.OnO.backend.studyroom.dto.StudyRoomDtos.StudyRoomDetailResponse;
import com.aisip.OnO.backend.studyroom.dto.StudyRoomDtos.StudyRoomUpdateRequest;
import com.aisip.OnO.backend.studyroom.entity.StudyRoom;
import com.aisip.OnO.backend.studyroom.exception.StudyRoomErrorCase;
import com.aisip.OnO.backend.studyroom.support.StudyRoomTestSupport;
import com.aisip.OnO.backend.user.entity.User;
import com.aisip.OnO.backend.user.exception.UserErrorCase;
import com.aisip.OnO.backend.util.fileupload.exception.FileUploadErrorCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 스터디룸 생성·수정 요청의 경계 검증.
 *
 * <p>이름과 썸네일은 각각 없어도 되지만 둘 다 없으면 바꿀 것이 없는 요청이다. 컨트롤러가 아니라
 * 서비스가 이 규칙을 들고 있으므로, JSON 요청과 멀티파트 요청 양쪽 진입점을 서비스 단에서 확인한다.
 */
@DisplayName("StudyRoomService — 요청 경계")
class StudyRoomServiceEdgeCaseTest extends StudyRoomTestSupport {

    private static final String UPLOADED_URL = "https://cdn.example.com/edge.png";

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

    private MultipartFile pngThumbnail() {
        return new MockMultipartFile("thumbnail", "t.png", "image/png", pngBytes());
    }

    private MultipartFile emptyThumbnail() {
        return new MockMultipartFile("thumbnail", "t.png", "image/png", new byte[0]);
    }

    @Nested
    @DisplayName("방 생성")
    class CreateRoom {

        @Test
        @DisplayName("요청 본문이 통째로 없으면 INVALID_STUDY_ROOM_REQUEST")
        void rejectsNullRequest() {
            User user = fixtures.createUser("solo");

            assertThatThrownBy(() -> studyRoomService.createRoom(null, user.getId()))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(StudyRoomServiceEdgeCaseTest::errorCaseOf)
                    .isEqualTo(StudyRoomErrorCase.INVALID_STUDY_ROOM_REQUEST);
        }

        @ParameterizedTest(name = "name=\"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "가나다라마바사아자차카타파하가나다라마바사"})
        @DisplayName("이름이 비었거나 20자를 넘으면 만들 수 없다")
        void rejectsInvalidName(String name) {
            User user = fixtures.createUser("solo");

            assertThatThrownBy(() -> studyRoomService.createRoom(name, null, user.getId()))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(StudyRoomServiceEdgeCaseTest::errorCaseOf)
                    .isEqualTo(StudyRoomErrorCase.INVALID_STUDY_ROOM_REQUEST);
        }

        @Test
        @DisplayName("이름 20자는 경계값으로 허용된다")
        void acceptsTwentyCharacterName() {
            User user = fixtures.createUser("solo");
            String name = repeat('가', 20);

            StudyRoomDetailResponse response = studyRoomService.createRoom(name, null, user.getId());

            assertThat(response.name()).isEqualTo(name);
        }

        @Test
        @DisplayName("빈 썸네일 파일을 함께 보내면 썸네일 없이 방만 만들어진다")
        void ignoresEmptyThumbnail() {
            User user = fixtures.createUser("solo");

            StudyRoomDetailResponse response = studyRoomService.createRoom("썸네일없음", emptyThumbnail(), user.getId());

            assertThat(response.thumbnailUrl()).isNull();
            verify(fileUploadService, never()).uploadFileToS3(any());
        }

        @Test
        @DisplayName("존재하지 않는 사용자로는 만들 수 없다")
        void rejectsUnknownUser() {
            Long unknownUserId = fixture.outsider().getId() + 1_000L;

            assertThatThrownBy(() -> studyRoomService.createRoom("없는사용자", null, unknownUserId))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(StudyRoomServiceEdgeCaseTest::errorCaseOf)
                    .isEqualTo(UserErrorCase.USER_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("방 수정")
    class UpdateRoom {

        @Test
        @DisplayName("JSON 요청 본문이 없으면 INVALID_STUDY_ROOM_REQUEST")
        void rejectsNullJsonRequest() {
            assertThatThrownBy(() -> studyRoomService.updateRoom(
                    fixture.roomId(), fixture.host().getId(), (StudyRoomUpdateRequest) null))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(StudyRoomServiceEdgeCaseTest::errorCaseOf)
                    .isEqualTo(StudyRoomErrorCase.INVALID_STUDY_ROOM_REQUEST);
        }

        @Test
        @DisplayName("이름도 썸네일도 없는 멀티파트 요청은 바꿀 것이 없어 거절한다")
        void rejectsEmptyMultipartRequest() {
            assertThatThrownBy(() -> studyRoomService.updateRoom(
                    fixture.roomId(), fixture.host().getId(), null, null))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(StudyRoomServiceEdgeCaseTest::errorCaseOf)
                    .isEqualTo(StudyRoomErrorCase.INVALID_STUDY_ROOM_REQUEST);
        }

        @Test
        @DisplayName("이름은 없고 빈 썸네일만 있는 요청도 거절한다")
        void rejectsBlankNameWithEmptyThumbnail() {
            assertThatThrownBy(() -> studyRoomService.updateRoom(
                    fixture.roomId(), fixture.host().getId(), "  ", emptyThumbnail()))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(StudyRoomServiceEdgeCaseTest::errorCaseOf)
                    .isEqualTo(StudyRoomErrorCase.INVALID_STUDY_ROOM_REQUEST);
        }

        @Test
        @DisplayName("이름만 보내면 썸네일은 그대로 둔다")
        void updatesNameOnly() {
            given(fileUploadService.uploadFileToS3(any())).willReturn(UPLOADED_URL);
            studyRoomService.updateThumbnail(fixture.roomId(), fixture.host().getId(), pngThumbnail());

            StudyRoomDetailResponse response =
                    studyRoomService.updateRoom(fixture.roomId(), fixture.host().getId(), "새이름", null);

            assertThat(response.name()).isEqualTo("새이름");
            assertThat(response.thumbnailUrl()).as("보내지 않은 값은 지워지면 안 된다").isEqualTo(UPLOADED_URL);
        }

        @Test
        @DisplayName("썸네일만 보내면 이름은 그대로 둔다")
        void updatesThumbnailOnly() {
            given(fileUploadService.uploadFileToS3(any())).willReturn(UPLOADED_URL);
            String originalName = fixture.room().getName();

            StudyRoomDetailResponse response =
                    studyRoomService.updateRoom(fixture.roomId(), fixture.host().getId(), null, pngThumbnail());

            assertThat(response.name()).isEqualTo(originalName);
            assertThat(response.thumbnailUrl()).isEqualTo(UPLOADED_URL);
        }

        @Test
        @DisplayName("멀티파트 수정에서도 이름 길이 제한을 지킨다")
        void validatesNameOnMultipartUpdate() {
            String tooLongName = repeat('가', 21);

            assertThatThrownBy(() -> studyRoomService.updateRoom(
                    fixture.roomId(), fixture.host().getId(), tooLongName, null))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(StudyRoomServiceEdgeCaseTest::errorCaseOf)
                    .isEqualTo(StudyRoomErrorCase.INVALID_STUDY_ROOM_REQUEST);
        }

        @Test
        @DisplayName("이름과 함께 온 썸네일이 이미지가 아니면 이름도 바뀌지 않는다")
        void rollsBackNameWhenThumbnailInvalid() {
            String originalName = fixture.room().getName();
            MultipartFile notAnImage = new MockMultipartFile("thumbnail", "t.gif", "image/gif", pngBytes());

            assertThatThrownBy(() -> studyRoomService.updateRoom(
                    fixture.roomId(), fixture.host().getId(), "새이름", notAnImage))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(StudyRoomServiceEdgeCaseTest::errorCaseOf)
                    .isEqualTo(FileUploadErrorCase.INVALID_IMAGE_FILE);

            assertThat(roomRepository.findById(fixture.roomId()).orElseThrow().getName())
                    .as("검증 실패는 요청 전체를 되돌려야 한다")
                    .isEqualTo(originalName);
        }
    }

    @Nested
    @DisplayName("방 잠금 조회")
    class LockRoom {

        @Test
        @DisplayName("없는 방을 잠그려 하면 STUDY_ROOM_NOT_FOUND")
        void rejectsUnknownRoom() {
            Long unknownRoomId = nonExistentRoomId();

            assertThatThrownBy(() -> studyRoomService.lockRoom(unknownRoomId))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(StudyRoomServiceEdgeCaseTest::errorCaseOf)
                    .isEqualTo(StudyRoomErrorCase.STUDY_ROOM_NOT_FOUND);
        }

        @Test
        @DisplayName("있는 방은 그대로 반환한다")
        void returnsExistingRoom() {
            StudyRoom room = studyRoomService.lockRoom(fixture.roomId());

            assertThat(room.getId()).isEqualTo(fixture.roomId());
        }
    }
}
