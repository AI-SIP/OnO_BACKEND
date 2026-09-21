package com.aisip.OnO.backend.admin.controller;

import com.aisip.OnO.backend.admin.dto.AdminPager;
import com.aisip.OnO.backend.admin.dto.AdminStudyRoomViews.Detail;
import com.aisip.OnO.backend.admin.dto.AdminStudyRoomViews.RoomHeader;
import com.aisip.OnO.backend.admin.dto.AdminStudyRoomViews.RoomRow;
import com.aisip.OnO.backend.admin.dto.AdminStudyRoomViews.SharedProblem;
import com.aisip.OnO.backend.admin.repository.AdminStudyRoomQueryRepository;
import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.mission.service.MissionPeriodKey;
import com.aisip.OnO.backend.studyroom.exception.StudyRoomErrorCase;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

@Controller
@RequestMapping("/admin/study-rooms")
@RequiredArgsConstructor
public class AdminStudyRoomController {

    /** 한 페이지 크기 상한. 너무 크게 넘기면 방마다 도는 집계 서브쿼리가 그만큼 늘어난다. */
    private static final int MAX_PAGE_SIZE = 200;

    private final AdminStudyRoomQueryRepository studyRoomQueryRepository;

    @GetMapping
    public String list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request,
            Model model
    ) {
        // 쿼리 파라미터는 사용자 입력이다. page 가 음수이거나 size 가 0 이하면 500 이 나지 않도록
        // 다른 관리자 목록 화면과 같은 방식으로 유효 범위에 맞춰 보정한다.
        int selectedPage = Math.max(page, 0);
        int selectedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        long totalCount = studyRoomQueryRepository.countRooms();
        List<RoomRow> rooms = studyRoomQueryRepository.findRooms(selectedPage * selectedSize, selectedSize);
        AdminPager pager = AdminPager.of(request, "page", selectedPage, selectedSize, totalCount);

        LocalDateTime weekStart = MissionPeriodKey.today()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .atStartOfDay();

        model.addAttribute("overview", studyRoomQueryRepository.overview(weekStart));
        model.addAttribute("rooms", rooms);
        model.addAttribute("totalCount", totalCount);
        model.addAttribute("currentPage", selectedPage);
        model.addAttribute("totalPages", pager.totalPages());
        model.addAttribute("size", selectedSize);
        model.addAttribute("pager", pager);

        return "admin-study-rooms";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        // 없는 리소스 조회는 500 이 아니라 404 다.
        RoomHeader room = studyRoomQueryRepository.findRoom(id)
                .orElseThrow(() -> new ApplicationException(StudyRoomErrorCase.STUDY_ROOM_NOT_FOUND));

        List<SharedProblem> sharedProblems = studyRoomQueryRepository.findSharedProblems(id);
        long totalCommentCount = sharedProblems.stream().mapToLong(SharedProblem::commentCount).sum();

        Detail detail = new Detail(
                room,
                studyRoomQueryRepository.findInviteCodes(id, MissionPeriodKey.now()),
                studyRoomQueryRepository.findMembers(id),
                studyRoomQueryRepository.findChallenges(id),
                sharedProblems,
                studyRoomQueryRepository.findRecentFeeds(id),
                studyRoomQueryRepository.findWeeklyReports(id),
                totalCommentCount,
                studyRoomQueryRepository.countReactions(id)
        );

        model.addAttribute("room", detail);
        return "admin-study-room-detail";
    }
}
