package com.aisip.OnO.backend.util.fileupload.service;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.problem.entity.ProblemImageData;
import com.aisip.OnO.backend.problem.exception.ProblemErrorCase;
import com.aisip.OnO.backend.problem.repository.ProblemImageDataRepository;
import com.aisip.OnO.backend.problemsolve.entity.ProblemSolveImageData;
import com.aisip.OnO.backend.problemsolve.exception.ProblemSolveErrorCase;
import com.aisip.OnO.backend.problemsolve.repository.ProblemSolveImageDataRepository;
import com.aisip.OnO.backend.util.fileupload.exception.FileUploadErrorCase;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.DeleteObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.io.IOException;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
    private final ProblemImageDataRepository problemImageDataRepository;
    private final ProblemSolveImageDataRepository problemSolveImageDataRepository;

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
        log.info("S3 upload completed - fileSize: {}, contentType: {}", file.getSize(), file.getContentType());
        return fileUrl;
    }

    public void deleteImageFileFromS3(String imageUrl) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String splitStr = ".com/";
        String fileName = imageUrl.substring(imageUrl.lastIndexOf(splitStr) + splitStr.length());

        log.info("S3 delete requested");

        try {
            amazonS3Client.deleteObject(new DeleteObjectRequest(bucket, fileName));
            recordExternalCall("s3", "delete", "success", sample);
        } catch (RuntimeException e) {
            recordExternalCall("s3", "delete", "failure", sample);
            throw e;
        }
    }

    @Transactional
    public void deleteUserImageFile(String imageUrl, Long userId) {
        problemImageDataRepository.findByImageUrl(imageUrl)
                .ifPresentOrElse(
                        imageData -> deleteProblemImageData(imageData, imageUrl, userId),
                        () -> deleteProblemSolveImageData(imageUrl, userId)
                );
    }

    private void deleteProblemImageData(ProblemImageData imageData, String imageUrl, Long userId) {
        if (!Objects.equals(imageData.getProblem().getUserId(), userId)) {
            throw new ApplicationException(ProblemErrorCase.PROBLEM_USER_UNMATCHED);
        }

        deleteImageFileFromS3(imageUrl);
        problemImageDataRepository.deleteByImageUrl(imageUrl);
    }

    private void deleteProblemSolveImageData(String imageUrl, Long userId) {
        ProblemSolveImageData imageData = problemSolveImageDataRepository.findByImageUrl(imageUrl)
                .orElseThrow(() -> new ApplicationException(FileUploadErrorCase.FILE_NOT_FOUND));

        if (!Objects.equals(imageData.getProblemSolve().getUserId(), userId)) {
            throw new ApplicationException(ProblemSolveErrorCase.PROBLEM_SOLVE_USER_UNMATCHED);
        }

        deleteImageFileFromS3(imageUrl);
        problemSolveImageDataRepository.deleteByImageUrl(imageUrl);
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
