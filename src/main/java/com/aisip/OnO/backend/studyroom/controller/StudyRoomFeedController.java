package com.aisip.OnO.backend.studyroom.controller;

import com.aisip.OnO.backend.common.response.CommonResponse;
import com.aisip.OnO.backend.common.response.CursorPageResponse;
import com.aisip.OnO.backend.studyroom.dto.StudyRoomDtos.*;
import com.aisip.OnO.backend.studyroom.service.StudyRoomFeedService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/study-room/{roomId}/feed")
public class StudyRoomFeedController {

    private final StudyRoomFeedService feedService;

    @GetMapping
    public CommonResponse<CursorPageResponse<FeedItemResponse>> getFeed(@PathVariable Long roomId,
                                                                        @RequestParam(value = "cursor", required = false) Long cursor,
                                                                        @RequestParam(defaultValue = "30") int size) {
        return CommonResponse.success(feedService.getFeed(roomId, currentUserId(), cursor, size));
    }

    @PostMapping("/{feedId}/reactions")
    public CommonResponse<FeedReactionToggleResponse> toggleReaction(@PathVariable Long roomId,
                                                                     @PathVariable Long feedId,
                                                                     @RequestBody ReactionToggleRequest request) {
        return CommonResponse.success(feedService.toggleReaction(roomId, feedId, currentUserId(), request));
    }

    private Long currentUserId() {
        return (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
