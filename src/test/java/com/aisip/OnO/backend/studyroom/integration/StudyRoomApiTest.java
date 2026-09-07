package com.aisip.OnO.backend.studyroom.integration;

import com.aisip.OnO.backend.studyroom.dto.StudyRoomDtos.*;
import com.aisip.OnO.backend.studyroom.entity.StudyRoom;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomMemberRole;
import com.aisip.OnO.backend.studyroom.service.StudyRoomService;
import com.aisip.OnO.backend.studyroom.support.StudyRoomTestSupport;
import com.aisip.OnO.backend.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("스터디룸 생성·조회·수정·삭제 API")
class StudyRoomApiTest extends StudyRoomTestSupport {

    @Nested
    @DisplayName("생성")
    class CreateRoom {

        @Test
        @DisplayName("생성한 사용자가 방장 멤버로 함께 등록된다")
        void creatorBecomesHostMember() throws Exception {
            User host = fixtures.createUser("host");
            authenticateAs(host.getId());

            mockMvc.perform(post("/api/study-room")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new StudyRoomCreateRequest("수능 준비방"))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.name").value("수능 준비방"))
                    .andExpect(jsonPath("$.data.hostUserId").value(host.getId()))
                    .andExpect(jsonPath("$.data.memberCount").value(1));

            StudyRoom room = roomRepository.findAll().get(0);
            assertThat(memberRepository.findByRoomIdAndUserId(room.getId(), host.getId()))
                    .as("생성자의 멤버십")
                    .isPresent()
                    .get()
                    .extracting(member -> member.getRole())
                    .isEqualTo(StudyRoomMemberRole.HOST);
        }

        @Test
        @DisplayName("이름 앞뒤 공백은 잘려서 저장된다")
        void nameIsTrimmed() throws Exception {
            User host = fixtures.createUser("host");
            authenticateAs(host.getId());

            mockMvc.perform(post("/api/study-room")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new StudyRoomCreateRequest("  공백방  "))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.name").value("공백방"));
        }

        @Test
        @DisplayName("이름 20자는 허용되고 21자는 400 으로 거절된다")
        void nameLengthBoundaryIsTwenty() throws Exception {
            User host = fixtures.createUser("host");
            authenticateAs(host.getId());

            mockMvc.perform(post("/api/study-room")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new StudyRoomCreateRequest(repeat('가', 20)))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.name").value(repeat('가', 20)));

            mockMvc.perform(post("/api/study-room")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new StudyRoomCreateRequest(repeat('가', 21)))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value(10016));
        }

        @ParameterizedTest(name = "이름 = \"{0}\"")
        @ValueSource(strings = {"", " ", "   "})
        @DisplayName("빈 이름은 400 으로 거절된다")
        void blankNameIsRejected(String name) throws Exception {
            User host = fixtures.createUser("host");
            authenticateAs(host.getId());

            mockMvc.perform(post("/api/study-room")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new StudyRoomCreateRequest(name))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value(10016));
        }

        @Test
        @DisplayName("이름이 null 이면 400 으로 거절된다")
        void nullNameIsRejected() throws Exception {
            User host = fixtures.createUser("host");
            authenticateAs(host.getId());

            mockMvc.perform(post("/api/study-room")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value(10016));
        }

        @Test
        @DisplayName("참여 스터디룸이 정원(10개)에 도달하면 더 만들 수 없다")
        void roomCountLimitIsEnforced() throws Exception {
            User host = fixtures.createUser("host");
            for (int i = 0; i < StudyRoomService.MAX_USER_ROOM_COUNT; i++) {
                createRoom(host, "방" + i);
            }
            authenticateAs(host.getId());

            mockMvc.perform(post("/api/study-room")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new StudyRoomCreateRequest("11번째 방"))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value(10005));
        }

        @Test
        @DisplayName("멀티파트로 이름과 썸네일을 함께 등록할 수 있다")
        void createWithThumbnail() throws Exception {
            User host = fixtures.createUser("host");
            authenticateAs(host.getId());
            given(fileUploadService.uploadFileToS3(any())).willReturn("https://cdn.example.com/new-room.png");

            mockMvc.perform(multipart("/api/study-room")
                            .file(new MockMultipartFile("thumbnailImage", "t.png", "image/png", pngBytes()))
                            .param("name", "썸네일방"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.name").value("썸네일방"))
                    .andExpect(jsonPath("$.data.thumbnailUrl").value("https://cdn.example.com/new-room.png"));
        }

        @Test
        @DisplayName("인증 없이 생성하면 401 이다")
        void unauthenticatedIsRejected() throws Exception {
            clearAuthentication();

            mockMvc.perform(post("/api/study-room")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new StudyRoomCreateRequest("몰래방"))))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("단건 조회")
    class GetRoom {

        @Test
        @DisplayName("멤버는 방 상세와 모든 멤버의 통계를 볼 수 있다")
        void memberSeesDetailWithStats() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            savePractices(fixture.host().getId(), LocalDateTime.now(), 2);
            savePractices(fixture.member().getId(), LocalDateTime.now(), 3);
            authenticateAs(fixture.member().getId());

            mockMvc.perform(get("/api/study-room/{roomId}", fixture.roomId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.memberCount").value(2))
                    .andExpect(jsonPath("$.data.members[?(@.userId == " + fixture.host().getId() + ")].todayPracticeCount").value(2))
                    .andExpect(jsonPath("$.data.members[?(@.userId == " + fixture.host().getId() + ")].practicedToday").value(true))
                    .andExpect(jsonPath("$.data.members[?(@.userId == " + fixture.member().getId() + ")].todayPracticeCount").value(3));
        }

        @Test
        @DisplayName("비멤버가 조회하면 403 이다")
        void nonMemberIsForbidden() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            authenticateAs(fixture.outsider().getId());

            mockMvc.perform(get("/api/study-room/{roomId}", fixture.roomId()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value(10002));
        }

        @Test
        @DisplayName("존재하지 않는 방은 404 다")
        void unknownRoomIsNotFound() throws Exception {
            User user = fixtures.createUser("user");
            authenticateAs(user.getId());

            mockMvc.perform(get("/api/study-room/{roomId}", nonExistentRoomId()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value(10001));
        }
    }

    @Nested
    @DisplayName("내 스터디룸 목록")
    class GetMyRooms {

        @Test
        @DisplayName("가입하지 않은 사용자는 빈 목록을 받는다")
        void emptyForUserWithoutRooms() throws Exception {
            User user = fixtures.createUser("user");
            authenticateAs(user.getId());

            mockMvc.perform(get("/api/study-room"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isEmpty());
        }

        @Test
        @DisplayName("남의 방은 목록에 섞이지 않는다")
        void otherUsersRoomIsNotListed() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            StudyRoom outsiderRoom = createRoom(fixture.outsider(), "외부인 방");
            authenticateAs(fixture.outsider().getId());

            mockMvc.perform(get("/api/study-room"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].roomId").value(outsiderRoom.getId()));
        }

        @Test
        @DisplayName("목록의 오늘 복습 집계는 방 전체 멤버를 합산한다")
        void todayPracticeIsAggregatedAcrossMembers() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            savePractices(fixture.host().getId(), LocalDateTime.now(), 2);
            savePractices(fixture.member().getId(), LocalDateTime.now(), 3);
            authenticateAs(fixture.host().getId());

            mockMvc.perform(get("/api/study-room"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].todayPracticeMemberCount").value(2))
                    .andExpect(jsonPath("$.data[0].todayPracticeCount").value(5))
                    .andExpect(jsonPath("$.data[0].members.length()").value(2));
        }

        @Test
        @DisplayName("읽지 않은 주간 리포트가 있으면 hasUnreadReport 가 true 다")
        void unreadReportIsFlagged() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            saveWeeklyReport(fixture.room(), java.time.LocalDate.now().minusWeeks(1));
            authenticateAs(fixture.member().getId());

            mockMvc.perform(get("/api/study-room"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].hasUnreadReport").value(true));
        }
    }

    @Nested
    @DisplayName("수정")
    class UpdateRoom {

        @Test
        @DisplayName("방장은 이름을 바꿀 수 있다")
        void hostCanRename() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            authenticateAs(fixture.host().getId());

            mockMvc.perform(patch("/api/study-room/{roomId}", fixture.roomId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new StudyRoomUpdateRequest("  새 이름  "))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.name").value("새 이름"));
        }

        @Test
        @DisplayName("일반 멤버가 이름을 바꾸려 하면 403 이다")
        void memberCannotRename() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            authenticateAs(fixture.member().getId());

            mockMvc.perform(patch("/api/study-room/{roomId}", fixture.roomId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new StudyRoomUpdateRequest("멤버 수정"))))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value(10003));

            assertThat(roomRepository.findById(fixture.roomId()).orElseThrow().getName())
                    .as("거절된 뒤 방 이름")
                    .isNotEqualTo("멤버 수정");
        }

        @Test
        @DisplayName("비멤버가 이름을 바꾸려 하면 403 이다")
        void nonMemberCannotRename() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            authenticateAs(fixture.outsider().getId());

            mockMvc.perform(patch("/api/study-room/{roomId}", fixture.roomId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new StudyRoomUpdateRequest("외부인 수정"))))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value(10002));
        }

        @Test
        @DisplayName("21자 이름으로는 수정할 수 없다")
        void renameLengthBoundary() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            authenticateAs(fixture.host().getId());

            mockMvc.perform(patch("/api/study-room/{roomId}", fixture.roomId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new StudyRoomUpdateRequest(repeat('나', 21)))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value(10016));
        }

        @Test
        @DisplayName("멀티파트 수정에서 이름과 썸네일이 모두 비면 400 이다")
        void multipartUpdateNeedsAtLeastOneField() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            authenticateAs(fixture.host().getId());

            mockMvc.perform(multipart("/api/study-rooms/{roomId}", fixture.roomId())
                            .with(patchMethod()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value(10016));
        }

        @Test
        @DisplayName("멀티파트로 이름과 썸네일을 한 번에 바꿀 수 있다")
        void multipartUpdatesNameAndThumbnail() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            authenticateAs(fixture.host().getId());
            given(fileUploadService.uploadFileToS3(any())).willReturn("https://cdn.example.com/updated.png");

            mockMvc.perform(multipart("/api/study-rooms/{roomId}", fixture.roomId())
                            .file(new MockMultipartFile("thumbnailImage", "t.png", "image/png", pngBytes()))
                            .param("name", "통합 수정방")
                            .with(patchMethod()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.name").value("통합 수정방"))
                    .andExpect(jsonPath("$.data.thumbnailUrl").value("https://cdn.example.com/updated.png"));
        }
    }

    @Nested
    @DisplayName("썸네일")
    class Thumbnail {

        @Test
        @DisplayName("방장이 올린 썸네일은 상세와 목록 응답에 모두 반영된다")
        void hostUploadIsReflectedEverywhere() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            authenticateAs(fixture.host().getId());
            given(fileUploadService.uploadFileToS3(any())).willReturn("https://cdn.example.com/room.png");

            mockMvc.perform(multipart("/api/study-room/{roomId}/thumbnail", fixture.roomId())
                            .file(new MockMultipartFile("thumbnail", "t.png", "image/png", pngBytes()))
                            .with(patchMethod()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.thumbnailUrl").value("https://cdn.example.com/room.png"));

            mockMvc.perform(get("/api/study-room/{roomId}", fixture.roomId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.thumbnailUrl").value("https://cdn.example.com/room.png"));

            mockMvc.perform(get("/api/study-room"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].thumbnailUrl").value("https://cdn.example.com/room.png"));
        }

        @Test
        @DisplayName("일반 멤버는 썸네일을 바꿀 수 없다")
        void memberCannotUpdateThumbnail() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            authenticateAs(fixture.member().getId());

            mockMvc.perform(multipart("/api/study-room/{roomId}/thumbnail", fixture.roomId())
                            .file(new MockMultipartFile("thumbnail", "t.png", "image/png", pngBytes()))
                            .with(patchMethod()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value(10003));
        }

        @Test
        @DisplayName("비멤버는 썸네일을 바꿀 수 없다")
        void nonMemberCannotUpdateThumbnail() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            authenticateAs(fixture.outsider().getId());

            mockMvc.perform(multipart("/api/study-room/{roomId}/thumbnail", fixture.roomId())
                            .file(new MockMultipartFile("thumbnail", "t.png", "image/png", pngBytes()))
                            .with(patchMethod()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value(10002));
        }

        @Test
        @DisplayName("확장자만 이미지이고 내용이 이미지가 아니면 400 이다")
        void contentSignatureIsValidated() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            authenticateAs(fixture.host().getId());

            mockMvc.perform(multipart("/api/study-room/{roomId}/thumbnail", fixture.roomId())
                            .file(new MockMultipartFile("thumbnail", "t.png", "image/png", "not-image".getBytes()))
                            .with(patchMethod()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value(2003));
        }

        @Test
        @DisplayName("허용되지 않은 확장자는 400 이다")
        void disallowedExtensionIsRejected() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            authenticateAs(fixture.host().getId());

            mockMvc.perform(multipart("/api/study-room/{roomId}/thumbnail", fixture.roomId())
                            .file(new MockMultipartFile("thumbnail", "t.gif", "image/gif", pngBytes()))
                            .with(patchMethod()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value(2003));
        }

        @Test
        @DisplayName("방장은 이미 업로드된 S3 URL 로 썸네일을 지정할 수 있다")
        void hostCanSetThumbnailByUrl() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            authenticateAs(fixture.host().getId());

            mockMvc.perform(patch("/api/study-room/{roomId}/thumbnail-url", fixture.roomId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new ThumbnailUrlUpdateRequest("https://cdn.example.com/by-url.png"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.thumbnailUrl").value("https://cdn.example.com/by-url.png"));

            assertThat(roomRepository.findById(fixture.roomId()).orElseThrow().getThumbnailUrl())
                    .as("저장된 썸네일 URL")
                    .isEqualTo("https://cdn.example.com/by-url.png");
        }

        @Test
        @DisplayName("URL 지정도 방장만 할 수 있다")
        void memberCannotSetThumbnailByUrl() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            authenticateAs(fixture.member().getId());

            mockMvc.perform(patch("/api/study-room/{roomId}/thumbnail-url", fixture.roomId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new ThumbnailUrlUpdateRequest("https://cdn.example.com/x.png"))))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value(10003));

            assertThat(roomRepository.findById(fixture.roomId()).orElseThrow().getThumbnailUrl())
                    .as("변경되지 않은 썸네일").isNull();
        }

        @Test
        @DisplayName("S3 URL 검증에 걸리면 400 이다")
        void invalidS3UrlIsRejected() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            authenticateAs(fixture.host().getId());
            org.mockito.BDDMockito.willThrow(new com.aisip.OnO.backend.common.exception.ApplicationException(
                            com.aisip.OnO.backend.util.fileupload.exception.FileUploadErrorCase.INVALID_IMAGE_FILE))
                    .given(fileUploadService).validateS3Url("https://evil.example.com/x.png");

            mockMvc.perform(patch("/api/study-room/{roomId}/thumbnail-url", fixture.roomId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new ThumbnailUrlUpdateRequest("https://evil.example.com/x.png"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value(2003));
        }

        @Test
        @DisplayName("새 썸네일로 바꾸면 이전 썸네일은 커밋 후 삭제 대상이 된다")
        void previousThumbnailIsScheduledForDeletion() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            authenticateAs(fixture.host().getId());
            given(fileUploadService.uploadFileToS3(any())).willReturn("https://cdn.example.com/first.png");
            mockMvc.perform(multipart("/api/study-room/{roomId}/thumbnail", fixture.roomId())
                            .file(new MockMultipartFile("thumbnail", "t.png", "image/png", pngBytes()))
                            .with(patchMethod()))
                    .andExpect(status().isOk());

            given(fileUploadService.uploadFileToS3(any())).willReturn("https://cdn.example.com/second.png");
            mockMvc.perform(multipart("/api/study-room/{roomId}/thumbnail", fixture.roomId())
                            .file(new MockMultipartFile("thumbnail", "t.png", "image/png", pngBytes()))
                            .with(patchMethod()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.thumbnailUrl").value("https://cdn.example.com/second.png"));

            assertThat(roomRepository.findById(fixture.roomId()).orElseThrow().getThumbnailUrl())
                    .as("최종 썸네일").isEqualTo("https://cdn.example.com/second.png");
        }

        @Test
        @DisplayName("5MB 를 넘는 파일은 400 이다")
        void oversizedThumbnailIsRejected() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            authenticateAs(fixture.host().getId());
            byte[] oversized = new byte[5 * 1024 * 1024 + 1];
            System.arraycopy(pngBytes(), 0, oversized, 0, pngBytes().length);

            mockMvc.perform(multipart("/api/study-room/{roomId}/thumbnail", fixture.roomId())
                            .file(new MockMultipartFile("thumbnail", "t.png", "image/png", oversized))
                            .with(patchMethod()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value(2004));
        }
    }

    @Nested
    @DisplayName("삭제")
    class DeleteRoom {

        @Test
        @DisplayName("방장이 삭제하면 방과 멤버십이 함께 사라진다")
        void hostDeletesRoomWithMemberships() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            authenticateAs(fixture.host().getId());

            mockMvc.perform(delete("/api/study-room/{roomId}", fixture.roomId()))
                    .andExpect(status().isOk());

            assertThat(roomRepository.findById(fixture.roomId())).as("삭제된 방").isEmpty();
            assertThat(memberRepository.countByRoomId(fixture.roomId())).as("남은 멤버십 수").isZero();
        }

        @Test
        @DisplayName("일반 멤버는 방을 삭제할 수 없다")
        void memberCannotDelete() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            authenticateAs(fixture.member().getId());

            mockMvc.perform(delete("/api/study-room/{roomId}", fixture.roomId()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value(10003));

            assertThat(roomRepository.findById(fixture.roomId())).as("삭제되지 않은 방").isPresent();
        }

        @Test
        @DisplayName("비멤버는 방을 삭제할 수 없다")
        void nonMemberCannotDelete() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            authenticateAs(fixture.outsider().getId());

            mockMvc.perform(delete("/api/study-room/{roomId}", fixture.roomId()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value(10002));

            assertThat(roomRepository.findById(fixture.roomId())).as("삭제되지 않은 방").isPresent();
        }
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor patchMethod() {
        return request -> {
            request.setMethod("PATCH");
            return request;
        };
    }
}
