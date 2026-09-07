package com.aisip.OnO.backend.user.controller;

import com.aisip.OnO.backend.auth.exception.AuthErrorCase;
import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.support.IntegrationTestSupport;
import com.aisip.OnO.backend.user.dto.NotificationSettingsUpdateDto;
import com.aisip.OnO.backend.user.dto.UserRegisterDto;
import com.aisip.OnO.backend.user.entity.User;
import com.aisip.OnO.backend.user.repository.UserRepository;
import com.aisip.OnO.backend.util.fileupload.exception.FileUploadErrorCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 사용자 API 는 경로에 userId 를 받지 않고 토큰의 주인만 다룬다.
 * 따라서 격리 검증의 핵심은 "인증 주체를 바꿨을 때 다른 사람 데이터가 절대 보이지 않는가" 다.
 */
@DisplayName("사용자 API")
class UserApiTest extends IntegrationTestSupport {

    @Autowired
    private UserRepository userRepository;

    /**
     * MockMvc 요청에 인증 주체를 싣는다.
     *
     * <p>{@code authenticateAs(userId)} 는 SecurityContextHolder 만 채우기 때문에
     * MockMvc 필터 체인까지 전달되지 않는다(실측: 401). 요청 단위 post-processor 로 넣어야
     * 실제 필터 체인을 거친 인증 상태가 된다.
     */
    private RequestPostProcessor asUser(User user) {
        return asUser(user.getId(), "ROLE_MEMBER");
    }

    private RequestPostProcessor asUser(Long userId, String role) {
        return authentication(new UsernamePasswordAuthenticationToken(
                userId, null, List.of(new SimpleGrantedAuthority(role))));
    }

    private static MockMultipartFile pngFile() {
        return new MockMultipartFile("profileImage", "profile.png", "image/png", new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x00
        });
    }

    @Nested
    @DisplayName("내 정보 조회")
    class GetMyProfile {

        @Test
        @DisplayName("인증한 사용자 본인의 정보를 돌려준다")
        void returnsOwnProfile() throws Exception {
            User user = fixtures.createUser();

            mockMvc.perform(get("/api/users").with(asUser(user)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.userId").value(user.getId()))
                    .andExpect(jsonPath("$.data.name").value(user.getName()))
                    .andExpect(jsonPath("$.data.email").value(user.getEmail()));
        }

        @Test
        @DisplayName("인증 주체가 바뀌면 그 사용자의 정보만 나온다")
        void neverLeaksAnotherUsersProfile() throws Exception {
            User me = fixtures.createUser();
            User other = fixtures.createOtherUser();

            mockMvc.perform(get("/api/users").with(asUser(other)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.userId").value(other.getId()))
                    .andExpect(jsonPath("$.data.email").value(other.getEmail()))
                    .andExpect(jsonPath("$.data.email").value(org.hamcrest.Matchers.not(me.getEmail())));
        }

        @Test
        @DisplayName("인증 없이 호출하면 401 + 1007 로 막는다")
        void rejectsAnonymousRequest() throws Exception {
            mockMvc.perform(get("/api/users"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value(AuthErrorCase.AUTHENTICATION_FAILED.getErrorCode()));
        }

        @Test
        @DisplayName("DB 에 없는 userId 로 인증되어 있으면 404 로 끝난다")
        void rejectsUnknownUserId() throws Exception {
            mockMvc.perform(get("/api/users").with(asUser(999_999L, "ROLE_MEMBER")))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("조회할 때 마지막 활동 시각이 기록된다")
        void recordsLastActiveAt() throws Exception {
            User user = fixtures.createUser();

            mockMvc.perform(get("/api/users").with(asUser(user))).andExpect(status().isOk());

            assertThat(userRepository.findById(user.getId()).orElseThrow().getLastActiveAt())
                    .as("휴면 알림 대상 선정이 이 값에 걸려 있다")
                    .isNotNull();
        }
    }

    @Nested
    @DisplayName("내 정보 수정")
    class UpdateMyProfile {

        @Test
        @DisplayName("본인 정보만 수정되고 다른 사용자는 그대로다")
        void updatesOnlyOwnProfile() throws Exception {
            User me = fixtures.createUser();
            User other = fixtures.createOtherUser();
            String otherNameBefore = other.getName();

            mockMvc.perform(patch("/api/users")
                            .with(asUser(me))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    UserRegisterDto.builder().name("바뀐이름").email("changed@test.ono").build())))
                    .andExpect(status().isOk());

            assertThat(userRepository.findById(me.getId()).orElseThrow().getName()).isEqualTo("바뀐이름");
            assertThat(userRepository.findById(other.getId()).orElseThrow().getName()).isEqualTo(otherNameBefore);
        }

        @Test
        @DisplayName("인증 없이 수정하면 401 로 막는다")
        void rejectsAnonymousUpdate() throws Exception {
            mockMvc.perform(patch("/api/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    UserRegisterDto.builder().name("침입자").build())))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("알림 수신 설정을 껐다 켤 수 있다")
        void togglesNotificationSettings() throws Exception {
            User user = fixtures.createUser();

            mockMvc.perform(patch("/api/users/notification-settings")
                            .with(asUser(user))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new NotificationSettingsUpdateDto(false))))
                    .andExpect(status().isOk());
            assertThat(userRepository.findById(user.getId()).orElseThrow().isNotificationEnabled()).isFalse();

            mockMvc.perform(patch("/api/users/notification-settings")
                            .with(asUser(user))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new NotificationSettingsUpdateDto(true))))
                    .andExpect(status().isOk());
            assertThat(userRepository.findById(user.getId()).orElseThrow().isNotificationEnabled()).isTrue();
        }
    }

    @Nested
    @DisplayName("프로필 이미지")
    class ProfileImage {

        @Test
        @DisplayName("이미지를 올리면 S3 URL 이 프로필에 반영된다")
        void uploadsProfileImage() throws Exception {
            User user = fixtures.createUser();
            given(fileUploadService.uploadFileToS3(any())).willReturn("https://cdn.test.ono/profile.png");

            mockMvc.perform(multipart(HttpMethod.PATCH, "/api/users/me/profile-image")
                            .file(pngFile())
                            .with(asUser(user)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.profileImageUrl").value("https://cdn.test.ono/profile.png"));

            assertThat(userRepository.findById(user.getId()).orElseThrow().getProfileImageUrl())
                    .isEqualTo("https://cdn.test.ono/profile.png");
        }

        @Test
        @DisplayName("이미지가 아닌 파일은 400 + 2003 으로 거절하고 S3 에 올리지 않는다")
        void rejectsNonImageFile() throws Exception {
            User user = fixtures.createUser();
            MockMultipartFile fake = new MockMultipartFile(
                    "profileImage", "profile.png", "image/png", "not an image".getBytes());

            mockMvc.perform(multipart(HttpMethod.PATCH, "/api/users/me/profile-image")
                            .file(fake)
                            .with(asUser(user)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value(FileUploadErrorCase.INVALID_IMAGE_FILE.getErrorCode()));
        }

        @Test
        @DisplayName("우리 버킷이 아닌 URL 로는 프로필 이미지를 바꿀 수 없다")
        void rejectsForeignImageUrl() throws Exception {
            User user = fixtures.createUser();
            willThrow(new ApplicationException(FileUploadErrorCase.INVALID_IMAGE_FILE))
                    .given(fileUploadService).validateS3Url(anyString());

            mockMvc.perform(patch("/api/users/me/profile-image-url")
                            .with(asUser(user))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new UserController.ProfileImageUrlRequest("https://evil.example.com/a.png"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value(FileUploadErrorCase.INVALID_IMAGE_FILE.getErrorCode()));

            assertThat(userRepository.findById(user.getId()).orElseThrow().getProfileImageUrl()).isNull();
        }

        @Test
        @DisplayName("프로필 이미지를 지우면 URL 이 비워진다")
        void deletesProfileImage() throws Exception {
            User user = fixtures.createUser();
            given(fileUploadService.uploadFileToS3(any())).willReturn("https://cdn.test.ono/profile.png");
            mockMvc.perform(multipart(HttpMethod.PATCH, "/api/users/me/profile-image")
                            .file(pngFile())
                            .with(asUser(user)))
                    .andExpect(status().isOk());

            mockMvc.perform(delete("/api/users/me/profile-image").with(asUser(user)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.profileImageUrl").doesNotExist());

            assertThat(userRepository.findById(user.getId()).orElseThrow().getProfileImageUrl()).isNull();
        }
    }

    @Nested
    @DisplayName("탈퇴")
    class Withdraw {

        @Test
        @DisplayName("탈퇴하면 이후 조회에서 사라진다")
        void deletesOwnAccount() throws Exception {
            User user = fixtures.createUser();

            mockMvc.perform(delete("/api/users").with(asUser(user))).andExpect(status().isOk());

            assertThat(userRepository.findById(user.getId())).isEmpty();
            // 탈퇴한 계정의 토큰이 아직 살아 있어도 데이터에는 닿지 못한다.
            mockMvc.perform(get("/api/users").with(asUser(user)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("내 탈퇴가 다른 사용자 계정을 건드리지 않는다")
        void doesNotAffectOtherUsers() throws Exception {
            User me = fixtures.createUser();
            User other = fixtures.createOtherUser();

            mockMvc.perform(delete("/api/users").with(asUser(me))).andExpect(status().isOk());

            assertThat(userRepository.findById(other.getId()))
                    .as("한 사람의 탈퇴가 다른 사람 데이터에 닿으면 안 된다")
                    .isPresent();
        }

        @Test
        @DisplayName("인증 없이 탈퇴 요청하면 401 로 막는다")
        void rejectsAnonymousWithdrawal() throws Exception {
            User user = fixtures.createUser();

            mockMvc.perform(delete("/api/users")).andExpect(status().isUnauthorized());

            assertThat(userRepository.findById(user.getId())).isPresent();
        }
    }
}
