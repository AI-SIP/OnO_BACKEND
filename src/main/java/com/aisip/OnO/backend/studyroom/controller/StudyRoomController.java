package com.aisip.OnO.backend.studyroom.controller;

import com.aisip.OnO.backend.common.response.CommonResponse;
import com.aisip.OnO.backend.studyroom.dto.StudyRoomDtos.*;
import com.aisip.OnO.backend.studyroom.service.StudyRoomInviteService;
import com.aisip.OnO.backend.studyroom.service.StudyRoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/study-room")
public class StudyRoomController {

    private final StudyRoomService studyRoomService;
    private final StudyRoomInviteService inviteService;

    @GetMapping
    public CommonResponse<List<StudyRoomListResponse>> getMyRooms() {
        return CommonResponse.success(studyRoomService.getMyRooms(currentUserId()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CommonResponse<StudyRoomDetailResponse> createRoom(@RequestBody StudyRoomCreateRequest request) {
        return CommonResponse.success(studyRoomService.createRoom(request, currentUserId()));
    }

    @GetMapping("/{roomId}")
    public CommonResponse<StudyRoomDetailResponse> getRoom(@PathVariable("roomId") Long roomId) {
        return CommonResponse.success(studyRoomService.getRoom(roomId, currentUserId()));
    }

    @PatchMapping("/{roomId}")
    public CommonResponse<StudyRoomDetailResponse> updateRoom(@PathVariable("roomId") Long roomId,
                                                              @RequestBody StudyRoomUpdateRequest request) {
        return CommonResponse.success(studyRoomService.updateRoom(roomId, currentUserId(), request));
    }

    @DeleteMapping("/{roomId}")
    public CommonResponse<String> deleteRoom(@PathVariable("roomId") Long roomId) {
        studyRoomService.deleteRoom(roomId, currentUserId());
        return CommonResponse.success("스터디룸 삭제가 완료되었습니다.");
    }

    @PostMapping("/{roomId}/invite")
    public CommonResponse<InviteCodeResponse> issueInviteCode(@PathVariable("roomId") Long roomId) {
        return CommonResponse.success(inviteService.issueInviteCode(roomId, currentUserId()));
    }

    @PostMapping("/join")
    public CommonResponse<StudyRoomDetailResponse> join(@RequestBody StudyRoomJoinRequest request) {
        return CommonResponse.success(inviteService.join(request, currentUserId()));
    }

    @DeleteMapping("/{roomId}/leave")
    public CommonResponse<String> leaveRoom(@PathVariable("roomId") Long roomId) {
        studyRoomService.leaveRoom(roomId, currentUserId());
        return CommonResponse.success("스터디룸 탈퇴가 완료되었습니다.");
    }

    @DeleteMapping("/{roomId}/members/{memberId}")
    public CommonResponse<String> kickMember(@PathVariable("roomId") Long roomId,
                                             @PathVariable("memberId") Long memberId) {
        studyRoomService.kickMember(roomId, memberId, currentUserId());
        return CommonResponse.success("스터디룸 멤버 강퇴가 완료되었습니다.");
    }

    @PutMapping("/{roomId}/members/me/goal")
    public CommonResponse<GoalUpdateResponse> updateGoal(@PathVariable("roomId") Long roomId,
                                                         @RequestBody StudyRoomGoalUpdateRequest request) {
        return CommonResponse.success(studyRoomService.updateGoal(roomId, currentUserId(), request));
    }

    @PatchMapping(value = "/{roomId}/thumbnail", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CommonResponse<StudyRoomThumbnailUpdateResponse> updateThumbnail(@PathVariable("roomId") Long roomId,
                                                                           @RequestParam("thumbnail") MultipartFile thumbnail) {
        return CommonResponse.success(studyRoomService.updateThumbnail(roomId, currentUserId(), thumbnail));
    }

    private Long currentUserId() {
        return (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
