package com.aisip.OnO.backend.service;

import com.aisip.OnO.backend.entity.Image.ImageData;
import com.aisip.OnO.backend.entity.Image.ImageType;
import com.aisip.OnO.backend.entity.Problem;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface FileUploadService {
    String uploadFileToS3(MultipartFile file, Problem problem, ImageType imageType) throws IOException;

    String saveProcessImageUrl(String problemImageUrl, Problem problem, ImageType imageType);

    String updateImage(MultipartFile file, Problem problem, ImageType imageType) throws IOException;

    List<ImageData> getProblemImages(Long problemId);

    void deleteImage(ImageData imageData);
}
