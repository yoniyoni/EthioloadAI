package com.ethioloadai.document.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Primary;
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
    @Profile("e2e")
    DocumentStorage e2eDocumentStorage() {
        return new LocalDocumentStorage(Path.of("uploads-e2e"));
    }

    @Bean
    @Profile("prod")
    B2DocumentStorage b2DocumentStorage(
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
        validateEndpoint(endpoint, region);
        S3Client client = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(keyId, applicationKey)))
                .build();
        return new B2DocumentStorage(client, bucketName);
    }

    @Bean
    @Profile("prod")
    @Primary
    DocumentStorage productionDocumentStorage(
            B2DocumentStorage b2Storage,
            @Value("${b2.legacy-local-path:uploads}") String legacyLocalPath) {
        return new LegacyDocumentStorage(b2Storage, new LocalDocumentStorage(Path.of(legacyLocalPath)));
    }

    static void validateEndpoint(String endpoint, String region) {
        URI uri;
        try {
            uri = URI.create(endpoint);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("B2_ENDPOINT must be the HTTPS regional S3 endpoint", e);
        }
        String expectedHost = "s3." + region + ".backblazeb2.com";
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || !expectedHost.equalsIgnoreCase(uri.getHost())
                || uri.getUserInfo() != null
                || uri.getQuery() != null
                || uri.getFragment() != null
                || (uri.getPort() != -1 && uri.getPort() != 443)
                || (uri.getPath() != null && !uri.getPath().isEmpty() && !"/".equals(uri.getPath()))) {
            throw new IllegalStateException("B2_ENDPOINT must match the HTTPS endpoint for B2_REGION");
        }
    }

    private void requireConfigured(String value, String variable) {
        if (value == null || value.isBlank()) throw new IllegalStateException(variable + " must be configured for the prod profile");
    }
}