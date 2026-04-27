package com.aisip.OnO.backend.admin.controller;

import com.aisip.OnO.backend.admin.dto.AdminUserResponseDto;
import com.aisip.OnO.backend.folder.dto.FolderResponseDto;
import com.aisip.OnO.backend.folder.service.FolderService;
import com.aisip.OnO.backend.mission.entity.MissionLog;
import com.aisip.OnO.backend.mission.service.MissionLogService;
import com.aisip.OnO.backend.practicenote.dto.PracticeNoteDetailResponseDto;
import com.aisip.OnO.backend.practicenote.service.PracticeNoteService;
import com.aisip.OnO.backend.problem.dto.ProblemResponseDto;
import com.aisip.OnO.backend.problem.service.ProblemService;
import com.aisip.OnO.backend.user.dto.UserRegisterDto;
import com.aisip.OnO.backend.user.dto.UserResponseDto;
import com.aisip.OnO.backend.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Controller
@RequestMapping("/admin")
public class AdminUserController {

    private final UserService userService;
    private final ProblemService problemService;
    private final FolderService folderService;
    private final PracticeNoteService practiceNoteService;
    private final MissionLogService missionLogService;

    @GetMapping("/users")
    public String getAllUsers(
            @RequestParam(defaultValue = "0", name = "page") int page,
            @RequestParam(defaultValue = "20", name = "size") int size,
            @RequestParam(defaultValue = "createdAt", name = "sortBy") String sortBy,
            @RequestParam(defaultValue = "desc", name = "direction") String direction,
            Model model
    ) {
        int selectedPage = Math.max(page, 0);
        int selectedSize = Math.max(size, 1);
        Page<AdminUserResponseDto> userPage = userService.findAdminUsers(selectedPage, selectedSize, sortBy, direction);
        int totalPages = userPage.getTotalPages();
        int pageBlockStart = (selectedPage / 10) * 10;
        int pageBlockEnd = Math.min(pageBlockStart + 9, Math.max(totalPages - 1, 0));

        model.addAttribute("users", userPage.getContent());
        model.addAttribute("currentPage", selectedPage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalUsers", userPage.getTotalElements());
        model.addAttribute("size", selectedSize);
        model.addAttribute("pageStartItem", userPage.isEmpty() ? 0 : selectedPage * selectedSize + 1);
        model.addAttribute("pageEndItem", selectedPage * selectedSize + userPage.getNumberOfElements());
        model.addAttribute("sortBy", sortBy);
        model.addAttribute("direction", direction);
        model.addAttribute("pageBlockStart", pageBlockStart);
        model.addAttribute("pageBlockEnd", pageBlockEnd);
        model.addAttribute("hasPreviousBlock", pageBlockStart > 0);
        model.addAttribute("hasNextBlock", pageBlockEnd < totalPages - 1);

        return "users";
    }

    @GetMapping("/user/{userId}")
    public String getUserDetailsById(@PathVariable(name = "userId") Long userId, Model model) {
        UserResponseDto user = userService.findUser(userId);
        model.addAttribute("user", user);

        // 문제 정보
        List<ProblemResponseDto> problems = problemService.findUserProblems(userId);
        model.addAttribute("problems", problems);
        Long problemCount = problemService.findProblemCountByUser(userId);
        model.addAttribute("problemCount", problemCount);

        // 폴더 정보
        List<FolderResponseDto> folders = folderService.findAllUserFolders(userId);
        model.addAttribute("folders", folders);
        model.addAttribute("folderCount", folders.size());

        // 복습노트 정보
        List<PracticeNoteDetailResponseDto> practiceNotes = practiceNoteService.findAllPracticesByUser(userId);
        model.addAttribute("practiceNotes", practiceNotes);
        model.addAttribute("practiceNoteCount", practiceNotes.size());

        // 미션 기록 정보
        List<MissionLog> missionLogs = missionLogService.findAllByUserId(userId);
        model.addAttribute("missionLogs", missionLogs);
        model.addAttribute("missionLogCount", missionLogs.size());

        return "user";
    }

    @PostMapping("/user/{userId}")
    public String updateUserInfo(@PathVariable(name = "userId") Long userId, @ModelAttribute UserRegisterDto userRegisterDto, Model model) {
        userService.updateUser(userId, userRegisterDto);

        // 업데이트된 정보 다시 조회
        return "redirect:/admin/user/" + userId;
    }

    @PostMapping("/user/{userId}/level")
    @ResponseBody
    public String updateUserLevel(
            @PathVariable(name = "userId") Long userId,
            @RequestParam(name = "levelType") String levelType,
            @RequestParam(name = "levelValue") Long levelValue,
            @RequestParam(name = "pointValue") Long pointValue
    ) {
        userService.updateUserLevel(userId, levelType, levelValue, pointValue);
        return "success";
    }

    @ResponseStatus(HttpStatus.OK)
    @DeleteMapping("/user/{userId}")
    public void deleteUserInfo(@PathVariable(name = "userId") Long userId) {
        userService.deleteUserById(userId);
    }
}
