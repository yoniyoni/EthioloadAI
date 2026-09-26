package com.ethioloadai.document.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;
import java.nio.file.Path;

@Configuration
public class DocumentStorageConfiguration {
    @Bean
    @Profile({"dev", "test"})
    DocumentStorage localDocumentStorage() { return new LocalDocumentStorage(Path.of("uploads")); }

    @Bean
    @Profile("prod")
    DocumentStorage b2DocumentStorage(
            @Value("${b2.key-id}") String keyId,
            @Value("${b2.application-key}") String applicationKey,
            @Value("${b2.bucket-name}") String bucketName,
            @Value("${b2.endpoint}") String endpoint,
            @Value("${b2.region}") String region) {
        requireConfigured(keyId, "B2_KEY_ID");
        requireConfigured(applicationKey, "B2_APPLICATION_KEY");
        requireConfigured(bucketName, "B2_BUCKET_NAME");
        requireConfigured(endpoint, "B2_ENDPOINT");
        requireConfigured(region, "B2_REGION");
        S3Client client = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(keyId, applicationKey)))
                .build();
        return new B2DocumentStorage(client, bucketName);
    }

    private void requireConfigured(String value, String variable) {
        if (value == null || value.isBlank()) throw new IllegalStateException(variable + " must be configured for the prod profile");
    }
}