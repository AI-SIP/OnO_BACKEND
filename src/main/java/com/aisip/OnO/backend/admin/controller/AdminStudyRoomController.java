package com.aisip.OnO.backend.admin.controller;

import com.aisip.OnO.backend.admin.dto.AdminStudyRoomDetailDto;
import com.aisip.OnO.backend.admin.dto.AdminStudyRoomSummaryDto;
import com.aisip.OnO.backend.studyroom.entity.StudyRoom;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomChallenge;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomMember;
import com.aisip.OnO.backend.studyroom.repository.StudyRoomChallengeRepository;
import com.aisip.OnO.backend.studyroom.repository.StudyRoomMemberRepository;
import com.aisip.OnO.backend.studyroom.repository.StudyRoomRepository;
import com.aisip.OnO.backend.studyroom.repository.StudyRoomSharedProblemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/study-rooms")
@RequiredArgsConstructor
public class AdminStudyRoomController {

    private final StudyRoomRepository studyRoomRepository;
    private final StudyRoomMemberRepository studyRoomMemberRepository;
    private final StudyRoomChallengeRepository studyRoomChallengeRepository;
    private final StudyRoomSharedProblemRepository studyRoomSharedProblemRepository;

    @GetMapping
    public String list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model
    ) {
        Page<StudyRoom> pageResult = studyRoomRepository.findAll(
                PageRequest.of(page, size, Sort.by("createdAt").descending()));

        List<Long> roomIds = pageResult.stream().map(StudyRoom::getId).toList();

        Map<Long, Long> memberCountMap = studyRoomMemberRepository.countMembersByRoomIds(roomIds)
                .stream().collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));

        Map<Long, Long> sharedProblemCountMap = studyRoomSharedProblemRepository.countSharedProblemsByRoomIds(roomIds)
                .stream().collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));

        List<AdminStudyRoomSummaryDto> rooms = pageResult.stream()
                .map(r -> AdminStudyRoomSummaryDto.from(
                        r,
                        memberCountMap.getOrDefault(r.getId(), 0L),
                        sharedProblemCountMap.getOrDefault(r.getId(), 0L)))
                .toList();

        model.addAttribute("rooms", rooms);
        model.addAttribute("totalCount", pageResult.getTotalElements());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", pageResult.getTotalPages());
        model.addAttribute("size", size);

        int blockSize = 10;
        int blockStart = (page / blockSize) * blockSize;
        int blockEnd = Math.min(blockStart + blockSize - 1, pageResult.getTotalPages() - 1);
        model.addAttribute("pageBlockStart", blockStart);
        model.addAttribute("pageBlockEnd", Math.max(blockEnd, blockStart));
        model.addAttribute("hasPreviousBlock", blockStart > 0);
        model.addAttribute("hasNextBlock", blockEnd < pageResult.getTotalPages() - 1);

        return "admin-study-rooms";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        StudyRoom room = studyRoomRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("스터디룸을 찾을 수 없습니다: " + id));

        List<StudyRoomMember> members = studyRoomMemberRepository.findAllWithUserByRoomId(id);
        List<StudyRoomChallenge> challenges = studyRoomChallengeRepository.findAllByRoomIdOrderByEndAtAsc(id);
        long sharedProblemCount = studyRoomSharedProblemRepository.countByRoomId(id);

        model.addAttribute("room", AdminStudyRoomDetailDto.from(room, members, challenges, sharedProblemCount));
        return "admin-study-room-detail";
    }
}
