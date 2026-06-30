package com.aisip.OnO.backend.user.controller;

import com.aisip.OnO.backend.common.response.CommonResponse;
import com.aisip.OnO.backend.mission.service.MissionLogService;
import com.aisip.OnO.backend.user.dto.NotificationSettingsUpdateDto;
import com.aisip.OnO.backend.user.dto.UserRegisterDto;
import com.aisip.OnO.backend.user.dto.UserResponseDto;
import com.aisip.OnO.backend.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final MissionLogService missionLogService;

    // ✅ 사용자 정보 조회
    @GetMapping("")
    public CommonResponse<UserResponseDto> getUserInfo() {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        missionLogService.registerLoginMission(userId);
        userService.touchLastActiveAt(userId);

        return CommonResponse.success(userService.findUser(userId));
    }

    // ✅ 사용자 정보 수정
    @PatchMapping("")
    public CommonResponse<String> updateUserInfo(@RequestBody UserRegisterDto userRegisterDto) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        userService.updateUser(userId, userRegisterDto);

        return CommonResponse.success("사용자 정보 수정이 완료되었습니다.");
    }

    // ✅ 알림 수신 설정
    @PatchMapping("/notification-settings")
    public CommonResponse<String> updateNotificationSettings(
            @RequestBody NotificationSettingsUpdateDto dto) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        userService.updateNotificationSettings(userId, dto.notificationEnabled());

        return CommonResponse.success("알림 설정이 변경되었습니다.");
    }

    @PatchMapping(value = "/me/profile-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CommonResponse<UserResponseDto> updateProfileImage(@RequestParam("profileImage") MultipartFile profileImage) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return CommonResponse.success(userService.updateProfileImage(userId, profileImage));
    }

    @PatchMapping(value = "/me/profile-image-url", consumes = MediaType.APPLICATION_JSON_VALUE)
    public CommonResponse<UserResponseDto> updateProfileImageByUrl(@RequestBody ProfileImageUrlRequest request) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return CommonResponse.success(userService.updateProfileImageByUrl(userId, request.profileImageUrl()));
    }

    @DeleteMapping("/me/profile-image")
    public CommonResponse<UserResponseDto> deleteProfileImage() {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return CommonResponse.success(userService.deleteProfileImage(userId));
    }

    // ✅ 사용자 계정 삭제
    @DeleteMapping("")
    public void deleteUserInfo() {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        userService.deleteUserById(userId);
    }

    public record ProfileImageUrlRequest(String profileImageUrl) {}
}
