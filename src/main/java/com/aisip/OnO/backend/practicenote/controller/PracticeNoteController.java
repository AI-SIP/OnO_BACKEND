package com.aisip.OnO.backend.practicenote.controller;

import com.aisip.OnO.backend.common.response.CommonResponse;
import com.aisip.OnO.backend.practicenote.dto.PracticeNoteRegisterDto;
import com.aisip.OnO.backend.practicenote.dto.PracticeNoteDetailResponseDto;
import com.aisip.OnO.backend.practicenote.dto.PracticeNoteThumbnailResponseDto;
import com.aisip.OnO.backend.practicenote.dto.PracticeNoteUpdateDto;
import com.aisip.OnO.backend.practicenote.service.PracticeNoteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/practiceNotes")
public class PracticeNoteController {

    private final PracticeNoteService practiceNoteService;

    // ✅ 특정 복습 리스트 조회
    @GetMapping("/{practiceId}")
    public CommonResponse<PracticeNoteDetailResponseDto> getPracticeDetail(@PathVariable Long practiceId) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        log.info("userId: {} get problem practice for practice id: {}", userId, practiceId);

        return CommonResponse.success(practiceNoteService.findPracticeNoteDetail(practiceId));
    }

    // ✅ 사용자의 모든 복습 리스트 썸네일 조회
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/thumbnail")
    public CommonResponse<List<PracticeNoteThumbnailResponseDto>> getAllPracticeThumbnail() {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        log.info("userId: {} get all problem practice thumbnails", userId);

        return CommonResponse.success(practiceNoteService.findAllPracticeThumbnailsByUser(userId));
    }

    // ✅ 복습 리스트 등록
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("")
    public CommonResponse<String> registerPractice(@RequestBody PracticeNoteRegisterDto practiceNoteRegisterDto) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        practiceNoteService.registerPractice(practiceNoteRegisterDto, userId);
        log.info("userId: {} registered problem practice", userId);

        return CommonResponse.success("복습 노트가 성공적으로 생성되었습니다.");
    }

    // ✅ 복습 완료 횟수 증가
    @PatchMapping("/{practiceId}/complete")
    public CommonResponse<String> addPracticeCount(@PathVariable Long practiceId) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        practiceNoteService.addPracticeNoteCount(practiceId);

        log.info("userId: {} completed problem practice with practiceNoteId: {}", userId, practiceId);
        return CommonResponse.success("복습을 완료했습니다.");
    }

    // ✅ 복습 리스트 수정
    @PatchMapping("")
    public CommonResponse<String> updatePractice(@RequestBody PracticeNoteUpdateDto practiceNoteUpdateDto) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        practiceNoteService.updatePracticeInfo(practiceNoteUpdateDto);

        return CommonResponse.success("복습 리스트가 성공적으로 수정되었습니다.");
    }

    // ✅ 복습 리스트 삭제 (204 No Content 반환)
    @DeleteMapping("")
    public CommonResponse<String> deletePractices(@RequestParam List<Long> deletePracticeIds) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        practiceNoteService.deletePractices(deletePracticeIds);

        return CommonResponse.success("선택한 복습 노트가 삭제되었습니다.");
    }

    @DeleteMapping("/all")
    public CommonResponse<String> deleteAllUserPractices() {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        practiceNoteService.deleteAllPracticesByUser(userId);

        return CommonResponse.success("유저의 모든 복습 노트가 삭제되었습니다.");
    }
}
