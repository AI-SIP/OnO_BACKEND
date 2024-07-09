package com.aisip.OnO.backend.service;

import com.aisip.OnO.backend.entity.Image.ImageData;
import com.aisip.OnO.backend.entity.Image.ImageType;
import com.aisip.OnO.backend.entity.Problem;
import com.amazonaws.services.s3.AmazonS3Client;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface FileUploadService {
    String uploadFileToS3(MultipartFile file) throws IOException;
    ImageData saveImageData(String imageUrl, Problem problem, ImageType imageType);

    List<ImageData> getProblemImages(Long problemId);
}
