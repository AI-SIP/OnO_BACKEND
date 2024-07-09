package com.aisip.OnO.backend.service;

import com.amazonaws.services.s3.AmazonS3Client;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface FileUploadService {
    String uploadFileToS3(MultipartFile file) throws IOException;
}
