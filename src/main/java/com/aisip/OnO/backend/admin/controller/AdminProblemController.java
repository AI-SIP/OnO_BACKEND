package com.aisip.OnO.backend.admin.controller;

import com.aisip.OnO.backend.admin.dto.AdminLearningDto.ProblemDetail;
import com.aisip.OnO.backend.admin.dto.AdminLearningDto.ProblemRow;
import com.aisip.OnO.backend.admin.dto.AdminLearningDto.SolveRow;
import com.aisip.OnO.backend.admin.dto.AdminPager;
import com.aisip.OnO.backend.admin.repository.AdminLearningQueryRepository;
import com.aisip.OnO.backend.admin.repository.AdminLearningQueryRepository.Filter;
import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.problem.entity.AnalysisStatus;
import com.aisip.OnO.backend.problem.exception.ProblemErrorCase;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Controller
@RequestMapping("/admin")
public class AdminProblemController {

    private final AdminLearningQueryRepository learningQueryRepository;
    private final ObjectMapper objectMapper;

    @GetMapping("/problems")
    public String getAllProblems(
            @RequestParam(defaultValue = "0", name = "page") int page,
            @RequestParam(defaultValue = "20", name = "size") int size,
            @RequestParam(name = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(name = "userId", required = false) Long userId,
            @RequestParam(name = "status", required = false) String status,
            HttpServletRequest request,
            Model model
    ) {
        int selectedSize = Math.max(size, 1);
        String selectedStatus = normalizeStatus(status);
        Filter filter = new Filter(date, userId, selectedStatus);

        long total = learningQueryRepository.countProblems(filter);
        int totalPages = (int) Math.ceil((double) total / selectedSize);
        int selectedPage = Math.max(page, 0);
        // 범위를 넘은 page 로 빈 화면을 보여 주면 데이터가 사라진 줄 안다. 마지막 페이지로 되돌린다.
        if (selectedPage >= totalPages) {
            selectedPage = Math.max(totalPages - 1, 0);
        }

        List<ProblemRow> problems = learningQueryRepository.findProblems(filter, selectedPage * selectedSize, selectedSize);

        model.addAttribute("problems", problems);
        model.addAttribute("totalProblems", total);
        model.addAttribute("currentPage", selectedPage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("size", selectedSize);
        model.addAttribute("pager", AdminPager.of(request, "page", selectedPage, selectedSize, total));
        model.addAttribute("summary", learningQueryRepository.summarizeProblems(LocalDate.now()));
        model.addAttribute("statuses", AnalysisStatus.values());
        model.addAttribute("filterDate", date);
        model.addAttribute("filterUserId", userId);
        model.addAttribute("filterUserName", userId == null ? null : learningQueryRepository.findUserName(userId));
        model.addAttribute("filterStatus", selectedStatus);
        model.addAttribute("filtered", date != null || userId != null || selectedStatus != null);

        return "problems";
    }

    @GetMapping("/problem/{problemId}")
    public String getProblemDetail(@PathVariable(name = "problemId") Long problemId, Model model) {
        ProblemDetail problem = learningQueryRepository.findProblemDetail(problemId)
                .orElseThrow(() -> new ApplicationException(ProblemErrorCase.PROBLEM_NOT_FOUND));
        List<SolveRow> problemSolves = learningQueryRepository.findSolvesOfProblem(problemId);

        model.addAttribute("problem", problem);
        model.addAttribute("keyPoints", parseKeyPoints(problem.keyPoints()));
        model.addAttribute("images", learningQueryRepository.findProblemImages(problemId));
        model.addAttribute("tags", learningQueryRepository.findProblemTags(problemId));
        model.addAttribute("practiceNotes", learningQueryRepository.findNotesContainingProblem(problemId));
        model.addAttribute("sharedRooms", learningQueryRepository.findRoomsSharingProblem(problemId));
        model.addAttribute("problemSolves", problemSolves);
        model.addAttribute("problemSolveCount", problemSolves.size());

        return "problem";
    }

    /** 모르는 상태값은 필터를 걸지 않은 것으로 본다. 잘못된 주소로 500 을 내지 않기 위해서다. */
    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return Arrays.stream(AnalysisStatus.values())
                .map(Enum::name)
                .filter(name -> name.equalsIgnoreCase(status.trim()))
                .findFirst()
                .orElse(null);
    }

    /** 핵심 포인트는 JSON 배열 문자열이거나, "• " 로 시작하는 줄을 이어 붙인 평문이다. */
    private List<String> parseKeyPoints(String keyPoints) {
        if (keyPoints == null || keyPoints.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(keyPoints, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            return Arrays.stream(keyPoints.split("\\r?\\n|\\\\n"))
                    .map(line -> line.replaceFirst("^\\s*[•\\-*]\\s*", "").trim())
                    .filter(line -> !line.isEmpty())
                    .toList();
        }
    }
}
