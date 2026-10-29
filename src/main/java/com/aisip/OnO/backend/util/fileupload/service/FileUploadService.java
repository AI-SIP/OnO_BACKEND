package com.aisip.OnO.backend.util.fileupload.service;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.problem.entity.ProblemImageData;
import com.aisip.OnO.backend.problem.exception.ProblemErrorCase;
import com.aisip.OnO.backend.problem.repository.ProblemImageDataRepository;
import com.aisip.OnO.backend.problemsolve.entity.ProblemSolveImageData;
import com.aisip.OnO.backend.problemsolve.exception.ProblemSolveErrorCase;
import com.aisip.OnO.backend.problemsolve.repository.ProblemSolveImageDataRepository;
import com.aisip.OnO.backend.util.fileupload.dto.PresignedUrlResponse;
import com.aisip.OnO.backend.util.fileupload.exception.FileUploadErrorCase;
import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.DeleteObjectRequest;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
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
    private final ImageUrlOwnershipService imageUrlOwnershipService;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    public String uploadFileToS3(MultipartFile file, Long userId) {
        Timer.Sample sample = Timer.start(meterRegistry);

        String fileName = createFileName(file, userId);
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

    public List<PresignedUrlResponse> generatePresignedUrls(String contentType, int count, Long userId) {
        Date expiration = new Date(System.currentTimeMillis() + 10 * 60 * 1000L); // 10분

        return IntStream.range(0, count)
                .mapToObj(i -> {
                    String fileName = createFileNameForPresigned(contentType, userId);
                    GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucket, fileName)
                            .withMethod(HttpMethod.PUT)
                            .withContentType(contentType)
                            .withExpiration(expiration);
                    String presignedUrl = amazonS3Client.generatePresignedUrl(request).toString();
                    return new PresignedUrlResponse(presignedUrl, getFileUrl(fileName));
                })
                .toList();
    }

    /**
     * 우리 버킷 URL 의 파일만 지운다. 다른 호스트 URL 은 key 를 뽑지 않고 넘어간다.
     * 예전에는 URL 의 마지막 ".com/" 뒤를 그대로 key 로 써서 호스트와 상관없이 우리 버킷의 key 를 지웠다.
     */
    public void deleteImageFileFromS3(String imageUrl) {
        Optional<String> objectKey = imageUrlOwnershipService.extractObjectKey(imageUrl);
        if (objectKey.isEmpty()) {
            log.warn("S3 delete skipped - reason: not_bucket_url");
            return;
        }

        Timer.Sample sample = Timer.start(meterRegistry);
        String fileName = objectKey.get();

        log.info("S3 delete requested");

        try {
            amazonS3Client.deleteObject(new DeleteObjectRequest(bucket, fileName));
            recordExternalCall("s3", "delete", "success", sample);
        } catch (RuntimeException e) {
            recordExternalCall("s3", "delete", "failure", sample);
            throw e;
        }
    }

    /**
     * 이 사용자의 행만 지운다. 같은 URL 을 가진 다른 사용자의 행은 남기고,
     * 다른 사용자의 행이 그 파일을 쓰고 있으면 S3 파일도 남긴다.
     */
    @Transactional
    public void deleteUserImageFile(String imageUrl, Long userId) {
        List<ProblemImageData> problemImages = problemImageDataRepository.findAllByImageUrlAndUserId(imageUrl, userId);
        if (!problemImages.isEmpty()) {
            deleteFromS3IfDeletable(imageUrl, userId);
            problemImageDataRepository.deleteAll(problemImages);
            return;
        }
        if (problemImageDataRepository.existsByImageUrl(imageUrl)) {
            throw new ApplicationException(ProblemErrorCase.PROBLEM_USER_UNMATCHED);
        }

        List<ProblemSolveImageData> solveImages = problemSolveImageDataRepository.findAllByImageUrlAndUserId(imageUrl, userId);
        if (!solveImages.isEmpty()) {
            deleteFromS3IfDeletable(imageUrl, userId);
            problemSolveImageDataRepository.deleteAll(solveImages);
            return;
        }
        if (problemSolveImageDataRepository.existsByImageUrl(imageUrl)) {
            throw new ApplicationException(ProblemSolveErrorCase.PROBLEM_SOLVE_USER_UNMATCHED);
        }

        throw new ApplicationException(FileUploadErrorCase.FILE_NOT_FOUND);
    }

    private void deleteFromS3IfDeletable(String imageUrl, Long userId) {
        if (imageUrlOwnershipService.isDeletable(imageUrl, userId)) {
            deleteImageFileFromS3(imageUrl);
        }
    }

    private String createFileName(MultipartFile file, Long userId) {
        String extension = resolveExtension(file.getOriginalFilename());

        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        return imageUrlOwnershipService.userKeyPrefix(userId) + datePath + "/" + UUID.randomUUID() + extension;
    }

    /**
     * 확장자가 없는 파일명이 들어오면 예전에는 substring(-1) 로 StringIndexOutOfBoundsException 이 나
     * 그대로 500 이 됐다. 잘못된 업로드 요청은 400 으로 돌려준다.
     */
    private String resolveExtension(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new ApplicationException(FileUploadErrorCase.INVALID_IMAGE_FILE);
        }

        int separatorIndex = originalFilename.lastIndexOf(".");
        if (separatorIndex < 0 || separatorIndex == originalFilename.length() - 1) {
            throw new ApplicationException(FileUploadErrorCase.INVALID_IMAGE_FILE);
        }

        return originalFilename.substring(separatorIndex);
    }

    private String createFileNameForPresigned(String contentType, Long userId) {
        String extension = switch (contentType) {
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            case "image/heic", "image/heif" -> ".heic";
            default -> ".jpg"; // image/jpeg, image/jpg, application/octet-stream 등
        };
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        return imageUrlOwnershipService.userKeyPrefix(userId) + datePath + "/" + UUID.randomUUID() + extension;
    }


    private String getFileUrl(String fileName) {
        return imageUrlOwnershipService.urlPrefix() + fileName;
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
