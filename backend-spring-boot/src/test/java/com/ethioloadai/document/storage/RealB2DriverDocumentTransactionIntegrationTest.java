package com.ethioloadai.document.storage;

import com.ethioloadai.document.entity.DriverDocument;
import com.ethioloadai.document.entity.DriverDocument.DocumentStatus;
import com.ethioloadai.document.entity.DriverDocument.DocumentType;
import com.ethioloadai.document.exception.DocumentException;
import com.ethioloadai.document.repository.DriverDocumentRepository;
import com.ethioloadai.document.service.DriverDocumentService;
import com.ethioloadai.document.service.DriverDocumentServiceImpl;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.user.repository.UserRepository;
import com.ethioloadai.websocket.notification.NotificationService;
import jakarta.persistence.EntityManagerFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.aop.support.AopUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.web.multipart.MultipartFile;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = RealB2DriverDocumentTransactionIntegrationTest.TestConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RealB2DriverDocumentTransactionIntegrationTest {

    private static final byte[] PDF = "%PDF-1.7 real B2 transaction test".getBytes(StandardCharsets.US_ASCII);

    @Autowired private DriverDocumentService documentService;
    @Autowired private DriverDocumentRepository documentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private DataSource dataSource;
    @Autowired private B2DocumentStorage b2Storage;
    @Autowired private RecordingDocumentStorage recordingStorage;
    @Autowired private NotificationService notificationService;

    @BeforeEach
    void resetStorageObservations() {
        recordingStorage.resetObservations();
        reset(notificationService);
    }

    private void assertObjectAbsent(String objectKey) {
        assertThatThrownBy(() -> b2Storage.get(objectKey, "test.pdf", "application/pdf"))
                .isInstanceOf(DocumentStorageObjectNotFoundException.class);
    }

    private void assertObjectPresent(String objectKey) {
        assertThat(b2Storage.get(objectKey, "test.pdf", "application/pdf").resource()).isNotNull();
    }

    @AfterEach
    void deleteOnlyTestOwnedObjects() {
        List<String> cleanupFailures = new ArrayList<>();
        for (String objectKey : recordingStorage.ownedKeys()) {
            try {
                b2Storage.delete(objectKey);
            } catch (RuntimeException e) {
                cleanupFailures.add(objectKey + " delete failed: " + e.getClass().getSimpleName());
            }
            try {
                b2Storage.get(objectKey, "test.pdf", "application/pdf");
                cleanupFailures.add(objectKey + " still exists");
            } catch (DocumentStorageObjectNotFoundException expected) {
            } catch (RuntimeException e) {
                cleanupFailures.add(objectKey + " absence check failed: " + e.getClass().getSimpleName());
            }
        }
        recordingStorage.clearOwnedKeys();
        assertThat(cleanupFailures).as("test-owned B2 object cleanup").isEmpty();
    }

    @Test
    void committedReplacementDeletesOldObjectAndRetainsReplacement() {
        assertSpringTransactionSetup();
        Scenario scenario = createScenario();

        documentService.uploadDocument(DocumentType.LICENSE, validPdf(), scenario.driver());

        String replacementKey = recordingStorage.lastPutKey();
        assertThat(replacementKey).isNotBlank().isNotEqualTo(scenario.oldKey());
        assertThat(documentPath(scenario)).isEqualTo(replacementKey);
        assertObjectAbsent(scenario.oldKey());
        assertObjectPresent(replacementKey);
    }

    @Test
    void forcedRollbackRetainsOldObjectAndDeletesReplacement() {
        assertSpringTransactionSetup();
        Scenario scenario = createScenario();
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> transactions.execute(status -> {
            documentService.uploadDocument(DocumentType.LICENSE, validPdf(), scenario.driver());
            throw new ForcedRollbackException();
        })).isInstanceOf(ForcedRollbackException.class);

        String replacementKey = recordingStorage.lastPutKey();
        assertThat(replacementKey).isNotBlank().isNotEqualTo(scenario.oldKey());
        assertThat(documentPath(scenario)).isEqualTo(scenario.oldKey());
        assertObjectPresent(scenario.oldKey());
        assertObjectAbsent(replacementKey);
    }

    @Test
    void notificationFailureRollsBackRowAndDeletesReplacement() {
        assertSpringTransactionSetup();
        Scenario scenario = createScenario();
        doThrow(new IllegalStateException("simulated notification failure"))
                .when(notificationService).notifyRole(eq("admin"), any());

        assertThatThrownBy(() -> documentService.uploadDocument(
                DocumentType.LICENSE, validPdf(), scenario.driver()))
                .isInstanceOf(DocumentException.DocumentValidationException.class);

        String replacementKey = recordingStorage.lastPutKey();
        assertThat(replacementKey).isNotBlank().isNotEqualTo(scenario.oldKey());
        assertThat(documentPath(scenario)).isEqualTo(scenario.oldKey());
        assertObjectPresent(scenario.oldKey());
        assertObjectAbsent(replacementKey);
    }

    @Test
    void ownerCanDownloadButAnotherDriverIsDeniedBeforeStorageAccess() throws IOException {
        Scenario scenario = createScenario();
        User otherDriver = createDriver();

        var downloaded = documentService.getDocumentFile(scenario.documentId(), scenario.driver());
        assertThat(downloaded.resource().getInputStream().readAllBytes()).containsExactly(PDF);

        recordingStorage.resetRequestCount();
        assertThatThrownBy(() -> documentService.getDocumentFile(scenario.documentId(), otherDriver))
                .isInstanceOf(DocumentException.DocumentAuthorizationException.class);
        assertThat(recordingStorage.requestCount()).isZero();
    }

    private void assertSpringTransactionSetup() {
        assertThat(AopUtils.isAopProxy(documentService)).isTrue();
        assertThat(transactionManager).isInstanceOf(JpaTransactionManager.class);
        assertThat(dataSource).isInstanceOf(JdbcDataSource.class);
    }

    private Scenario createScenario() {
        User driver = createDriver();
        String oldKey = "driver-documents/" + driver.getId() + "/license/old-" + UUID.randomUUID() + ".pdf";
        recordingStorage.recordOwnedKey(oldKey);
        try {
            b2Storage.put(oldKey, validPdf());
        } catch (IOException e) {
            throw new IllegalStateException("Unable to seed test-owned B2 object", e);
        }
        assertObjectPresent(oldKey);

        DriverDocument document = Objects.requireNonNull(new TransactionTemplate(transactionManager).execute(status ->
                documentRepository.saveAndFlush(DriverDocument.builder()
                        .user(driver)
                        .documentType(DocumentType.LICENSE)
                        .documentLabel(DocumentType.LICENSE.getLabel())
                        .fileUrl(oldKey)
                        .filePath(oldKey)
                        .originalName("old-license.pdf")
                        .mimeType("application/pdf")
                        .fileSize((long) PDF.length)
                        .status(DocumentStatus.REJECTED)
                        .build())));
        return new Scenario(driver, document.getId(), oldKey);
    }

    private User createDriver() {
        return Objects.requireNonNull(new TransactionTemplate(transactionManager).execute(status ->
                userRepository.saveAndFlush(User.builder()
                        .fullName("Real B2 transaction test driver")
                        .phone("+b2tx" + UUID.randomUUID().toString().replace("-", "").substring(0, 12))
                        .password("test-password")
                        .role(User.Role.DRIVER)
                        .verificationStatus(false)
                        .isActive(true)
                        .build())));
    }

    private String documentPath(Scenario scenario) {
        return new TransactionTemplate(transactionManager).execute(status -> documentRepository
                .findById(scenario.documentId())
                .orElseThrow()
                .getFilePath());
    }

    private MockMultipartFile validPdf() {
        return new MockMultipartFile("file", "license.pdf", "application/pdf", PDF);
    }

    private record Scenario(User driver, Long documentId, String oldKey) {}

    private static final class ForcedRollbackException extends RuntimeException {}

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = {DriverDocumentRepository.class, UserRepository.class})
    @Import(DriverDocumentServiceImpl.class)
    static class TestConfiguration {

        @Bean
        DataSource dataSource() {
            JdbcDataSource dataSource = new JdbcDataSource();
            dataSource.setURL("jdbc:h2:mem:real_b2_driver_tx_" + UUID.randomUUID().toString().replace("-", "")
                    + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;NON_KEYWORDS=VALUE");
            dataSource.setUser("sa");
            dataSource.setPassword("");
            return dataSource;
        }

        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPackagesToScan(DriverDocument.class.getPackageName(), User.class.getPackageName());
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.getJpaPropertyMap().put("hibernate.hbm2ddl.auto", "create-drop");
            factory.getJpaPropertyMap().put("hibernate.dialect", "org.hibernate.dialect.H2Dialect");
            return factory;
        }

        @Bean
        PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
            return new JpaTransactionManager(entityManagerFactory);
        }

        @Bean
        NotificationService notificationService() {
            return mock(NotificationService.class);
        }

        @Bean(destroyMethod = "close")
        B2DocumentStorage b2DocumentStorage(
                @Value("${b2.key-id}") String keyId,
                @Value("${b2.application-key}") String applicationKey,
                @Value("${b2.bucket-name}") String bucketName,
                @Value("${b2.endpoint}") String endpoint,
                @Value("${b2.region}") String region) {
            return new DocumentStorageConfiguration().b2DocumentStorage(
                    keyId, applicationKey, bucketName, endpoint, region);
        }

        @Bean
        @Primary
        RecordingDocumentStorage recordingDocumentStorage(B2DocumentStorage b2Storage) {
            return new RecordingDocumentStorage(b2Storage);
        }
    }

    static final class RecordingDocumentStorage implements DocumentStorage {
        private final B2DocumentStorage delegate;
        private final Set<String> ownedKeys = ConcurrentHashMap.newKeySet();
        private final AtomicInteger requestCount = new AtomicInteger();
        private volatile String lastPutKey;

        RecordingDocumentStorage(B2DocumentStorage delegate) {
            this.delegate = delegate;
        }

        @Override
        public StoredDocument put(String objectKey, MultipartFile file) throws IOException {
            recordOwnedKey(objectKey);
            lastPutKey = objectKey;
            requestCount.incrementAndGet();
            return delegate.put(objectKey, file);
        }

        @Override
        public StoredDocument get(String objectKey, String filename, String contentType) {
            requestCount.incrementAndGet();
            return delegate.get(objectKey, filename, contentType);
        }

        @Override
        public boolean exists(String objectKey) {
            requestCount.incrementAndGet();
            return delegate.exists(objectKey);
        }

        @Override
        public void delete(String objectKey) {
            requestCount.incrementAndGet();
            delegate.delete(objectKey);
        }

        void recordOwnedKey(String objectKey) {
            ownedKeys.add(objectKey);
        }

        Set<String> ownedKeys() {
            return Set.copyOf(ownedKeys);
        }

        String lastPutKey() {
            return lastPutKey;
        }

        int requestCount() {
            return requestCount.get();
        }

        void resetRequestCount() {
            requestCount.set(0);
        }

        void resetObservations() {
            lastPutKey = null;
            requestCount.set(0);
        }

        void clearOwnedKeys() {
            ownedKeys.clear();
        }
    }
}
