package com.aisip.OnO.backend.service;

import com.aisip.OnO.backend.Dto.Process.ImageProcessRegisterDto;
import com.aisip.OnO.backend.entity.Image.ImageData;
import com.aisip.OnO.backend.entity.Image.ImageType;
import com.aisip.OnO.backend.entity.Problem;
import com.aisip.OnO.backend.repository.ImageDataRepository;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.DeleteObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileUploadServiceImpl implements FileUploadService {
    private final AmazonS3Client amazonS3Client;

    private final ImageDataRepository imageDataRepository;
    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    @Value("${external.api.fastApiUrl}")
    private String fastApiUrl;

    @Override
    public String uploadFileToS3(MultipartFile file, Problem problem, ImageType imageType) throws IOException {
        String fileName = createFileName(file, problem, imageType);
        String fileUrl = getFileUrl(fileName);

        ObjectMetadata objectMetadata = new ObjectMetadata();
        objectMetadata.setContentType(file.getContentType());
        objectMetadata.setContentLength(file.getSize());

        amazonS3Client.putObject(bucket, fileName, file.getInputStream(), objectMetadata);
        saveImageData(fileUrl, problem, imageType);

        log.info("file url : " + fileUrl + " has upload to S3");
        return fileUrl;
    }


    private void saveImageData(String imageUrl, Problem problem, ImageType imageType) {

        ImageData imageData = ImageData.builder()
                .imageUrl(imageUrl)
                .problem(problem)
                .imageType(imageType)
                .build();

        imageDataRepository.save(imageData);
    }

    @Override
    public String saveProcessImageUrl(ImageProcessRegisterDto imageProcessRegisterDto, Problem problem, ImageType imageType) {
        RestTemplate restTemplate = new RestTemplate();
        String url = fastApiUrl + "/process-color";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<ImageProcessRegisterDto> request = new HttpEntity<>(imageProcessRegisterDto, headers);

        log.info("remove colors on problemImage by colors: " + imageProcessRegisterDto.getColorsList());

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            log.info("Response from fastApi server: " + response);

            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(response.getBody());
            JsonNode pathNode = rootNode.path("path");
            String inputPath = pathNode.path("output_path").asText();

            String fileUrl = "https://" + bucket + ".s3.ap-northeast-2.amazonaws.com/" + inputPath;
            saveImageData(fileUrl, problem, imageType);

            log.info("process image url : " + fileUrl + " has successfully processed");
            return fileUrl;
        } catch (Exception e) {
            log.info(e.getMessage());
            throw new RuntimeException("Failed to parse response", e);
        }
    }

    @Override
    public String updateImage(MultipartFile file, Problem problem, ImageType imageType) throws IOException {

        Optional<ImageData> beforeImageData = imageDataRepository.findByProblemIdAndImageType(problem.getId(), imageType);
        beforeImageData.ifPresent(this::deleteImage);

        if (imageType.equals(ImageType.PROBLEM_IMAGE)) {
            Optional<ImageData> processImageData = imageDataRepository.findByProblemIdAndImageType(problem.getId(), ImageType.PROCESS_IMAGE);
            processImageData.ifPresent(this::deleteImage);
        }

        String fileName = createFileName(file, problem, imageType);
        String fileUrl = getFileUrl(fileName);

        ObjectMetadata objectMetadata = new ObjectMetadata();
        objectMetadata.setContentType(file.getContentType());
        objectMetadata.setContentLength(file.getSize());

        amazonS3Client.putObject(bucket, fileName, file.getInputStream(), objectMetadata);
        saveImageData(fileUrl, problem, imageType);

        log.info("file url : " + fileUrl + " successfully updated");
        return fileUrl;
    }

    @Transactional(readOnly = true)
    public List<ImageData> getProblemImages(Long problemId) {
        return imageDataRepository.findByProblemId(problemId);
    }

    public void deleteImage(ImageData imageData) {

        String fileUrl = imageData.getImageUrl();
        String splitStr = ".com/";
        String fileName = fileUrl.substring(fileUrl.lastIndexOf(splitStr) + splitStr.length());
        amazonS3Client.deleteObject(new DeleteObjectRequest(bucket, fileName));

        if (imageData.getImageType() == ImageType.PROCESS_IMAGE) {
            String suffix = ".output.png";
            String originalFileName = fileName.substring(0, fileName.length() - suffix.length());

            amazonS3Client.deleteObject(new DeleteObjectRequest(bucket, originalFileName + ".input.png"));
            amazonS3Client.deleteObject(new DeleteObjectRequest(bucket, originalFileName + ".mask.png"));
        }

        log.info("imageData: " + imageData.getImageUrl() + " has successfully deleted");
        imageDataRepository.deleteById(imageData.getId());
    }

    private String createFileName(MultipartFile file, Problem problem, ImageType imageType) {
        return problem.getCreatedAt() + "/" + imageType.getDescription() + "_" + file.getOriginalFilename();
    }

    private String getFileUrl(String fileName){
        return "https://" + bucket + ".s3.ap-northeast-2.amazonaws.com/" + fileName;
    }

    private String getFileExtension(String fileName) {
        try {
            return fileName.substring(fileName.lastIndexOf("."));
        } catch (StringIndexOutOfBoundsException e) {
            throw new IllegalArgumentException(String.format("잘못된 형식의 파일 (%s) 입니다.", fileName));
        }
    }
}
