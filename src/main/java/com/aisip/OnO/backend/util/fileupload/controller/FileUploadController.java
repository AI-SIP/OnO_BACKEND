package com.aisip.OnO.backend.util.fileupload.controller;

import com.aisip.OnO.backend.common.response.CommonResponse;
import com.aisip.OnO.backend.util.fileupload.dto.PresignedUrlResponse;
import com.aisip.OnO.backend.util.fileupload.service.FileUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/fileUpload")
public class FileUploadController {

    private final FileUploadService fileUploadService;

    @PostMapping("/image")
    public CommonResponse<String> uploadImageFile(
            @RequestParam("image") MultipartFile file
    ) {
        String imageUrl = fileUploadService.uploadFileToS3(file);

        return CommonResponse.success(imageUrl);
    }

    @PostMapping("/images")
    public CommonResponse<List<String>> uploadMultipleImageFiles(
            @RequestParam("images") List<MultipartFile> files
    ) {
        List<String> imageUrls = files.stream()
                .map(fileUploadService::uploadFileToS3)
                .toList();

        return CommonResponse.success(imageUrls);
    }

    // 앱이 S3에 직접 업로드할 수 있는 Presigned URL 발급
    // count: 발급할 URL 수 (최대 20)
    // contentType: 업로드할 이미지 MIME 타입 (예: image/jpeg)
    @GetMapping("/presigned-urls")
    public CommonResponse<List<PresignedUrlResponse>> getPresignedUrls(
            @RequestParam(defaultValue = "1") int count,
            @RequestParam(defaultValue = "image/jpeg") String contentType
    ) {
        int safeCount = Math.min(count, 20);
        return CommonResponse.success(fileUploadService.generatePresignedUrls(contentType, safeCount));
    }

    @DeleteMapping("/image")
    public CommonResponse<String> deleteImageFile(
            @RequestParam("imageUrl") String imageUrl
    ) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        fileUploadService.deleteUserImageFile(imageUrl, userId);

        return CommonResponse.success("이미지 삭제가 완료되었습니다.");
    }
}
