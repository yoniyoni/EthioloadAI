package com.ethioloadai.document.storage;

public class DocumentStorageObjectNotFoundException extends RuntimeException {
    public DocumentStorageObjectNotFoundException(String objectKey) {
        super("Document object not found");
    }
}
