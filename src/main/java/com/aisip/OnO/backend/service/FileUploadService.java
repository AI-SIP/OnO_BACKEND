package com.aisip.OnO.backend.service;

import com.aisip.OnO.backend.Dto.Process.ImageProcessRegisterDto;
import com.aisip.OnO.backend.entity.Image.ImageData;
import com.aisip.OnO.backend.entity.Image.ImageType;
import com.aisip.OnO.backend.entity.Problem.Problem;
import com.aisip.OnO.backend.exception.ProblemRegisterException;
import com.aisip.OnO.backend.repository.ImageDataRepository;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.sentry.Sentry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileUploadService {
    private final AmazonS3Client amazonS3Client;

    private final ImageDataRepository imageDataRepository;
    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    @Value("${external.api.fastApiUrl}")
    private String fastApiUrl;

    public String uploadFileToS3(MultipartFile file, Problem problem, ImageType imageType) {
        String fileName = createFileName(file, problem, imageType);
        String fileUrl = getFileUrl(fileName);

        /*
        ObjectMetadata objectMetadata = new ObjectMetadata();
        objectMetadata.setContentType(file.getContentType());
        objectMetadata.setContentLength(file.getSize());

        amazonS3Client.putObject(bucket, fileName, file.getInputStream(), objectMetadata);

         */

        if(imageType != ImageType.SOLVE_IMAGE){
            saveImageData(fileUrl, problem, imageType);
        }

        log.info("file url : " + fileUrl + " has upload to S3");
        return fileUrl;
    }

    public Long getImageTypeCount(ImageType imageType){
        return imageDataRepository.countAllByImageType(imageType);
    }

    public void saveImageData(String imageUrl, Problem problem, ImageType imageType) {

        if(imageDataRepository.findByImageUrl(imageUrl).isEmpty()){
            ImageData imageData = ImageData.builder()
                    .imageUrl(imageUrl)
                    .problem(problem)
                    .imageType(imageType)
                    .build();

            imageDataRepository.save(imageData);
        }
    }

    public String getProcessImage(ImageProcessRegisterDto imageProcessRegisterDto) {
        RestTemplate restTemplate = new RestTemplate();
        String url = fastApiUrl;

        if(imageProcessRegisterDto.getColorsList() != null){
            log.info("intensity: " + imageProcessRegisterDto.getIntensity());
            log.info("remove colors on problemImage by colors: " + imageProcessRegisterDto.getColorsList());
            url += "/process-color";
        } else if (imageProcessRegisterDto.getPoints() != null){
            if(imageProcessRegisterDto.getPoints().isEmpty()){
                imageProcessRegisterDto.setPoints(List.of(List.of(0.0, 0.0, 0.0, 0.0)));
            }
            log.info("point list: " + imageProcessRegisterDto.getPoints().toString());
            url += "/process-shape";
        } else{
            url += "/process-color";
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<ImageProcessRegisterDto> request = new HttpEntity<>(imageProcessRegisterDto, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            log.info("Response from fastApi server: " + response);

            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(response.getBody());
            JsonNode pathNode = rootNode.path("path");
            String inputPath = pathNode.path("output_path").asText();

            String fileUrl = "https://" + bucket + ".s3.ap-northeast-2.amazonaws.com/" + inputPath;

            log.info("process image url : " + fileUrl + " has successfully processed");
            return fileUrl;
        } catch (Exception e) {
            log.info(e.getMessage());
            Sentry.captureException(e);
            throw new ProblemRegisterException();
        }
    }

    public String saveAndGetProcessImageUrl(ImageProcessRegisterDto imageProcessRegisterDto, Problem problem, ImageType imageType) {
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
            Sentry.captureException(e);
            throw new ProblemRegisterException();
        }
    }

    public String getProblemAnalysis(String problemImageUrl){
        try{
            RestTemplate restTemplate = new RestTemplate();
            String url = UriComponentsBuilder.fromHttpUrl(fastApiUrl + "/analysis/whole")
                    .queryParam("problem_url", problemImageUrl)
                    .toUriString();

            ResponseEntity<Map<String, Object>> responseEntity = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {
                    }
            );

            // JSON 응답에서 필요한 값 추출
            if (responseEntity.getStatusCode().is2xxSuccessful()) {
                Map<String, Object> response = responseEntity.getBody();

                // 응답 본문에서 필요한 값 추출
                if (response != null) {
                    return (String) response.get("answer");
                } else {
                    throw new RuntimeException("응답 데이터가 비어있습니다.");
                }
            } else {
                throw new RuntimeException("요청이 실패했습니다. 상태 코드: " + responseEntity.getStatusCode());
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("문제 분석 요청 중 오류가 발생했습니다.", e);
        }
    }

    public String updateImage(MultipartFile file, Problem problem, ImageType imageType) {
        try {
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
        } catch (IOException e) {
            throw new ProblemRegisterException();
        }
    }

    @Transactional(readOnly = true)
    public List<ImageData> getProblemImages(Long problemId) {
        return imageDataRepository.findByProblemId(problemId);
    }

    public void deleteImage(ImageData imageData) {

        String fileUrl = imageData.getImageUrl();
        String splitStr = ".com/";
        String fileName = fileUrl.substring(fileUrl.lastIndexOf(splitStr) + splitStr.length());

        /*
        amazonS3Client.deleteObject(new DeleteObjectRequest(bucket, fileName));

        if (imageData.getImageType() == ImageType.PROCESS_IMAGE) {
            String suffix = ".output.png";
            String originalFileName = fileName.substring(0, fileName.length() - suffix.length());

            amazonS3Client.deleteObject(new DeleteObjectRequest(bucket, originalFileName + ".input.png"));
            amazonS3Client.deleteObject(new DeleteObjectRequest(bucket, originalFileName + ".mask.png"));
        }

         */

        log.info("imageData: " + imageData.getImageUrl() + " has successfully deleted");
        imageDataRepository.deleteById(imageData.getId());
    }

    private String createFileName(MultipartFile file, Problem problem, ImageType imageType) {
        return problem.getCreatedAt() + "/" + imageType.getDescription() + "_" + file.getOriginalFilename();
    }

    private String getFileUrl(String fileName) {
        return "https://" + bucket + ".s3.ap-northeast-2.amazonaws.com/" + fileName;
    }
}
