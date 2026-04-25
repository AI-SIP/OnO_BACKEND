package com.aisip.OnO.backend.admin.controller;

import com.aisip.OnO.backend.admin.dto.AdminPracticeLogResponseDto;
import com.aisip.OnO.backend.admin.dto.AdminPracticeNoteResponseDto;
import com.aisip.OnO.backend.mission.service.MissionLogService;
import com.aisip.OnO.backend.practicenote.service.PracticeNoteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Slf4j
@RequiredArgsConstructor
@Controller
@RequestMapping("/admin")
public class AdminPracticeNoteController {

    private final PracticeNoteService practiceNoteService;
    private final MissionLogService missionLogService;

    @GetMapping("/practice-notes")
    public String getPracticeNotes(
            @RequestParam(defaultValue = "0", name = "notePage") int notePage,
            @RequestParam(defaultValue = "0", name = "logPage") int logPage,
            @RequestParam(defaultValue = "20", name = "size") int size,
            Model model
    ) {
        int selectedNotePage = Math.max(notePage, 0);
        int selectedLogPage = Math.max(logPage, 0);
        int selectedSize = Math.max(size, 1);

        Page<AdminPracticeNoteResponseDto> practiceNotes = practiceNoteService.findAdminPracticeNotes(selectedNotePage, selectedSize);
        Page<AdminPracticeLogResponseDto> practiceLogs = missionLogService.findAdminPracticeLogs(selectedLogPage, selectedSize);
        int noteTotalPages = practiceNotes.getTotalPages();
        int logTotalPages = practiceLogs.getTotalPages();
        int notePageBlockStart = (selectedNotePage / 10) * 10;
        int logPageBlockStart = (selectedLogPage / 10) * 10;
        int notePageBlockEnd = Math.min(notePageBlockStart + 9, Math.max(noteTotalPages - 1, 0));
        int logPageBlockEnd = Math.min(logPageBlockStart + 9, Math.max(logTotalPages - 1, 0));

        model.addAttribute("practiceNotes", practiceNotes.getContent());
        model.addAttribute("practiceLogs", practiceLogs.getContent());
        model.addAttribute("notePage", selectedNotePage);
        model.addAttribute("logPage", selectedLogPage);
        model.addAttribute("noteTotalPages", noteTotalPages);
        model.addAttribute("logTotalPages", logTotalPages);
        model.addAttribute("notePageBlockStart", notePageBlockStart);
        model.addAttribute("notePageBlockEnd", notePageBlockEnd);
        model.addAttribute("logPageBlockStart", logPageBlockStart);
        model.addAttribute("logPageBlockEnd", logPageBlockEnd);
        model.addAttribute("hasPreviousNoteBlock", notePageBlockStart > 0);
        model.addAttribute("hasNextNoteBlock", notePageBlockEnd < noteTotalPages - 1);
        model.addAttribute("hasPreviousLogBlock", logPageBlockStart > 0);
        model.addAttribute("hasNextLogBlock", logPageBlockEnd < logTotalPages - 1);
        model.addAttribute("totalPracticeNotes", practiceNotes.getTotalElements());
        model.addAttribute("totalPracticeLogs", practiceLogs.getTotalElements());
        model.addAttribute("size", selectedSize);

        return "practice-notes";
    }
}
