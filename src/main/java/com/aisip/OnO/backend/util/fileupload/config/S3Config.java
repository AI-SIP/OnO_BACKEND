package com.aisip.OnO.backend.util.fileupload.config;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class S3Config {
    @Value("${cloud.aws.credentials.access-key}")
    private String accessKey;
    @Value("${cloud.aws.credentials.secret-key}")
    private String secretKey;
    @Value("${cloud.aws.region.static}")
    private String region;
    /**
     * 블랙박스 테스트가 S3 대신 S3 호환 가짜 서버 컨테이너를 붙일 때만 쓴다(#422).
     * 운영과 dev 설정에는 이 키가 없고, 비어 있으면 지금처럼 AWS 리전 엔드포인트를 쓴다.
     */
    @Value("${cloud.aws.s3.endpoint:}")
    private String endpoint;

    @Bean
    public AmazonS3Client amazonS3Client() {
        BasicAWSCredentials basicAWSCredentials = new BasicAWSCredentials(accessKey, secretKey);
        AmazonS3ClientBuilder builder = AmazonS3ClientBuilder.standard()
                .withCredentials(new AWSStaticCredentialsProvider(basicAWSCredentials));
        if (endpoint == null || endpoint.isBlank()) {
            builder.withRegion(region);
        } else {
            // 가짜 서버는 버킷을 호스트(bucket.s3...)가 아니라 경로로 받는다.
            builder.withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(endpoint, region))
                    .withPathStyleAccessEnabled(true);
        }
        return (AmazonS3Client) builder.build();
    }
}
