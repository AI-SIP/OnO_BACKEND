package com.aisip.OnO.backend.service;

import com.aisip.OnO.backend.entity.Image.ImageData;
import com.aisip.OnO.backend.entity.Image.ImageType;
import com.aisip.OnO.backend.entity.Problem;
import com.aisip.OnO.backend.repository.ImageDataRepository;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ObjectMetadata;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FileUploadServiceImpl implements FileUploadService{
    private final AmazonS3Client amazonS3Client;

    private final ImageDataRepository imageDataRepository;
    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    public String uploadFileToS3(MultipartFile file) throws IOException {
        String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
        String fileUrl = "https://" + bucket + ".s3.ap-northeast-2.amazonaws.com/" + fileName;

        ObjectMetadata objectMetadata = new ObjectMetadata();
        objectMetadata.setContentType(file.getContentType());
        objectMetadata.setContentLength(file.getSize());

        amazonS3Client.putObject(bucket, fileName, file.getInputStream(), objectMetadata);

        return fileUrl;
    }

    public ImageData saveImageData(String imageUrl, Problem problem, ImageType imageType){

        ImageData imageData = ImageData.builder()
                .imageUrl(imageUrl)
                .problem(problem)
                .imageType(imageType)
                .build();

        return imageDataRepository.save(imageData);
    }

    @Transactional(readOnly = true)
    public List<ImageData> getProblemImages(Long problemId) {
        return imageDataRepository.findByProblemId(problemId);
    }

    //파일 이름 생성 로직
    private String createFileName(String originalFileName) {
        return UUID.randomUUID().toString().concat(getFileExtension(originalFileName));
    }

    //파일의 확장자명을 가져오는 로직
    private String getFileExtension(String fileName){
        try{
            return fileName.substring(fileName.lastIndexOf("."));
        }catch(StringIndexOutOfBoundsException e) {
            throw new IllegalArgumentException(String.format("잘못된 형식의 파일 (%s) 입니다.",fileName));
        }
    }
}
