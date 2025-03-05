package com.aisip.OnO.backend.practicenote.controller;

import com.aisip.OnO.backend.practicenote.dto.PracticeNoteRegisterDto;
import com.aisip.OnO.backend.practicenote.dto.PracticeNoteResponseDto;
import com.aisip.OnO.backend.practicenote.service.PracticeNoteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/problem/practice")
public class PracticeNoteController {

    private final PracticeNoteService practiceNoteService;

    // ✅ 특정 복습 리스트 조회
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/{practiceId}")
    public PracticeNoteResponseDto getPracticeDetail(Authentication authentication, @PathVariable Long practiceId) {
        Long userId = (Long) authentication.getPrincipal();
        log.info("userId: {} get problem practice for practice id: {}", userId, practiceId);
        return practiceNoteService.findPractice(practiceId);
    }

    // ✅ 사용자의 모든 복습 리스트 썸네일 조회
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/thumbnail/all")
    public List<PracticeNoteResponseDto> getAllPracticeThumbnail(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        log.info("userId: {} get all problem practice thumbnails", userId);
        return practiceNoteService.findAllPracticesByUser(userId);
    }

    // ✅ 사용자의 모든 복습 리스트 상세 조회
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/all")
    public List<PracticeNoteResponseDto> getAllPracticeDetail(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        log.info("userId: {} get all problem practices", userId);
        return practiceNoteService.findAllPracticesByUser(userId);
    }

    // ✅ 복습 리스트 등록
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("")
    public PracticeNoteResponseDto registerPractice(Authentication authentication, @RequestBody PracticeNoteRegisterDto practiceNoteRegisterDto) {
        Long userId = (Long) authentication.getPrincipal();
        PracticeNoteResponseDto response = practiceNoteService.createPractice(userId, practiceNoteRegisterDto);
        log.info("userId: {} registered problem practice", userId);
        return response;
    }

    // ✅ 복습 완료 횟수 증가
    @ResponseStatus(HttpStatus.OK)
    @PatchMapping("/complete/{practiceId}")
    public String addPracticeCount(Authentication authentication, @PathVariable Long practiceId) {
        Long userId = (Long) authentication.getPrincipal();
        practiceNoteService.addPracticeCount(practiceId);

        log.info("userId: {} completed problem practice with practiceId: {}", userId, practiceId);
        return "복습을 완료했습니다.";
    }

    // ✅ 복습 리스트 수정
    @ResponseStatus(HttpStatus.OK)
    @PatchMapping("")
    public String updatePractice(Authentication authentication, @RequestBody PracticeNoteRegisterDto practiceNoteRegisterDto) {
        Long userId = (Long) authentication.getPrincipal();
        practiceNoteService.updatePractice(practiceNoteRegisterDto);

        log.info("userId: {} updated problem practice", userId);
        return "복습 리스트 수정을 완료했습니다.";
    }

    // ✅ 복습 리스트 삭제 (204 No Content 반환)
    @ResponseStatus(HttpStatus.OK)
    @DeleteMapping("")
    public void deletePractices(Authentication authentication, @RequestParam List<Long> deletePracticeIds) {
        Long userId = (Long) authentication.getPrincipal();
        practiceNoteService.deletePractices(deletePracticeIds);

        log.info("userId: {} deleted problem practice ids: {}", userId, deletePracticeIds);
    }
}
