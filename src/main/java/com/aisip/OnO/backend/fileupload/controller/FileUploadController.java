package com.aisip.OnO.backend.fileupload.controller;

import com.aisip.OnO.backend.common.response.CommonResponse;
import com.aisip.OnO.backend.fileupload.service.FileUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/fileUpload")
public class FileUploadController {

    private final FileUploadService fileUploadService;

    @PostMapping("/image")
    public CommonResponse<Map<String, Object>> uploadImageFile(
            Authentication authentication,
            @RequestParam("image") MultipartFile file
    ) {
        //Long userId = (Long) authentication.getPrincipal();
        String imageUrl = fileUploadService.uploadFileToS3(file);

        return CommonResponse.success(Map.of("imageUrl", imageUrl));
    }

    @DeleteMapping("/image")
    public CommonResponse<String> deleteImageFile(
            Authentication authentication,
            @RequestParam("imageUrl") String imageUrl
    ) {
        //Long userId = (Long) authentication.getPrincipal();
        fileUploadService.deleteImageFileFromS3(imageUrl);

        return CommonResponse.success("이미지 삭제가 완료되었습니다.");
    }
}
