package com.ethioloadai.document.storage;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;

public class B2DocumentStorage implements DocumentStorage {
    private final S3Client client;
    private final String bucket;

    public B2DocumentStorage(S3Client client, String bucket) { this.client = client; this.bucket = bucket; }

    @Override
    public StoredDocument put(String objectKey, MultipartFile file) throws IOException {
        String contentType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
        client.putObject(PutObjectRequest.builder().bucket(bucket).key(objectKey).contentType(contentType).build(),
                RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        return new StoredDocument(null, file.getOriginalFilename(), contentType);
    }

    @Override
    public StoredDocument get(String objectKey, String filename, String contentType) {
        ResponseBytes<GetObjectResponse> bytes = client.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(objectKey).build());
        String resolvedType = bytes.response().contentType() == null ? contentType : bytes.response().contentType();
        return new StoredDocument(new ByteArrayResource(bytes.asByteArray()), filename, resolvedType);
    }

    @Override
    public void delete(String objectKey) { client.deleteObject(builder -> builder.bucket(bucket).key(objectKey)); }
}