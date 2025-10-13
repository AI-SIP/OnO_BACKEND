package com.aisip.OnO.backend.util.fileupload.service;

import com.amazonaws.services.s3.AmazonS3Client;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileUploadService {
    private final AmazonS3Client amazonS3Client;
    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    public String uploadFileToS3(MultipartFile file) {

        /*
        String fileName = createFileName(file);
        String fileUrl = getFileUrl(fileName);

        ObjectMetadata objectMetadata = new ObjectMetadata();
        objectMetadata.setContentType(file.getContentType());
        objectMetadata.setContentLength(file.getSize());

        try {
            amazonS3Client.putObject(bucket, fileName, file.getInputStream(), objectMetadata);
        } catch (IOException e) {
            throw new ApplicationException(FileUploadErrorCase.FILE_UPLOAD_FAILED);
        }

        log.info("file url : " + fileUrl + " has upload to S3");
        return fileUrl;
         */

        return file.getOriginalFilename();
    }

    public void deleteImageFileFromS3(String imageUrl) {
        /*
        String splitStr = ".com/";
        String fileName = imageUrl.substring(imageUrl.lastIndexOf(splitStr) + splitStr.length());

        log.info("file url : " + imageUrl + " has removed from S3");

        amazonS3Client.deleteObject(new DeleteObjectRequest(bucket, fileName));

         */
    }

    private String createFileName(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        String extension = originalFilename.substring(originalFilename.lastIndexOf(".")); // 확장자 추출

        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd")); // 날짜 기반 폴더
        String randomFileName = UUID.randomUUID().toString(); // 랜덤 UUID

        // 예: image/YYYY/MM/DD/랜덤값.png
        return "image/" + datePath + "/" + randomFileName + extension;
    }


    private String getFileUrl(String fileName) {
        return "https://" + bucket + ".s3.ap-northeast-2.amazonaws.com/" + fileName;
    }
}
