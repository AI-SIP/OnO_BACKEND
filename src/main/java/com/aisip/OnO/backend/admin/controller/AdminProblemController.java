package com.aisip.OnO.backend.admin.controller;

import com.aisip.OnO.backend.admin.dto.AdminProblemResponseDto;
import com.aisip.OnO.backend.folder.dto.FolderResponseDto;
import com.aisip.OnO.backend.folder.entity.Folder;
import com.aisip.OnO.backend.folder.service.FolderService;
import com.aisip.OnO.backend.problem.dto.ProblemResponseDto;
import com.aisip.OnO.backend.problem.service.ProblemService;
import com.aisip.OnO.backend.problemsolve.dto.ProblemSolveResponseDto;
import com.aisip.OnO.backend.problemsolve.service.ProblemSolveService;
import com.aisip.OnO.backend.user.dto.UserResponseDto;
import com.aisip.OnO.backend.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Controller
@RequestMapping("/admin")
public class AdminProblemController {

    private final ProblemService problemService;
    private final UserService userService;
    private final FolderService folderService;
    private final ProblemSolveService problemSolveService;

    @GetMapping("/problems")
    public String getAllProblems(
            @RequestParam(defaultValue = "0", name = "page") int page,
            @RequestParam(defaultValue = "20", name = "size") int size,
            Model model
    ) {
        int selectedPage = Math.max(page, 0);
        int selectedSize = Math.max(size, 1);
        Page<AdminProblemResponseDto> problemPage = problemService.findAdminProblems(selectedPage, selectedSize);
        int totalPages = problemPage.getTotalPages();
        int pageBlockStart = (selectedPage / 10) * 10;
        int pageBlockEnd = Math.min(pageBlockStart + 9, Math.max(totalPages - 1, 0));

        model.addAttribute("problems", problemPage.getContent());
        model.addAttribute("currentPage", selectedPage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalProblems", problemPage.getTotalElements());
        model.addAttribute("size", selectedSize);
        model.addAttribute("pageStartItem", problemPage.isEmpty() ? 0 : selectedPage * selectedSize + 1);
        model.addAttribute("pageEndItem", selectedPage * selectedSize + problemPage.getNumberOfElements());
        model.addAttribute("pageBlockStart", pageBlockStart);
        model.addAttribute("pageBlockEnd", pageBlockEnd);
        model.addAttribute("hasPreviousBlock", pageBlockStart > 0);
        model.addAttribute("hasNextBlock", pageBlockEnd < totalPages - 1);

        return "problems";
    }

    @GetMapping("/problem/{problemId}")
    public String getProblemDetail(@PathVariable(name = "problemId") Long problemId, Model model) {
        ProblemResponseDto problem = problemService.findProblem(problemId);
        model.addAttribute("problem", problem);

        // 폴더 및 작성자 정보 조회
        FolderResponseDto folder = folderService.findFolder(problem.folderId());
        UserResponseDto user = userService.findUser(folder.userId());
        List<ProblemSolveResponseDto> problemSolves = problemSolveService.getAdminProblemSolvesByProblemId(problemId);
        model.addAttribute("folder", folder);
        model.addAttribute("user", user);
        model.addAttribute("problemSolves", problemSolves);
        model.addAttribute("problemSolveCount", problemSolves.size());

        return "problem";
    }
}
