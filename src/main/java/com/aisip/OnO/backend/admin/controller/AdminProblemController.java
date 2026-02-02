package com.aisip.OnO.backend.admin.controller;

import com.aisip.OnO.backend.folder.dto.FolderResponseDto;
import com.aisip.OnO.backend.folder.entity.Folder;
import com.aisip.OnO.backend.folder.service.FolderService;
import com.aisip.OnO.backend.problem.dto.ProblemResponseDto;
import com.aisip.OnO.backend.problem.service.ProblemService;
import com.aisip.OnO.backend.user.dto.UserResponseDto;
import com.aisip.OnO.backend.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    @GetMapping("/problems")
    public String getAllProblems(
            @RequestParam(defaultValue = "0", name = "page") int page,
            @RequestParam(defaultValue = "20", name = "size") int size,
            Model model
    ) {
        List<ProblemResponseDto> allProblems = problemService.findAllProblems();

        // 페이징 계산
        int totalProblems = allProblems.size();
        int totalPages = (int) Math.ceil((double) totalProblems / size);
        int startIndex = page * size;
        int endIndex = Math.min(startIndex + size, totalProblems);

        List<ProblemResponseDto> pagedProblems = allProblems.subList(startIndex, endIndex);

        model.addAttribute("problems", pagedProblems);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalProblems", totalProblems);
        model.addAttribute("size", size);

        return "problems";
    }

    @GetMapping("/problem/{problemId}")
    public String getProblemDetail(@PathVariable(name = "problemId") Long problemId, Model model) {
        ProblemResponseDto problem = problemService.findProblem(problemId);
        model.addAttribute("problem", problem);

        // 폴더 및 작성자 정보 조회
        FolderResponseDto folder = folderService.findFolder(problem.folderId());
        UserResponseDto user = userService.findUser(folder.userId());
        model.addAttribute("folder", folder);
        model.addAttribute("user", user);

        return "problem";
    }
}