package com.aisip.OnO.backend.studyroom.controller;

import com.aisip.OnO.backend.common.response.CommonResponse;
import com.aisip.OnO.backend.studyroom.dto.StudyRoomDtos.*;
import com.aisip.OnO.backend.studyroom.service.StudyRoomInviteService;
import com.aisip.OnO.backend.studyroom.service.StudyRoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

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
    public CommonResponse<StudyRoomDetailResponse> getRoom(@PathVariable Long roomId) {
        return CommonResponse.success(studyRoomService.getRoom(roomId, currentUserId()));
    }

    @DeleteMapping("/{roomId}")
    public CommonResponse<String> deleteRoom(@PathVariable Long roomId) {
        studyRoomService.deleteRoom(roomId, currentUserId());
        return CommonResponse.success("스터디룸 삭제가 완료되었습니다.");
    }

    @PostMapping("/{roomId}/invite")
    public CommonResponse<InviteCodeResponse> issueInviteCode(@PathVariable Long roomId) {
        return CommonResponse.success(inviteService.issueInviteCode(roomId, currentUserId()));
    }

    @PostMapping("/join")
    public CommonResponse<StudyRoomDetailResponse> join(@RequestBody StudyRoomJoinRequest request) {
        return CommonResponse.success(inviteService.join(request, currentUserId()));
    }

    @DeleteMapping("/{roomId}/leave")
    public CommonResponse<String> leaveRoom(@PathVariable Long roomId) {
        studyRoomService.leaveRoom(roomId, currentUserId());
        return CommonResponse.success("스터디룸 탈퇴가 완료되었습니다.");
    }

    @DeleteMapping("/{roomId}/members/{memberId}")
    public CommonResponse<String> kickMember(@PathVariable Long roomId, @PathVariable Long memberId) {
        studyRoomService.kickMember(roomId, memberId, currentUserId());
        return CommonResponse.success("스터디룸 멤버 강퇴가 완료되었습니다.");
    }

    @PutMapping("/{roomId}/members/me/goal")
    public CommonResponse<GoalUpdateResponse> updateGoal(@PathVariable Long roomId,
                                                         @RequestBody StudyRoomGoalUpdateRequest request) {
        return CommonResponse.success(studyRoomService.updateGoal(roomId, currentUserId(), request));
    }

    private Long currentUserId() {
        return (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
