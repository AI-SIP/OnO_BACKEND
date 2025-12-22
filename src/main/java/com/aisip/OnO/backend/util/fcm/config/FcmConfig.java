package com.aisip.OnO.backend.util.fcm.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.io.InputStream;

@Slf4j
@Configuration
public class FcmConfig {

    @Value("${fcm.file-path}")
    private String filePath;

    @Value("${fcm.project-id}")
    private String projectId;

    /*
    public FcmConfig(
            @Value("${fcm.file_path}") String firebaseFilePath,
            @Value("${fcm.project_id}") String projectId
    ) {
        this.firebaseResource = new ClassPathResource(firebaseFilePath);
        this.projectId = projectId;
    }

     */

    @PostConstruct
    public void init() throws IOException {
        // ClassPathResource와 FileSystemResource 모두 시도
        Resource resource;
        try {
            // 먼저 classpath에서 시도 (로컬 개발 환경)
            resource = new ClassPathResource(filePath);
            if (!resource.exists()) {
                // classpath에 없으면 파일 시스템에서 시도 (Docker 환경)
                resource = new FileSystemResource(filePath);
            }
        } catch (Exception e) {
            // classpath 실패 시 파일 시스템에서 시도
            resource = new FileSystemResource(filePath);
        }

        try (InputStream inputStream = resource.getInputStream()) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(inputStream))
                    .setProjectId(projectId)
                    .build();

            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
            }
        }
    }

    @Bean
    FirebaseMessaging firebaseMessaging() {
        return FirebaseMessaging.getInstance(firebaseApp());
    }

    @Bean
    FirebaseApp firebaseApp() {
        return FirebaseApp.getInstance();
    }
}
