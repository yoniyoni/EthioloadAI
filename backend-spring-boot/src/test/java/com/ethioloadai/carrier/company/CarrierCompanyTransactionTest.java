package com.ethioloadai.carrier.company;

import com.ethioloadai.carrier.company.dto.CarrierCompanyRequest;
import com.ethioloadai.carrier.company.entity.*;
import com.ethioloadai.carrier.company.repository.*;
import com.ethioloadai.carrier.company.service.CarrierCompanyService;
import com.ethioloadai.document.storage.DocumentStorage;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.user.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CarrierCompanyTransactionTest {
    @Autowired CarrierCompanyService service;
    @Autowired CarrierCompanyRepository companies;
    @Autowired CarrierCompanyDocumentRepository documents;
    @Autowired UserRepository users;
    @Autowired PlatformTransactionManager transactions;
    @MockBean DocumentStorage storage;
    User owner, admin;
    Long companyId;

    @BeforeEach void setup() {
        owner = createUser(User.Role.SHIPPER);
        admin = createUser(User.Role.ADMIN);
        var request = new CarrierCompanyRequest();
        request.setCompanyName("Transaction test");
        request.setTinNumber("T-" + UUID.randomUUID());
        request.setBusinessRegistrationNumber("R-" + UUID.randomUUID());
        request.setPhone("+251911223344");
        request.setEmail("test@example.com");
        request.setBusinessAddress("Address");
        request.setCity("City");
        companyId = service.create(request, owner).getId();
    }

    @AfterEach void cleanup() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            documents.deleteAll(documents.findByCarrierCompanyIdOrderById(companyId));
            documents.flush();
            companies.deleteById(companyId);
            companies.flush();
            users.deleteAllById(List.of(owner.getId(), admin.getId()));
        });
    }

    User createUser(User.Role role) {
        return users.saveAndFlush(User.builder().fullName("Transaction test").phone(UUID.randomUUID().toString())
            .password("unused").role(role).build());
    }

    void upload(CompanyDocumentType type) {
        service.upload(companyId, type, new MockMultipartFile("file", "business.pdf", "application/pdf",
            "%PDF-1.4 business".getBytes(java.nio.charset.StandardCharsets.UTF_8)), owner);
    }

    @Test void committedReplacementDeletesOnlyOldObject() {
        upload(CompanyDocumentType.BUSINESS_LICENSE);
        String oldKey = documents.findByCarrierCompanyIdOrderById(companyId).getFirst().getStorageKey();
        upload(CompanyDocumentType.BUSINESS_LICENSE);
        String newKey = documents.findByCarrierCompanyIdOrderById(companyId).getFirst().getStorageKey();
        assertThat(newKey).isNotEqualTo(oldKey);
        verify(storage).delete(oldKey);
        verify(storage, never()).delete(newKey);
    }

    @Test void outerTransactionRollbackKeepsPreviousDocumentAndDeletesNewObject() throws Exception {
        upload(CompanyDocumentType.BUSINESS_LICENSE);
        var original = documents.findByCarrierCompanyIdOrderById(companyId).getFirst();
        clearInvocations(storage);
        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
            upload(CompanyDocumentType.BUSINESS_LICENSE);
            throw new IllegalStateException("simulated failure after upload");
        })).hasMessage("simulated failure after upload");
        assertThat(documents.findById(original.getId()).orElseThrow().getStorageKey()).isEqualTo(original.getStorageKey());
        var key = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(storage).put(key.capture(), any());
        verify(storage).delete(key.getValue());
        verify(storage, never()).delete(original.getStorageKey());
    }

    @Test void storageFailureRollsBackDocumentMetadata() throws Exception {
        doThrow(new IOException("storage offline")).when(storage).put(any(), any());
        assertThatThrownBy(() -> upload(CompanyDocumentType.BUSINESS_LICENSE))
            .hasMessage("Unable to store company document");
        assertThat(documents.findByCarrierCompanyIdOrderById(companyId)).isEmpty();
        verify(storage, atLeastOnce()).delete(startsWith("carrier-companies/" + companyId + "/"));
    }

    @Test void concurrentAdminDecisionsHaveExactlyOneWinner() throws Exception {
        for (var type : List.of(CompanyDocumentType.BUSINESS_LICENSE, CompanyDocumentType.BUSINESS_REGISTRATION,
                CompanyDocumentType.TIN_CERTIFICATE)) upload(type);
        service.submit(companyId, owner);
        try (var executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Boolean>> results = new ArrayList<>();
            for (var decision : List.of(VerificationStatus.APPROVED, VerificationStatus.REJECTED)) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("start timed out");
                    try {
                        service.review(companyId, decision, "Reviewed", admin);
                        return true;
                    } catch (com.ethioloadai.carrier.company.exception.CarrierCompanyException ex) {
                        assertThat(ex.getCode()).isEqualTo("INVALID_CARRIER_APPLICATION");
                        return false;
                    }
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            int wins = 0;
            for (var result : results) if (result.get(20, TimeUnit.SECONDS)) wins++;
            assertThat(wins).isEqualTo(1);
        }
        var result = companies.findById(companyId).orElseThrow();
        assertThat(documents.findByCarrierCompanyIdOrderById(companyId))
            .allMatch(d -> d.getVerificationStatus() == result.getVerificationStatus());
    }

    @Test void concurrentRegistrationIsProtectedByDatabaseUniqueness() throws Exception {
        var request = new CarrierCompanyRequest();
        request.setCompanyName("Concurrent company");
        request.setTinNumber("UNIQUE-" + UUID.randomUUID());
        try (var executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Long>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                results.add(executor.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    try { return service.create(request, owner).getId(); }
                    catch (com.ethioloadai.carrier.company.exception.CarrierCompanyException ex) {
                        assertThat(ex.getCode()).isEqualTo("DUPLICATE_CARRIER_REGISTRATION");
                        return null;
                    }
                }));
            }
            start.countDown();
            List<Long> winners = new ArrayList<>();
            for (var result : results) {
                Long id = result.get(20, TimeUnit.SECONDS);
                if (id != null) winners.add(id);
            }
            try { assertThat(winners).hasSize(1); }
            finally { companies.deleteAllById(winners); }
        }
    }
}

