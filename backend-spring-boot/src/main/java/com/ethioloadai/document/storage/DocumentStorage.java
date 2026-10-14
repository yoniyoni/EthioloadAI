package com.ethioloadai.document.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface DocumentStorage {
    StoredDocument put(String objectKey, MultipartFile file) throws IOException;
    StoredDocument get(String objectKey, String filename, String contentType);
    void delete(String objectKey);

    record StoredDocument(Resource resource, String filename, String contentType) {}
}