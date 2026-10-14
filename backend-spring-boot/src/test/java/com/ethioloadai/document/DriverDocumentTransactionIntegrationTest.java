package com.ethioloadai.document;

import com.ethioloadai.document.entity.DriverDocument;
import com.ethioloadai.document.entity.DriverDocument.DocumentStatus;
import com.ethioloadai.document.entity.DriverDocument.DocumentType;
import com.ethioloadai.document.exception.DocumentException;
import com.ethioloadai.document.repository.DriverDocumentRepository;
import com.ethioloadai.document.service.DriverDocumentService;
import com.ethioloadai.document.storage.DocumentStorage;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.user.repository.UserRepository;
import com.ethioloadai.websocket.notification.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.aop.support.AopUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:driver_document_transaction_testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=PostgreSQL;NON_KEYWORDS=VALUE"
})
@ActiveProfiles("test")
@Import(DriverDocumentTransactionIntegrationTest.StorageTestConfiguration.class)
class DriverDocumentTransactionIntegrationTest {

    private static final byte[] PDF = "%PDF-1.7 transaction test".getBytes(StandardCharsets.US_ASCII);

    @Autowired private DriverDocumentService documentService;
    @Autowired private DriverDocumentRepository documentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private InMemoryDocumentStorage storage;
    @Autowired private jakarta.persistence.EntityManager entityManager;

    @MockBean private NotificationService notificationService;

    @Test
    void replacementDeletesOldObjectOnlyAfterRealTransactionCommit() {
        assertThat(AopUtils.isAopProxy(documentService)).isTrue();
        assertThat(transactionManager).isInstanceOf(JpaTransactionManager.class);
        TransactionTemplate transactions = new TransactionTemplate(Objects.requireNonNull(transactionManager));
        Scenario scenario = createScenario(transactions);

        assertThat(documentPath(transactions, scenario)).isEqualTo(scenario.oldKey());
        assertThat(storage.keys()).contains(scenario.oldKey());

        String newKey = transactions.execute(status -> {
            documentService.uploadDocument(DocumentType.LICENSE, validPdf(), scenario.driver());

            DriverDocument updated = documentRepository
                    .findByUser_IdAndDocumentType(scenario.driver().getId(), DocumentType.LICENSE)
                    .orElseThrow();
            entityManager.flush();

            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(updated.getFilePath()).isNotEqualTo(scenario.oldKey());
            assertThat(storage.putObservedActiveTransaction()).isTrue();
            assertThat(storage.putObservedSynchronization()).isTrue();
            assertThat(storage.keys()).contains(scenario.oldKey(), updated.getFilePath());
            assertThat(storage.deletedKeys()).doesNotContain(scenario.oldKey());
            return updated.getFilePath();
        });

        assertThat(newKey).isNotNull();
        assertThat(documentPath(transactions, scenario)).isEqualTo(newKey);
        assertThat(storage.keys()).contains(newKey).doesNotContain(scenario.oldKey());
        assertThat(storage.deletedKeys()).containsExactly(scenario.oldKey());
    }

    @Test
    void replacementDeletesNewObjectAndRetainsOldObjectAfterRealTransactionRollback() {
        assertThat(AopUtils.isAopProxy(documentService)).isTrue();
        assertThat(transactionManager).isInstanceOf(JpaTransactionManager.class);
        TransactionTemplate transactions = new TransactionTemplate(Objects.requireNonNull(transactionManager));
        Scenario scenario = createScenario(transactions);

        assertThat(documentPath(transactions, scenario)).isEqualTo(scenario.oldKey());
        assertThat(storage.keys()).contains(scenario.oldKey());

        assertThatThrownBy(() -> transactions.execute(status -> {
            documentService.uploadDocument(DocumentType.LICENSE, validPdf(), scenario.driver());

            DriverDocument updated = documentRepository
                    .findByUser_IdAndDocumentType(scenario.driver().getId(), DocumentType.LICENSE)
                    .orElseThrow();
            entityManager.flush();

            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(updated.getFilePath()).isNotEqualTo(scenario.oldKey());
            assertThat(storage.putObservedActiveTransaction()).isTrue();
            assertThat(storage.putObservedSynchronization()).isTrue();
            assertThat(storage.keys()).contains(scenario.oldKey(), updated.getFilePath());
            assertThat(storage.deletedKeys()).doesNotContain(scenario.oldKey(), updated.getFilePath());

            throw new ForcedRollbackException();
        })).isInstanceOf(ForcedRollbackException.class);

        assertThat(documentPath(transactions, scenario)).isEqualTo(scenario.oldKey());
        assertThat(storage.keys()).containsExactly(scenario.oldKey());
        assertThat(storage.deletedKeys()).hasSize(1).doesNotContain(scenario.oldKey());
        assertThat(storage.deletedKeys().getFirst()).startsWith(
                "driver-documents/" + scenario.driver().getId() + "/license/");
    }

    @Test
    void notificationFailureRollsBackDocumentAndDeletesOnlyNewObject() {
        assertThat(AopUtils.isAopProxy(documentService)).isTrue();
        assertThat(transactionManager).isInstanceOf(JpaTransactionManager.class);
        TransactionTemplate transactions = new TransactionTemplate(Objects.requireNonNull(transactionManager));
        Scenario scenario = createScenario(transactions);
        AtomicReference<String> replacementKey = new AtomicReference<>();

        doAnswer(invocation -> {
            DriverDocument updated = documentRepository
                    .findByUser_IdAndDocumentType(scenario.driver().getId(), DocumentType.LICENSE)
                    .orElseThrow();
            entityManager.flush();
            replacementKey.set(updated.getFilePath());

            assertThat(updated.getFilePath()).isNotEqualTo(scenario.oldKey());
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isTrue();
            assertThat(storage.keys()).contains(scenario.oldKey(), updated.getFilePath());
            assertThat(storage.deletedKeys()).doesNotContain(scenario.oldKey());
            throw new IllegalStateException("simulated notification failure");
        }).when(notificationService).notifyRole(eq("admin"), any());

        assertThatThrownBy(() -> transactions.execute(status ->
                documentService.uploadDocument(DocumentType.LICENSE, validPdf(), scenario.driver())))
                .isInstanceOf(DocumentException.DocumentValidationException.class);

        assertThat(replacementKey.get()).isNotNull();
        assertThat(documentPath(transactions, scenario)).isEqualTo(scenario.oldKey());
        assertThat(storage.keys()).containsExactly(scenario.oldKey());
        assertThat(storage.deletedKeys()).containsExactly(replacementKey.get());
    }

    private Scenario createScenario(TransactionTemplate transactions) {
        storage.reset();
        User driver = Objects.requireNonNull(transactions.execute(status -> userRepository.saveAndFlush(
                Objects.requireNonNull(User.builder()
                        .fullName("Transaction test driver")
                        .phone("+tx" + UUID.randomUUID().toString().replace("-", "").substring(0, 10))
                        .password("test-password")
                        .role(User.Role.DRIVER)
                        .verificationStatus(false)
                        .isActive(true)
                        .build()))));

        String oldKey = "driver-documents/" + driver.getId() + "/license/old-" + UUID.randomUUID() + ".pdf";
        storage.putExisting(oldKey, PDF);
        transactions.execute(status -> {
            documentRepository.saveAndFlush(Objects.requireNonNull(DriverDocument.builder()
                    .user(driver)
                    .documentType(DocumentType.LICENSE)
                    .documentLabel(DocumentType.LICENSE.getLabel())
                    .fileUrl(oldKey)
                    .filePath(oldKey)
                    .originalName("old-license.pdf")
                    .mimeType("application/pdf")
                    .fileSize((long) PDF.length)
                    .status(DocumentStatus.REJECTED)
                    .build()));
            return Boolean.TRUE;
        });
        storage.clearObservations();
        return new Scenario(driver, oldKey);
    }

    private String documentPath(TransactionTemplate transactions, Scenario scenario) {
        return transactions.execute(status -> documentRepository
                .findByUser_IdAndDocumentType(scenario.driver().getId(), DocumentType.LICENSE)
                .orElseThrow()
                .getFilePath());
    }

    private MockMultipartFile validPdf() {
        return new MockMultipartFile("file", "new-license.pdf", "application/pdf", PDF);
    }

    private record Scenario(User driver, String oldKey) {}

    private static final class ForcedRollbackException extends RuntimeException {}

    @TestConfiguration
    static class StorageTestConfiguration {
        @Bean
        @Primary
        InMemoryDocumentStorage inMemoryDocumentStorage() {
            return new InMemoryDocumentStorage();
        }
    }

    static class InMemoryDocumentStorage implements DocumentStorage {
        private final ConcurrentHashMap<String, byte[]> objects = new ConcurrentHashMap<>();
        private final CopyOnWriteArrayList<String> deleted = new CopyOnWriteArrayList<>();
        private volatile boolean putSawActiveTransaction;
        private volatile boolean putSawActiveSynchronization;

        @Override
        public StoredDocument put(String objectKey, org.springframework.web.multipart.MultipartFile file)
                throws IOException {
            objects.put(objectKey, file.getBytes());
            putSawActiveTransaction = TransactionSynchronizationManager.isActualTransactionActive();
            putSawActiveSynchronization = TransactionSynchronizationManager.isSynchronizationActive();
            return get(objectKey, file.getOriginalFilename(), file.getContentType());
        }

        @Override
        public StoredDocument get(String objectKey, String filename, String contentType) {
            byte[] bytes = objects.get(objectKey);
            if (bytes == null) {
                throw new IllegalArgumentException("Missing test object: " + objectKey);
            }
            return new StoredDocument(new ByteArrayResource(bytes), filename, contentType);
        }

        @Override
        public void delete(String objectKey) {
            objects.remove(objectKey);
            deleted.add(objectKey);
        }

        void putExisting(String objectKey, byte[] bytes) {
            objects.put(objectKey, bytes.clone());
        }

        Set<String> keys() {
            return Set.copyOf(objects.keySet());
        }

        CopyOnWriteArrayList<String> deletedKeys() {
            return new CopyOnWriteArrayList<>(deleted);
        }

        boolean putObservedActiveTransaction() {
            return putSawActiveTransaction;
        }

        boolean putObservedSynchronization() {
            return putSawActiveSynchronization;
        }

        void clearObservations() {
            deleted.clear();
            putSawActiveTransaction = false;
            putSawActiveSynchronization = false;
        }

        void reset() {
            objects.clear();
            deleted.clear();
            putSawActiveTransaction = false;
            putSawActiveSynchronization = false;
        }
    }
}
