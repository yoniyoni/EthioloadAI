package com.ethioloadai.document.storage;

import com.ethioloadai.booking.entity.Booking;
import com.ethioloadai.delivery.entity.ProofOfDelivery;
import com.ethioloadai.delivery.repository.ProofOfDeliveryRepository;
import com.ethioloadai.delivery.service.ProofOfDeliveryService;
import com.ethioloadai.delivery.service.ProofOfDeliveryServiceImpl;
import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.payment.entity.Payment;
import com.ethioloadai.rating.entity.Rating;
import com.ethioloadai.trip.entity.Trip;
import com.ethioloadai.trip.entity.TripStop;
import com.ethioloadai.trip.repository.TripRepository;
import com.ethioloadai.trip.service.TripLifecycleService;
import com.ethioloadai.settlement.service.SettlementOutboxService;
import com.ethioloadai.notification.service.NotificationStoreService;
import com.ethioloadai.carrier.team.service.CompanySecurityService;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.vehicle.entity.Vehicle;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import org.springframework.orm.jpa.SharedEntityManagerCreator;
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
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = RealB2ProofOfDeliveryTransactionIntegrationTest.TestConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RealB2ProofOfDeliveryTransactionIntegrationTest {

    private static final byte[] JPEG = new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x01, 0x02};

    @Autowired private ProofOfDeliveryService proofService;
    @Autowired private ProofOfDeliveryRepository proofRepository;
    @Autowired private TripRepository tripRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private DataSource dataSource;
    @Autowired private B2DocumentStorage b2Storage;
    @Autowired private RecordingDocumentStorage recordingStorage;

    private User driver;
    private User shipper;
    private Trip trip;
    private ProofOfDelivery proof;
    private String oldKey;

    @BeforeEach
    void createScenario() {
        recordingStorage.resetObservations();
        Scenario scenario = new TransactionTemplate(transactionManager).execute(status -> {
            EntityManager em = entityManager;
            User seededShipper = em.merge(User.builder()
                    .fullName("B2 proof test shipper")
                    .phone("+b2ps" + UUID.randomUUID().toString().replace("-", "").substring(0, 10))
                    .password("test-password")
                    .role(User.Role.SHIPPER)
                    .verificationStatus(true)
                    .isActive(true)
                    .build());
            User seededDriver = em.merge(User.builder()
                    .fullName("B2 proof test driver")
                    .phone("+b2pd" + UUID.randomUUID().toString().replace("-", "").substring(0, 10))
                    .password("test-password")
                    .role(User.Role.DRIVER)
                    .verificationStatus(true)
                    .isActive(true)
                    .build());
            Vehicle vehicle = em.merge(Vehicle.builder()
                    .userId(seededDriver.getId())
                    .truckType("B2 proof test truck")
                    .plateNumber("B2P-" + UUID.randomUUID().toString().substring(0, 8))
                    .capacity(10.0)
                    .availabilityStatus("available")
                    .approvalStatus(Vehicle.VehicleApprovalStatus.APPROVED)
                    .build());
            Freight freight = em.merge(Freight.builder()
                    .shipper(seededShipper)
                    .pickupLocation("B2 test origin")
                    .destination("B2 test destination")
                    .materialType("general")
                    .weightTons(BigDecimal.ONE)
                    .budget(BigDecimal.valueOf(100))
                    .urgencyLevel("normal")
                    .build());
            Booking booking = em.merge(Booking.builder()
                    .cargoRequest(freight)
                    .vehicle(vehicle)
                    .driver(seededDriver)
                    .bookingStatus(Booking.BookingStatus.ACCEPTED)
                    .estimatedPrice(BigDecimal.valueOf(100))
                    .commissionFee(BigDecimal.ZERO)
                    .build());
            Trip seededTrip = em.merge(Trip.builder()
                    .booking(booking)
                    .driver(seededDriver)
                    .vehicle(vehicle)
                    .startLocation("B2 test origin")
                    .destination("B2 test destination")
                    .routeData(List.of(Map.of("lat", 0.0, "lng", 0.0)))
                    .tripStatus(Trip.TripStatus.ARRIVED)
                    .build());
            String key = "proof/" + UUID.randomUUID() + "/old-proof.jpg";
            ProofOfDelivery seededProof = em.merge(ProofOfDelivery.builder()
                    .trip(seededTrip)
                    .booking(booking)
                    .freight(freight)
                    .uploadedBy(seededDriver)
                    .storageKey(key)
                    .fileName("old-proof.jpg")
                    .contentType("image/jpeg")
                    .fileSize((long) JPEG.length)
                    .status(ProofOfDelivery.ProofStatus.REJECTED)
                    .uploadedAt(java.time.LocalDateTime.now())
                    .build());
            em.flush();
            return new Scenario(seededDriver, seededShipper, seededTrip, seededProof, key);
        });
        driver = scenario.driver();
        shipper = scenario.shipper();
        trip = scenario.trip();
        proof = scenario.proof();
        oldKey = scenario.oldKey();
        recordingStorage.recordOwnedKey(oldKey);
        try {
            b2Storage.put(oldKey, validJpeg("old-proof.jpg"));
        } catch (IOException e) {
            throw new IllegalStateException("Unable to seed test-owned B2 object", e);
        }
        assertObjectPresent(oldKey);
        when(tripRepository.findById(trip.getId())).thenReturn(java.util.Optional.of(trip));
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
                b2Storage.get(objectKey, "test.jpg", "image/jpeg");
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

        proofService.submitProof(trip.getId(), validJpeg("replacement.jpg"), "Receiver", "Delivered", driver);

        String replacementKey = recordingStorage.lastPutKey();
        assertThat(replacementKey).isNotBlank().isNotEqualTo(oldKey);
        assertThat(proofStorageKey()).isEqualTo(replacementKey);
        assertObjectAbsent(oldKey);
        assertObjectPresent(replacementKey);
    }

    @Test
    void forcedRollbackRetainsOldObjectAndDeletesReplacement() {
        assertSpringTransactionSetup();
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> transactions.execute(status -> {
            proofService.submitProof(trip.getId(), validJpeg("replacement.jpg"), "Receiver", "Delivered", driver);
            throw new ForcedRollbackException();
        })).isInstanceOf(ForcedRollbackException.class);

        String replacementKey = recordingStorage.lastPutKey();
        assertThat(replacementKey).isNotBlank().isNotEqualTo(oldKey);
        assertThat(proofStorageKey()).isEqualTo(oldKey);
        assertObjectPresent(oldKey);
        assertObjectAbsent(replacementKey);
    }

    @Test
    void unauthorizedDownloadIsDeniedBeforeStorageAccess() {
        User other = User.builder().id(7002L).fullName("Unauthorized B2 proof reader")
                .phone("unused").password("unused").role(User.Role.DRIVER).build();
        recordingStorage.resetRequestCount();

        assertThatThrownBy(() -> proofService.downloadProof(proof.getId(), other))
                .isInstanceOf(com.ethioloadai.delivery.exception.ProofOfDeliveryException.AuthorizationException.class);
        assertThat(recordingStorage.requestCount()).isZero();
    }

    @Test
    void authorizedDriverDownloadReturnsTheUploadedBytes() throws IOException {
        var downloaded = proofService.downloadProof(proof.getId(), driver);

        assertThat(downloaded.resource().getInputStream().readAllBytes()).containsExactly(JPEG);
    }

    private void assertSpringTransactionSetup() {
        assertThat(AopUtils.isAopProxy(proofService)).isTrue();
        assertThat(transactionManager).isInstanceOf(JpaTransactionManager.class);
        assertThat(dataSource).isInstanceOf(JdbcDataSource.class);
    }

    private String proofStorageKey() {
        return new TransactionTemplate(transactionManager).execute(status ->
                proofRepository.findByTrip_Id(trip.getId()).orElseThrow().getStorageKey());
    }

    private MockMultipartFile validJpeg(String filename) {
        return new MockMultipartFile("file", filename, "image/jpeg", JPEG);
    }

    private void assertObjectAbsent(String objectKey) {
        assertThatThrownBy(() -> b2Storage.get(objectKey, "test.jpg", "image/jpeg"))
                .isInstanceOf(DocumentStorageObjectNotFoundException.class);
    }

    private void assertObjectPresent(String objectKey) {
        assertThat(b2Storage.get(objectKey, "test.jpg", "image/jpeg").resource()).isNotNull();
    }

    private record Scenario(User driver, User shipper, Trip trip, ProofOfDelivery proof, String oldKey) {}

    private static final class ForcedRollbackException extends RuntimeException {}

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = ProofOfDeliveryRepository.class)
    @Import(ProofOfDeliveryServiceImpl.class)
    static class TestConfiguration {

        @Bean
        DataSource dataSource() {
            JdbcDataSource dataSource = new JdbcDataSource();
            dataSource.setURL("jdbc:h2:mem:real_b2_proof_tx_" + UUID.randomUUID().toString().replace("-", "")
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
                    ProofOfDelivery.class.getPackageName(),
                    Trip.class.getPackageName(),
                    TripStop.class.getPackageName(),
                    Booking.class.getPackageName(),
                    Freight.class.getPackageName(),
                    User.class.getPackageName(),
                    Vehicle.class.getPackageName(),
                    Payment.class.getPackageName(),
                    Rating.class.getPackageName(),
                    com.ethioloadai.bid.entity.Bid.class.getPackageName());
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
        EntityManager entityManager(EntityManagerFactory entityManagerFactory) {
            return SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory);
        }

        @Bean
        TripRepository tripRepository() {
            return mock(TripRepository.class);
        }

        @Bean
        TripLifecycleService tripLifecycleService() {
            return mock(TripLifecycleService.class);
        }

        @Bean
        SettlementOutboxService settlementOutboxService() {
            return mock(SettlementOutboxService.class);
        }

        @Bean
        NotificationStoreService notificationStoreService() {
            return mock(NotificationStoreService.class);
        }

        @Bean
        CompanySecurityService companySecurityService() {
            return mock(CompanySecurityService.class);
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
