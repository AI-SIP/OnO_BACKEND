package com.aisip.OnO.backend.fileupload.service;

import com.aisip.OnO.backend.fileupload.dto.FileUploadRegisterDto;
import com.aisip.OnO.backend.problem.entity.ProblemImageData;
import com.aisip.OnO.backend.problem.entity.ProblemImageType;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problem.exception.ProblemRegisterException;
import com.aisip.OnO.backend.problem.repository.ProblemImageRepository;
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

    private final ProblemImageRepository problemImageRepository;
    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    @Value("${external.api.fastApiUrl}")
    private String fastApiUrl;

    public String uploadFileToS3(MultipartFile file, Problem problem, ProblemImageType problemImageType) {
        String fileName = createFileName(file, problem, problemImageType);
        String fileUrl = getFileUrl(fileName);

        /*
        ObjectMetadata objectMetadata = new ObjectMetadata();
        objectMetadata.setContentType(file.getContentType());
        objectMetadata.setContentLength(file.getSize());

        amazonS3Client.putObject(bucket, fileName, file.getInputStream(), objectMetadata);

         */

        if(problemImageType != ProblemImageType.SOLVE_IMAGE){
            saveImageData(fileUrl, problem, problemImageType);
        }

        log.info("file url : " + fileUrl + " has upload to S3");
        return fileUrl;
    }

    public Long getImageTypeCount(ProblemImageType problemImageType){
        return problemImageRepository.countAllByImageType(problemImageType);
    }

    public void saveImageData(String imageUrl, Problem problem, ProblemImageType problemImageType) {

        if(problemImageRepository.findByImageUrl(imageUrl).isEmpty()){
            ProblemImageData problemImageData = ProblemImageData.builder()
                    .imageUrl(imageUrl)
                    .problem(problem)
                    .problemImageType(problemImageType)
                    .build();

            problemImageRepository.save(problemImageData);
        }
    }

    public String getProcessImage(FileUploadRegisterDto fileUploadRegisterDto) {
        RestTemplate restTemplate = new RestTemplate();
        String url = fastApiUrl;

        if(fileUploadRegisterDto.getColorsList() != null){
            log.info("intensity: " + fileUploadRegisterDto.getIntensity());
            log.info("remove colors on problemImage by colors: " + fileUploadRegisterDto.getColorsList());
            url += "/process-color";
        } else if (fileUploadRegisterDto.getPoints() != null){
            if(fileUploadRegisterDto.getPoints().isEmpty()){
                fileUploadRegisterDto.setPoints(List.of(List.of(0.0, 0.0, 0.0, 0.0)));
            }
            log.info("point list: " + fileUploadRegisterDto.getPoints().toString());
            url += "/process-shape";
        } else{
            url += "/process-color";
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<FileUploadRegisterDto> request = new HttpEntity<>(fileUploadRegisterDto, headers);

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

    public String saveAndGetProcessImageUrl(FileUploadRegisterDto fileUploadRegisterDto, Problem problem, ProblemImageType problemImageType) {
        RestTemplate restTemplate = new RestTemplate();
        String url = fastApiUrl + "/process-color";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<FileUploadRegisterDto> request = new HttpEntity<>(fileUploadRegisterDto, headers);

        log.info("remove colors on problemImage by colors: " + fileUploadRegisterDto.getColorsList());

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            log.info("Response from fastApi server: " + response);

            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(response.getBody());
            JsonNode pathNode = rootNode.path("path");
            String inputPath = pathNode.path("output_path").asText();

            String fileUrl = "https://" + bucket + ".s3.ap-northeast-2.amazonaws.com/" + inputPath;
            saveImageData(fileUrl, problem, problemImageType);

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

    public String updateImage(MultipartFile file, Problem problem, ProblemImageType problemImageType) {
        try {
            Optional<ProblemImageData> beforeImageData = problemImageRepository.findByProblemIdAndImageType(problem.getId(), problemImageType);
            beforeImageData.ifPresent(this::deleteImage);

            if (problemImageType.equals(ProblemImageType.PROBLEM_IMAGE)) {
                Optional<ProblemImageData> processImageData = problemImageRepository.findByProblemIdAndImageType(problem.getId(), ProblemImageType.PROCESS_IMAGE);
                processImageData.ifPresent(this::deleteImage);
            }

            String fileName = createFileName(file, problem, problemImageType);
            String fileUrl = getFileUrl(fileName);

            ObjectMetadata objectMetadata = new ObjectMetadata();
            objectMetadata.setContentType(file.getContentType());
            objectMetadata.setContentLength(file.getSize());

            amazonS3Client.putObject(bucket, fileName, file.getInputStream(), objectMetadata);
            saveImageData(fileUrl, problem, problemImageType);

            log.info("file url : " + fileUrl + " successfully updated");
            return fileUrl;
        } catch (IOException e) {
            throw new ProblemRegisterException();
        }
    }

    @Transactional(readOnly = true)
    public List<ProblemImageData> getProblemImages(Long problemId) {
        return problemImageRepository.findByProblemId(problemId);
    }

    public void deleteImage(ProblemImageData problemImageData) {

        String fileUrl = problemImageData.getImageUrl();
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

        log.info("imageData: " + problemImageData.getImageUrl() + " has successfully deleted");
        problemImageRepository.deleteById(problemImageData.getId());
    }

    private String createFileName(MultipartFile file, Problem problem, ProblemImageType problemImageType) {
        return problem.getCreatedAt() + "/" + problemImageType.getDescription() + "_" + file.getOriginalFilename();
    }

    private String getFileUrl(String fileName) {
        return "https://" + bucket + ".s3.ap-northeast-2.amazonaws.com/" + fileName;
    }
}
