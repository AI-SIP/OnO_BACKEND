package com.aisip.OnO.backend.admin.controller;

import com.aisip.OnO.backend.problem.dto.ProblemResponseDto;
import com.aisip.OnO.backend.problem.service.ProblemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Controller
@RequestMapping("/admin")
public class AdminProblemController {

    private final ProblemService problemService;

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
}