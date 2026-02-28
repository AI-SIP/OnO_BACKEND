package com.aisip.OnO.backend.util.fileupload.service;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.util.fileupload.exception.FileUploadErrorCase;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.DeleteObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.io.IOException;
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
    private final MeterRegistry meterRegistry;
    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    public String uploadFileToS3(MultipartFile file) {
        Timer.Sample sample = Timer.start(meterRegistry);

        String fileName = createFileName(file);
        String fileUrl = getFileUrl(fileName);

        ObjectMetadata objectMetadata = new ObjectMetadata();
        objectMetadata.setContentType(file.getContentType());
        objectMetadata.setContentLength(file.getSize());

        try {
            amazonS3Client.putObject(bucket, fileName, file.getInputStream(), objectMetadata);
        } catch (IOException e) {
            recordExternalCall("s3", "upload", "failure", sample);
            throw new ApplicationException(FileUploadErrorCase.FILE_UPLOAD_FAILED);
        }

        recordExternalCall("s3", "upload", "success", sample);
        log.info("file url : " + fileUrl + " has upload to S3");
        return fileUrl;
    }

    public void deleteImageFileFromS3(String imageUrl) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String splitStr = ".com/";
        String fileName = imageUrl.substring(imageUrl.lastIndexOf(splitStr) + splitStr.length());

        log.info("file url : " + imageUrl + " has removed from S3");

        try {
            amazonS3Client.deleteObject(new DeleteObjectRequest(bucket, fileName));
            recordExternalCall("s3", "delete", "success", sample);
        } catch (RuntimeException e) {
            recordExternalCall("s3", "delete", "failure", sample);
            throw e;
        }
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

    private void recordExternalCall(String dependency, String operation, String outcome, Timer.Sample sample) {
        sample.stop(
                Timer.builder("ono.external.requests")
                        .description("External dependency call latency")
                        .publishPercentileHistogram()
                        .tag("dependency", dependency)
                        .tag("operation", operation)
                        .tag("outcome", outcome)
                        .register(meterRegistry)
        );
    }
}
