package com.aisip.OnO.backend.util.fileupload.controller;

import com.aisip.OnO.backend.common.response.CommonResponse;
import com.aisip.OnO.backend.util.fileupload.service.FileUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
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
            Authentication authentication,
            @RequestParam("image") MultipartFile file
    ) {
        //Long userId = (Long) authentication.getPrincipal();
        String imageUrl = fileUploadService.uploadFileToS3(file);

        return CommonResponse.success(imageUrl);
    }

    @PostMapping("/images")
    public CommonResponse<List<String>> uploadMultipleImageFiles(
            Authentication authentication,
            @RequestParam("images") List<MultipartFile> files
    ) {
        //Long userId = (Long) authentication.getPrincipal();

        List<String> imageUrls = files.stream()
                .map(fileUploadService::uploadFileToS3)
                .toList();

        return CommonResponse.success(imageUrls);
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
