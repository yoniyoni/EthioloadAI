package com.ethioloadai.document.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class B2DocumentStorageTest {
    private static final String BUCKET = "ethioload-private-documents";
    private static final String KEY = "driver-documents/42/license/random.pdf";

    private S3Client client;
    private B2DocumentStorage storage;

    @BeforeEach
    void setUp() {
        client = mock(S3Client.class);
        storage = new B2DocumentStorage(client, BUCKET);
    }

    @Test
    void uploadUsesPrivateBucketObjectKeyAndValidatedContentType() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "license.pdf", "application/pdf", "%PDF-1.7".getBytes());
        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);

        storage.put(KEY, file);

        verify(client).putObject(request.capture(), any(RequestBody.class));
        assertThat(request.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(request.getValue().key()).isEqualTo(KEY);
        assertThat(request.getValue().contentType()).isEqualTo("application/pdf");
    }

    @Test
    void downloadReturnsStoredBytesWithoutCreatingPublicUrl() {
        byte[] content = "%PDF-1.7 private".getBytes();
        GetObjectResponse response = GetObjectResponse.builder().contentType("application/pdf").build();
        when(client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(response, content));

        var stored = storage.get(KEY, "license.pdf", "application/octet-stream");

        assertThat(stored.resource()).isNotNull();
        assertThat(stored.resource().getFilename()).isNull();
        assertThat(stored.contentType()).isEqualTo("application/pdf");
        try {
            assertThat(stored.resource().getInputStream().readAllBytes()).isEqualTo(content);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void onlyConfirmedNoSuchKeyIsNormalizedForGetObject() {
        when(client.getObjectAsBytes(any(GetObjectRequest.class))).thenThrow(s3Exception(404, "NoSuchKey"));

        assertThatThrownBy(() -> storage.get(KEY, "license.pdf", "application/pdf"))
                .isInstanceOf(DocumentStorageObjectNotFoundException.class);
    }

    @Test
    void modeledNoSuchKeyExceptionIsNormalizedForGetObject() {
        when(client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().statusCode(404).message("NoSuchKey").build());

        assertThatThrownBy(() -> storage.get(KEY, "license.pdf", "application/pdf"))
                .isInstanceOf(DocumentStorageObjectNotFoundException.class);
    }

    @Test
    void missingBucket404FromGetObjectIsPropagated() {
        when(client.getObjectAsBytes(any(GetObjectRequest.class))).thenThrow(s3Exception(404, "NoSuchBucket"));

        assertThatThrownBy(() -> storage.get(KEY, "license.pdf", "application/pdf"))
                .isInstanceOf(S3Exception.class)
                .extracting("awsErrorDetails.errorCode").isEqualTo("NoSuchBucket");
    }

    @Test
    void unclassified404FromGetObjectIsPropagated() {
        when(client.getObjectAsBytes(any(GetObjectRequest.class))).thenThrow(s3Exception(404, null));

        assertThatThrownBy(() -> storage.get(KEY, "license.pdf", "application/pdf"))
                .isInstanceOf(S3Exception.class)
                .extracting("statusCode").isEqualTo(404);
    }

    @Test
    void forbiddenGetObjectFailureIsPropagated() {
        when(client.getObjectAsBytes(any(GetObjectRequest.class))).thenThrow(s3Exception(403, "AccessDenied"));

        assertThatThrownBy(() -> storage.get(KEY, "license.pdf", "application/pdf"))
                .isInstanceOf(S3Exception.class)
                .extracting("statusCode").isEqualTo(403);
    }

    @Test
    void unclassifiedHeadObject404IsPropagated() {
        when(client.headObject(any(HeadObjectRequest.class))).thenThrow(s3Exception(404, null));

        assertThatThrownBy(() -> storage.exists(KEY))
                .isInstanceOf(S3Exception.class)
                .extracting("statusCode").isEqualTo(404);
    }

    @Test
    void headObject404WithNoSuchKeyCodeIsStillPropagated() {
        when(client.headObject(any(HeadObjectRequest.class))).thenThrow(s3Exception(404, "NoSuchKey"));

        assertThatThrownBy(() -> storage.exists(KEY))
                .isInstanceOf(S3Exception.class)
                .extracting("statusCode").isEqualTo(404);
    }

    @Test
    void existenceAndDeletionUseBucketScopedRequests() {
        when(client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder().build());
        when(client.deleteObject(any(DeleteObjectRequest.class))).thenReturn(DeleteObjectResponse.builder().build());

        assertThat(storage.exists(KEY)).isTrue();
        storage.delete(KEY);

        ArgumentCaptor<HeadObjectRequest> head = ArgumentCaptor.forClass(HeadObjectRequest.class);
        ArgumentCaptor<DeleteObjectRequest> delete = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(client).headObject(head.capture());
        verify(client).deleteObject(delete.capture());
        assertThat(head.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(head.getValue().key()).isEqualTo(KEY);
        assertThat(delete.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(delete.getValue().key()).isEqualTo(KEY);
    }

    @Test
    void providerFailuresAreNotReportedAsMissingObjects() {
        when(client.headObject(any(HeadObjectRequest.class))).thenThrow(s3Exception(503));

        assertThatThrownBy(() -> storage.exists(KEY))
                .isInstanceOf(S3Exception.class)
                .extracting("statusCode").isEqualTo(503);
    }

    @Test
    void uploadProviderFailurePropagates() throws Exception {
        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(s3Exception(503));
        MockMultipartFile file = new MockMultipartFile("file", "license.pdf", "application/pdf", "%PDF-1.7".getBytes());

        assertThatThrownBy(() -> storage.put(KEY, file))
                .isInstanceOf(S3Exception.class)
                .extracting("statusCode").isEqualTo(503);
    }

    private S3Exception s3Exception(int status) {
        return s3Exception(status, null);
    }

    private S3Exception s3Exception(int status, String errorCode) {
        var builder = S3Exception.builder().statusCode(status).message("test response");
        if (errorCode != null) {
            builder.awsErrorDetails(AwsErrorDetails.builder().errorCode(errorCode).build());
        }
        return (S3Exception) builder.build();
    }
}
