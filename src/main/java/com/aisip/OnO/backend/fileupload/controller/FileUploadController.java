package com.aisip.OnO.backend.fileupload.controller;

import com.aisip.OnO.backend.common.response.CommonResponse;
import com.aisip.OnO.backend.fileupload.service.FileUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/fileUpload")
public class FileUploadController {

    //private final ProblemService problemService;

    private final FileUploadService fileUploadService;

    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/image")
    public CommonResponse<Map<String, Object>> uploadImageFile(
            Authentication authentication,
            @RequestParam("image") MultipartFile file
    ) {
        //Long userId = (Long) authentication.getPrincipal();
        //Problem problem = problemService.createProblem(userId);
        String imageUrl = fileUploadService.uploadFileToS3(file);

        return CommonResponse.success(Map.of("imageUrl", imageUrl));
    }

    /*
    // ✅ 이미지 분석 요청
    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/analysis")
    public Map<String, String> getProblemAnalysis(
            Authentication authentication,
            @RequestBody Map<String, String> requestBody
    ) {
        String problemImageUrl = requestBody.get("problemImageUrl");
        String analysisResult = fileUploadService.getProblemAnalysis(problemImageUrl);

        return Map.of("analysis", analysisResult);
    }

     */

    /*
    // ✅ 이미지 보정 요청
    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/processImage")
    public Map<String, String> getProcessImage(
            Authentication authentication,
            @RequestBody FileUploadRegisterDto fileUploadRegisterDto
    ) {
        String processImageUrl = fileUploadService.getProcessImage(fileUploadRegisterDto);

        return Map.of("processImageUrl", processImageUrl);
    }

     */
}
