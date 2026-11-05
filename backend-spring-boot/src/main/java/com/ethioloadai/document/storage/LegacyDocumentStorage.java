package com.ethioloadai.document.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public class LegacyDocumentStorage implements DocumentStorage {
    private final DocumentStorage primary;
    private final DocumentStorage legacy;

    public LegacyDocumentStorage(DocumentStorage primary, DocumentStorage legacy) {
        this.primary = primary;
        this.legacy = legacy;
    }

    @Override
    public StoredDocument put(String objectKey, MultipartFile file) throws IOException {
        return primary.put(objectKey, file);
    }

    @Override
    public StoredDocument get(String objectKey, String filename, String contentType) {
        try {
            return primary.get(objectKey, filename, contentType);
        } catch (DocumentStorageObjectNotFoundException e) {
            return legacy.get(objectKey, filename, contentType);
        }
    }

    @Override
    public boolean exists(String objectKey) {
        return primary.exists(objectKey) || legacy.exists(objectKey);
    }

    @Override
    public void delete(String objectKey) {
        primary.delete(objectKey);
        legacy.delete(objectKey);
    }
}
