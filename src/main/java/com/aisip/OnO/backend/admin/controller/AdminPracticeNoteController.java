package com.aisip.OnO.backend.admin.controller;

import com.aisip.OnO.backend.admin.dto.AdminLearningDto.NoteProblemRow;
import com.aisip.OnO.backend.admin.dto.AdminLearningDto.NoteRow;
import com.aisip.OnO.backend.admin.dto.AdminLearningDto.SolveRow;
import com.aisip.OnO.backend.admin.dto.AdminPager;
import com.aisip.OnO.backend.admin.repository.AdminLearningQueryRepository;
import com.aisip.OnO.backend.admin.repository.AdminLearningQueryRepository.Filter;
import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.practicenote.entity.PracticeNote;
import com.aisip.OnO.backend.practicenote.entity.PracticeNotification;
import com.aisip.OnO.backend.practicenote.exception.PracticeNoteErrorCase;
import com.aisip.OnO.backend.practicenote.repository.PracticeNoteRepository;
import com.aisip.OnO.backend.problemsolve.entity.AnswerStatus;
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
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Controller
@RequestMapping("/admin")
public class AdminPracticeNoteController {

    /** 서비스 기준 시간대. 인자 없는 now() 는 서버 기본 시간대를 따라 하루가 밀릴 수 있다. */
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /** 복습노트 상세에서 보여 줄 최근 복습 기록 수. */
    private static final int RECENT_SOLVE_LIMIT = 50;

    private final AdminLearningQueryRepository learningQueryRepository;
    private final PracticeNoteRepository practiceNoteRepository;

    @GetMapping("/practice-notes")
    public String getPracticeNotes(
            @RequestParam(defaultValue = "0", name = "notePage") int notePage,
            @RequestParam(defaultValue = "20", name = "size") int size,
            @RequestParam(name = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(name = "userId", required = false) Long userId,
            HttpServletRequest request,
            Model model
    ) {
        int selectedSize = Math.max(size, 1);
        Filter filter = new Filter(date, userId, null);

        long total = learningQueryRepository.countNotes(filter);
        int totalPages = (int) Math.ceil((double) total / selectedSize);
        int selectedPage = clampPage(notePage, totalPages);

        List<NoteRow> notes = withWeekDays(
                learningQueryRepository.findNotes(filter, selectedPage * selectedSize, selectedSize));

        model.addAttribute("practiceNotes", notes);
        model.addAttribute("totalPracticeNotes", total);
        model.addAttribute("notePage", selectedPage);
        model.addAttribute("noteTotalPages", totalPages);
        model.addAttribute("size", selectedSize);
        model.addAttribute("pager", AdminPager.of(request, "notePage", selectedPage, selectedSize, total));
        model.addAttribute("summary", learningQueryRepository.summarizeNotes());
        addFilterAttributes(model, date, userId, null);

        return "practice-notes";
    }

    @GetMapping("/practice-notes/{noteId}")
    public String getPracticeNoteDetail(@PathVariable(name = "noteId") Long noteId, Model model) {
        NoteRow note = learningQueryRepository.findNote(noteId)
                .orElseThrow(() -> new ApplicationException(PracticeNoteErrorCase.PRACTICE_NOTE_NOT_FOUND));
        note = withWeekDays(List.of(note)).get(0);

        List<NoteProblemRow> problems = learningQueryRepository.findNoteProblems(noteId);
        List<Long> problemIds = problems.stream().map(NoteProblemRow::problemId).toList();
        List<SolveRow> recentSolves = note.userId() == null
                ? List.of()
                : learningQueryRepository.findRecentSolvesOfProblems(note.userId(), problemIds, RECENT_SOLVE_LIMIT);

        model.addAttribute("note", note);
        model.addAttribute("problems", problems);
        model.addAttribute("recentSolves", recentSolves);

        return "admin-practice-note-detail";
    }

    /**
     * problem_solve 기반 복습 기록.
     *
     * <p>예전에는 mission_log 의 NOTE_PRACTICE 를 셌는데, 그 행은 복습노트를 처음 완료할 때 한 번만
     * 쌓여서 실제 복습 횟수보다 훨씬 적게 보였다. 사용자가 문제를 다시 풀 때마다 남기는 기록은
     * problem_solve 다.
     */
    @GetMapping("/practice-logs")
    public String getPracticeLogs(
            @RequestParam(defaultValue = "0", name = "page") int page,
            @RequestParam(defaultValue = "20", name = "size") int size,
            @RequestParam(name = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(name = "userId", required = false) Long userId,
            @RequestParam(name = "answerStatus", required = false) String answerStatus,
            HttpServletRequest request,
            Model model
    ) {
        int selectedSize = Math.max(size, 1);
        String selectedStatus = normalizeAnswerStatus(answerStatus);
        Filter filter = new Filter(date, userId, selectedStatus);

        long total = learningQueryRepository.countSolves(filter);
        int totalPages = (int) Math.ceil((double) total / selectedSize);
        int selectedPage = clampPage(page, totalPages);

        model.addAttribute("solves", learningQueryRepository.findSolves(filter, selectedPage * selectedSize, selectedSize));
        model.addAttribute("totalSolves", total);
        model.addAttribute("currentPage", selectedPage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("size", selectedSize);
        model.addAttribute("pager", AdminPager.of(request, "page", selectedPage, selectedSize, total));
        model.addAttribute("summary", learningQueryRepository.summarizeSolves(filter));
        model.addAttribute("todaySolveCount", learningQueryRepository.countSolvesOn(LocalDate.now(KST)));
        model.addAttribute("answerStatuses", AnswerStatus.values());
        addFilterAttributes(model, date, userId, selectedStatus);

        return "practice-logs";
    }

    private void addFilterAttributes(Model model, LocalDate date, Long userId, String status) {
        model.addAttribute("filterDate", date);
        model.addAttribute("filterUserId", userId);
        model.addAttribute("filterUserName", userId == null ? null : learningQueryRepository.findUserName(userId));
        model.addAttribute("filterStatus", status);
        model.addAttribute("filtered", date != null || userId != null || status != null);
    }

    /** 범위를 넘은 page 로 빈 화면을 보여 주면 데이터가 사라진 줄 안다. 마지막 페이지로 되돌린다. */
    private int clampPage(int page, int totalPages) {
        int selected = Math.max(page, 0);
        return selected >= totalPages ? Math.max(totalPages - 1, 0) : selected;
    }

    private String normalizeAnswerStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return Arrays.stream(AnswerStatus.values())
                .map(Enum::name)
                .filter(name -> name.equalsIgnoreCase(status.trim()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 알림 요일은 JPA 가 List 를 직렬화해 넣은 varbinary 라 SQL 로는 읽을 수 없다.
     * 화면에 보이는 노트만 엔티티로 한 번에 읽어 요일을 붙인다.
     */
    private List<NoteRow> withWeekDays(List<NoteRow> notes) {
        if (notes.isEmpty()) {
            return notes;
        }
        Map<Long, PracticeNote> entities = practiceNoteRepository
                .findAllById(notes.stream().map(NoteRow::noteId).toList())
                .stream()
                .collect(Collectors.toMap(PracticeNote::getId, Function.identity()));

        return notes.stream()
                .map(note -> {
                    PracticeNote entity = entities.get(note.noteId());
                    PracticeNotification notification = entity == null ? null : entity.getPracticeNotification();
                    return note.withWeekDays(notification == null ? null : notification.getWeekDays());
                })
                .toList();
    }
}
