package com.ethioloadai.document.storage;

import com.ethioloadai.carrier.company.entity.CarrierCompany;
import com.ethioloadai.carrier.company.entity.CarrierCompanyDocument;
import com.ethioloadai.carrier.company.entity.CompanyDocumentType;
import com.ethioloadai.carrier.company.entity.VerificationStatus;
import com.ethioloadai.carrier.company.mapper.CarrierCompanyMapper;
import com.ethioloadai.carrier.company.repository.CarrierCompanyDocumentRepository;
import com.ethioloadai.carrier.company.repository.CarrierCompanyRepository;
import com.ethioloadai.carrier.company.service.CarrierCompanyService;
import com.ethioloadai.carrier.company.service.CarrierCompanyServiceImpl;
import com.ethioloadai.carrier.team.entity.CompanyMembership;
import com.ethioloadai.carrier.team.repository.CompanyMembershipRepository;
import com.ethioloadai.user.entity.User;
import jakarta.persistence.EntityManagerFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = RealB2CarrierCompanyDocumentTransactionIntegrationTest.TestConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RealB2CarrierCompanyDocumentTransactionIntegrationTest {

    private static final byte[] PDF = "%PDF-1.7 real B2 company document test".getBytes(StandardCharsets.US_ASCII);

    @Autowired private CarrierCompanyService companyService;
    @Autowired private CarrierCompanyRepository companyRepository;
    @Autowired private CarrierCompanyDocumentRepository documentRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private DataSource dataSource;
    @Autowired private B2DocumentStorage b2Storage;
    @Autowired private RecordingDocumentStorage recordingStorage;

    private User owner;
    private CarrierCompany company;
    private CarrierCompanyDocument document;
    private String oldKey;

    @BeforeEach
    void createScenario() throws IOException {
        recordingStorage.resetObservations();
        owner = User.builder().id(77001L)
                .fullName("B2 company owner").phone("unused").password("unused").role(User.Role.FLEET_OWNER).build();
        company = new TransactionTemplate(transactionManager).execute(status ->
                companyRepository.saveAndFlush(CarrierCompany.builder()
                        .companyName("B2 document test " + UUID.randomUUID())
                        .ownerId(owner.getId())
                        .verificationStatus(VerificationStatus.DRAFT)
                        .build()));

        oldKey = "carrier-companies/" + company.getId() + "/old-" + UUID.randomUUID() + ".pdf";
        recordingStorage.recordOwnedKey(oldKey);
        b2Storage.put(oldKey, validPdf());
        assertObjectPresent(oldKey);

        document = new TransactionTemplate(transactionManager).execute(status ->
                documentRepository.saveAndFlush(CarrierCompanyDocument.builder()
                        .carrierCompanyId(company.getId())
                        .documentType(CompanyDocumentType.BUSINESS_LICENSE)
                        .storageKey(oldKey)
                        .originalName("old-license.pdf")
                        .contentType("application/pdf")
                        .fileSize((long) PDF.length)
                        .verificationStatus(VerificationStatus.REJECTED)
                        .uploadedAt(java.time.LocalDateTime.now())
                        .build()));
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

        companyService.upload(company.getId(), CompanyDocumentType.BUSINESS_LICENSE, validPdf(), owner);

        String replacementKey = recordingStorage.lastPutKey();
        assertThat(replacementKey).isNotBlank().isNotEqualTo(oldKey);
        assertThat(documentStorageKey()).isEqualTo(replacementKey);
        assertObjectAbsent(oldKey);
        assertObjectPresent(replacementKey);
    }

    @Test
    void forcedRollbackRetainsOldObjectAndDeletesReplacement() {
        assertSpringTransactionSetup();
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> transactions.execute(status -> {
            companyService.upload(company.getId(), CompanyDocumentType.BUSINESS_LICENSE, validPdf(), owner);
            throw new ForcedRollbackException();
        })).isInstanceOf(ForcedRollbackException.class);

        String replacementKey = recordingStorage.lastPutKey();
        assertThat(replacementKey).isNotBlank().isNotEqualTo(oldKey);
        assertThat(documentStorageKey()).isEqualTo(oldKey);
        assertObjectPresent(oldKey);
        assertObjectAbsent(replacementKey);
    }

    @Test
    void unauthorizedDownloadIsDeniedBeforeStorageAccess() {
        User other = User.builder().id(7002L).fullName("Unauthorized B2 company reader")
                .phone("unused").password("unused").role(User.Role.FLEET_OWNER).build();
        recordingStorage.resetRequestCount();

        assertThatThrownBy(() -> companyService.download(company.getId(), document.getId(), other))
                .isInstanceOf(com.ethioloadai.carrier.company.exception.CarrierCompanyException.class);
        assertThat(recordingStorage.requestCount()).isZero();
    }

    @Test
    void authorizedOwnerDownloadReturnsTheUploadedBytes() throws IOException {
        var downloaded = companyService.download(company.getId(), document.getId(), owner);

        assertThat(downloaded.resource().getInputStream().readAllBytes()).containsExactly(PDF);
    }

    private void assertSpringTransactionSetup() {
        assertThat(AopUtils.isAopProxy(companyService)).isTrue();
        assertThat(transactionManager).isInstanceOf(JpaTransactionManager.class);
        assertThat(dataSource).isInstanceOf(JdbcDataSource.class);
    }

    private String documentStorageKey() {
        return new TransactionTemplate(transactionManager).execute(status ->
                documentRepository.findById(document.getId()).orElseThrow().getStorageKey());
    }

    private MockMultipartFile validPdf() {
        return new MockMultipartFile("file", "license.pdf", "application/pdf", PDF);
    }

    private void assertObjectAbsent(String objectKey) {
        assertThatThrownBy(() -> b2Storage.get(objectKey, "test.pdf", "application/pdf"))
                .isInstanceOf(DocumentStorageObjectNotFoundException.class);
    }

    private void assertObjectPresent(String objectKey) {
        assertThat(b2Storage.get(objectKey, "test.pdf", "application/pdf").resource()).isNotNull();
    }

    private static final class ForcedRollbackException extends RuntimeException {}

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @EnableJpaRepositories(
            basePackageClasses = {
                    CarrierCompanyRepository.class, CarrierCompanyDocumentRepository.class,
                    CompanyMembershipRepository.class
            },
            excludeFilters = @org.springframework.context.annotation.ComponentScan.Filter(
                    type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE,
                    classes = com.ethioloadai.carrier.team.repository.CompanyInvitationRepository.class))
    @Import(CarrierCompanyServiceImpl.class)
    static class TestConfiguration {

        @Bean
        DataSource dataSource() {
            JdbcDataSource dataSource = new JdbcDataSource();
            dataSource.setURL("jdbc:h2:mem:real_b2_company_tx_" + UUID.randomUUID().toString().replace("-", "")
                    + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;NON_KEYWORDS=VALUE");
            dataSource.setUser("sa");
            dataSource.setPassword("");
            return dataSource;
        }

        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPackagesToScan(
                    CarrierCompany.class.getPackageName(), CompanyMembership.class.getPackageName());
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
        CarrierCompanyMapper carrierCompanyMapper() {
            return Mappers.getMapper(CarrierCompanyMapper.class);
        }

        @Bean
        @Primary
        RecordingDocumentStorage recordingDocumentStorage(B2DocumentStorage b2Storage) {
            return new RecordingDocumentStorage(b2Storage);
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

        void resetObservations() {
            lastPutKey = null;
            requestCount.set(0);
        }

        void resetRequestCount() {
            requestCount.set(0);
        }

        void clearOwnedKeys() {
            ownedKeys.clear();
        }
    }
}
