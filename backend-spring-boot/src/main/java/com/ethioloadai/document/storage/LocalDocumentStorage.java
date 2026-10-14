package com.ethioloadai.document.storage;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class LocalDocumentStorage implements DocumentStorage {
    private final Path root;

    public LocalDocumentStorage(Path root) { this.root = root.toAbsolutePath().normalize(); }

    @Override
    public StoredDocument put(String objectKey, MultipartFile file) throws IOException {
        Path target = resolve(objectKey);
        Files.createDirectories(target.getParent());
        file.transferTo(target);
        return new StoredDocument(new FileSystemResource(target), file.getOriginalFilename(), file.getContentType());
    }

    @Override
    public StoredDocument get(String objectKey, String filename, String contentType) {
        Resource resource = new FileSystemResource(resolve(objectKey));
        if (!resource.exists() || !resource.isReadable()) throw new IllegalStateException("Document object not found");
        return new StoredDocument(resource, filename, contentType);
    }

    @Override
    public void delete(String objectKey) {
        try { Files.deleteIfExists(resolve(objectKey)); } catch (IOException ignored) { }
    }

    private Path resolve(String objectKey) {
        Path resolved = root.resolve(objectKey).normalize();
        if (!resolved.startsWith(root)) throw new IllegalArgumentException("Invalid document object key");
        return resolved;
    }
}