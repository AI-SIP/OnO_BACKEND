package com.aisip.OnO.backend.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("s3UploadExecutor")
    public Executor s3UploadExecutor() {
        return Executors.newFixedThreadPool(10);
    }
}
