package com.aisip.OnO.backend.fcm.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;

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
        ClassPathResource resource = new ClassPathResource(filePath);
        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(resource.getInputStream()))
                .setProjectId(projectId)
                .build();

        if (FirebaseApp.getApps().isEmpty()) {
            FirebaseApp.initializeApp(options);
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
