package com.ethioloadai.carrier.company;

import com.ethioloadai.carrier.company.dto.*;
import com.ethioloadai.carrier.company.entity.*;
import com.ethioloadai.carrier.company.repository.*;
import com.ethioloadai.carrier.company.service.CarrierCompanyService;
import com.ethioloadai.document.storage.DocumentStorage;
import com.ethioloadai.security.jwt.JwtService;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.user.repository.UserRepository;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CarrierCompanyIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired JwtService jwt;
    @Autowired CarrierCompanyService service;
    @Autowired CarrierCompanyRepository companies;
    @Autowired CarrierCompanyDocumentRepository documents;
    @MockBean DocumentStorage storage;
    User owner, other, admin;
    static final String ROOT = "/carrier-companies";
    static final String ADMIN = "/admin/carrier-companies";
    static final byte[] PDF = "%PDF-1.4 test company document".getBytes(java.nio.charset.StandardCharsets.UTF_8);

    @BeforeEach void setUp() {
        owner = user(User.Role.SHIPPER);
        other = user(User.Role.FLEET_OWNER);
        admin = user(User.Role.ADMIN);
    }

    User user(User.Role role) {
        return users.saveAndFlush(User.builder().fullName("Carrier test").phone(UUID.randomUUID().toString())
            .email(UUID.randomUUID() + "@example.com").password("unused-test-password").role(role).isActive(true).build());
    }

    String auth(User user) { return "Bearer " + jwt.generateToken(user); }
    CarrierCompanyRequest request() {
        CarrierCompanyRequest request = new CarrierCompanyRequest();
        request.setCompanyName("Abay Transport");
        request.setLegalName("Abay Transport PLC");
        request.setTinNumber("TIN-" + UUID.randomUUID());
        request.setBusinessRegistrationNumber("REG-" + UUID.randomUUID());
        request.setPhone("+251911223344");
        request.setEmail("office@example.com");
        request.setBusinessAddress("Bole Road");
        request.setCity("Addis Ababa");
        return request;
    }
    CarrierCompanyResponse draft() { return service.create(request(), owner); }
    CarrierCompanyResponse complete() {
        CarrierCompanyResponse company = draft();
        for (CompanyDocumentType type : List.of(CompanyDocumentType.BUSINESS_LICENSE,
                CompanyDocumentType.BUSINESS_REGISTRATION, CompanyDocumentType.TIN_CERTIFICATE)) {
            service.upload(company.getId(), type, file(PDF, "application/pdf"), owner);
        }
        return company;
    }
    CarrierCompanyResponse pending() {
        return service.submit(complete().getId(), owner);
    }
    MockMultipartFile file(byte[] bytes, String type) {
        return new MockMultipartFile("file", "../../private.pdf", type, bytes);
    }
    JsonNode body(MvcResult result) throws Exception { return json.readTree(result.getResponse().getContentAsString()); }

    @Test void authenticatedCreationUsesServerOwnershipAndIgnoresPrivilegedFields() throws Exception {
        var payload = json.valueToTree(request());
        ((com.fasterxml.jackson.databind.node.ObjectNode) payload).put("ownerId", other.getId())
            .put("verificationStatus", "APPROVED").put("reviewedBy", admin.getId());
        mvc.perform(post(ROOT).header("Authorization", auth(owner)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(payload)))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.ownerId").value(owner.getId()))
            .andExpect(jsonPath("$.verificationStatus").value("DRAFT")).andExpect(jsonPath("$.reviewedBy").isEmpty());
    }

    @Test void unauthenticatedCreateIs401() throws Exception {
        mvc.perform(post(ROOT).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request())))
            .andExpect(status().isUnauthorized());
        mvc.perform(get(ADMIN)).andExpect(status().isUnauthorized());
    }

    @Test void ownerReadsOnlyTheirCompaniesAndMayOwnMultiple() throws Exception {
        var first = draft();
        draft();
        service.create(request(), other);
        mvc.perform(get(ROOT + "/me").header("Authorization", auth(owner)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get(ROOT + "/" + first.getId()).header("Authorization", auth(owner)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.companyName").value("Abay Transport"));
    }

    @Test void unrelatedOwnerCannotReadUpdateSubmitUploadListOrDownload() throws Exception {
        var company = complete();
        long id = company.getId();
        long documentId = documents.findByCarrierCompanyIdOrderById(id).getFirst().getId();
        String token = auth(other);
        mvc.perform(get(ROOT + "/" + id).header("Authorization", token)).andExpect(status().isNotFound());
        mvc.perform(put(ROOT + "/" + id).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(request()))).andExpect(status().isNotFound());
        mvc.perform(post(ROOT + "/" + id + "/submit").header("Authorization", token)).andExpect(status().isNotFound());
        mvc.perform(multipart(ROOT + "/" + id + "/documents").file(file(PDF, "application/pdf"))
            .param("documentType", "OTHER").header("Authorization", token)).andExpect(status().isNotFound());
        mvc.perform(get(ROOT + "/" + id + "/documents").header("Authorization", token)).andExpect(status().isNotFound());
        mvc.perform(get(ROOT + "/" + id + "/documents/" + documentId + "/file").header("Authorization", token))
            .andExpect(status().isNotFound());
        verify(storage, never()).get(any(), any(), any());
    }

    @Test void ownerUpdatesDraftWithoutChangingOwnershipOrStatus() throws Exception {
        var company = draft();
        var request = request();
        request.setCompanyName("Updated");
        mvc.perform(put(ROOT + "/" + company.getId()).header("Authorization", auth(owner))
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.companyName").value("Updated"))
            .andExpect(jsonPath("$.verificationStatus").value("DRAFT"))
            .andExpect(jsonPath("$.ownerId").value(owner.getId()));
    }

    @Test void completeApplicationSubmitsAndDocumentsBecomePending() throws Exception {
        var company = complete();
        mvc.perform(post(ROOT + "/" + company.getId() + "/submit").header("Authorization", auth(owner)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.verificationStatus").value("PENDING"))
            .andExpect(jsonPath("$.submittedAt").isNotEmpty());
        assertThat(documents.findByCarrierCompanyIdOrderById(company.getId()))
            .allMatch(d -> d.getVerificationStatus() == VerificationStatus.PENDING);
    }

    @Test void incompleteDraftMayBeSavedButCannotSubmit() throws Exception {
        mvc.perform(post(ROOT).header("Authorization", auth(owner)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"companyName\":\"Draft only\"}")).andExpect(status().isCreated());
        var request = request();
        request.setCity(null);
        var company = service.create(request, owner);
        mvc.perform(post(ROOT + "/" + company.getId() + "/submit").header("Authorization", auth(owner)))
            .andExpect(status().isBadRequest());
        assertThat(companies.findById(company.getId()).orElseThrow().getVerificationStatus()).isEqualTo(VerificationStatus.DRAFT);
    }

    @Test void missingRequiredDocumentsPreventSubmission() throws Exception {
        var company = draft();
        service.upload(company.getId(), CompanyDocumentType.OTHER, file(PDF, "application/pdf"), owner);
        mvc.perform(post(ROOT + "/" + company.getId() + "/submit").header("Authorization", auth(owner)))
            .andExpect(status().isBadRequest());
    }

    @ParameterizedTest @ValueSource(strings = {"approve", "reject", "request-information"})
    void ordinaryUsersCannotReview(String action) throws Exception {
        var company = pending();
        mvc.perform(post(ADMIN + "/" + company.getId() + "/" + action).header("Authorization", auth(owner))
            .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"review\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(get(ADMIN).header("Authorization", auth(other))).andExpect(status().isForbidden());
        mvc.perform(get(ADMIN + "/" + company.getId()).header("Authorization", auth(other))).andExpect(status().isForbidden());
    }

    @Test void adminApprovesPendingWithServerReviewerAndDocuments() throws Exception {
        var company = pending();
        mvc.perform(post(ADMIN + "/" + company.getId() + "/approve").header("Authorization", auth(admin))
            .contentType(MediaType.APPLICATION_JSON).content("{\"reviewedBy\":" + other.getId() + "}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.verificationStatus").value("APPROVED"))
            .andExpect(jsonPath("$.reviewedBy").value(admin.getId())).andExpect(jsonPath("$.reviewedAt").isNotEmpty());
        assertThat(documents.findByCarrierCompanyIdOrderById(company.getId()))
            .allMatch(d -> d.getVerificationStatus() == VerificationStatus.APPROVED && d.getReviewedBy().equals(admin.getId()));
    }

    @Test void adminRejectsAndOwnerSeesReason() throws Exception {
        var company = pending();
        mvc.perform(post(ADMIN + "/" + company.getId() + "/reject").header("Authorization", auth(admin))
            .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Registration is invalid\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.verificationStatus").value("REJECTED"));
        mvc.perform(get(ROOT + "/" + company.getId()).header("Authorization", auth(owner)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.reviewMessage").value("Registration is invalid"));
        mvc.perform(post(ROOT + "/" + company.getId() + "/submit").header("Authorization", auth(owner)))
            .andExpect(status().isBadRequest());
    }

    @ParameterizedTest @ValueSource(strings = {"reject", "request-information"})
    void negativeDecisionRequiresReason(String action) throws Exception {
        var company = pending();
        mvc.perform(post(ADMIN + "/" + company.getId() + "/" + action).header("Authorization", auth(admin))
            .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"   \"}"))
            .andExpect(status().isUnprocessableEntity());
        assertThat(companies.findById(company.getId()).orElseThrow().getVerificationStatus()).isEqualTo(VerificationStatus.PENDING);
    }

    @Test void requestInformationAllowsCorrectionReplacementAndResubmission() throws Exception {
        var company = pending();
        long id = company.getId();
        mvc.perform(post(ADMIN + "/" + id + "/request-information").header("Authorization", auth(admin))
            .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Upload a clearer license\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.verificationStatus").value("MORE_INFORMATION_REQUIRED"));
        mvc.perform(get(ROOT + "/" + id).header("Authorization", auth(owner)))
            .andExpect(jsonPath("$.reviewMessage").value("Upload a clearer license"));
        service.update(id, request(), owner);
        var old = documents.findByCarrierCompanyIdAndDocumentType(id, CompanyDocumentType.BUSINESS_LICENSE).orElseThrow();
        Long oldId = old.getId();
        String oldKey = old.getStorageKey();
        var replacement = service.upload(id, CompanyDocumentType.BUSINESS_LICENSE, file(PDF, "application/pdf"), owner);
        assertThat(replacement.getId()).isEqualTo(oldId);
        assertThat(documents.findByCarrierCompanyIdOrderById(id)).hasSize(3);
        verify(storage, never()).delete(oldKey); // old file survives until the database transaction commits
        mvc.perform(post(ROOT + "/" + id + "/submit").header("Authorization", auth(owner)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.verificationStatus").value("PENDING"))
            .andExpect(jsonPath("$.reviewMessage").isEmpty()).andExpect(jsonPath("$.reviewedBy").isEmpty());
    }

    @Test void adminCannotReviewOwnCompany() throws Exception {
        owner = admin;
        var company = pending();
        mvc.perform(post(ADMIN + "/" + company.getId() + "/approve").header("Authorization", auth(admin)))
            .andExpect(status().isForbidden());
    }

    @Test void pendingAndApprovedAreFrozenAndInvalidReviewsFail() throws Exception {
        var draft = draft();
        mvc.perform(post(ADMIN + "/" + draft.getId() + "/approve").header("Authorization", auth(admin)))
            .andExpect(status().isBadRequest());
        var company = pending();
        for (int step = 0; step < 2; step++) {
            mvc.perform(put(ROOT + "/" + company.getId()).header("Authorization", auth(owner))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request())))
                .andExpect(status().isBadRequest());
            mvc.perform(multipart(ROOT + "/" + company.getId() + "/documents").file(file(PDF, "application/pdf"))
                .param("documentType", "OTHER").header("Authorization", auth(owner))).andExpect(status().isBadRequest());
            mvc.perform(post(ROOT + "/" + company.getId() + "/submit").header("Authorization", auth(owner)))
                .andExpect(status().isBadRequest());
            if (step == 0) service.review(company.getId(), VerificationStatus.APPROVED, null, admin);
        }
        mvc.perform(post(ADMIN + "/" + company.getId() + "/approve").header("Authorization", auth(admin)))
            .andExpect(status().isBadRequest());
    }

    @Test void duplicateTinAndRegistrationAreCaseInsensitiveOnCreateAndUpdate() throws Exception {
        var company = draft();
        var duplicateTin = request();
        duplicateTin.setTinNumber(company.getTinNumber().toLowerCase(Locale.ROOT));
        mvc.perform(post(ROOT).header("Authorization", auth(other)).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(duplicateTin))).andExpect(status().isConflict());
        var duplicateRegistration = request();
        duplicateRegistration.setBusinessRegistrationNumber(company.getBusinessRegistrationNumber().toLowerCase(Locale.ROOT));
        mvc.perform(post(ROOT).header("Authorization", auth(other)).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(duplicateRegistration))).andExpect(status().isConflict());
        var second = draft();
        mvc.perform(put(ROOT + "/" + second.getId()).header("Authorization", auth(owner))
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(duplicateTin)))
            .andExpect(status().isConflict());
    }

    @Test void documentsAreScopedToCompanyAndDownloadedPrivately() throws Exception {
        var company = complete();
        var doc = documents.findByCarrierCompanyIdOrderById(company.getId()).getFirst();
        var another = draft();
        when(storage.get(doc.getStorageKey(), doc.getOriginalName(), doc.getContentType()))
            .thenReturn(new DocumentStorage.StoredDocument(new ByteArrayResource(PDF), "document.pdf", "application/pdf"));
        mvc.perform(get(ROOT + "/" + company.getId() + "/documents").header("Authorization", auth(owner)))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].carrierCompanyId").value(company.getId()))
            .andExpect(jsonPath("$[0].storageKey").doesNotExist());
        mvc.perform(get(ROOT + "/" + another.getId() + "/documents/" + doc.getId() + "/file")
            .header("Authorization", auth(owner))).andExpect(status().isNotFound());
        mvc.perform(get(ROOT + "/" + company.getId() + "/documents/" + doc.getId() + "/file")
            .header("Authorization", auth(owner))).andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store")).andExpect(content().bytes(PDF));
        mvc.perform(get(ROOT + "/" + company.getId() + "/documents/" + doc.getId() + "/file")
            .header("Authorization", auth(admin))).andExpect(status().isOk());
        mvc.perform(get(ROOT + "/" + company.getId() + "/documents/" + doc.getId() + "/file"))
            .andExpect(status().isUnauthorized());
    }

    @Test void uploadValidatesSignatureSizeAndTypeAndDoesNotAcceptStorageKeys() throws Exception {
        var company = draft();
        mvc.perform(multipart(ROOT + "/" + company.getId() + "/documents").file(file("bad".getBytes(), "application/pdf"))
            .param("documentType", "OTHER").header("Authorization", auth(owner))).andExpect(status().isBadRequest());
        mvc.perform(multipart(ROOT + "/" + company.getId() + "/documents").file(file(PDF, "text/html"))
            .param("documentType", "OTHER").header("Authorization", auth(owner))).andExpect(status().isBadRequest());
        mvc.perform(multipart(ROOT + "/" + company.getId() + "/documents").file(file(new byte[5242881], "application/pdf"))
            .param("documentType", "OTHER").header("Authorization", auth(owner))).andExpect(status().isBadRequest());
        verify(storage, never()).put(any(), any());
        mvc.perform(multipart(ROOT + "/" + company.getId() + "/documents").file(file(PDF, "application/pdf"))
            .param("documentType", "OTHER").param("storageKey", "driver-documents/another-user/private.pdf")
            .header("Authorization", auth(owner))).andExpect(status().isCreated())
            .andExpect(jsonPath("$.originalName").value("document.pdf"));
        verify(storage).put(startsWith("carrier-companies/" + company.getId() + "/"), any());
    }

    @Test void adminListsFiltersAndReadsApplications() throws Exception {
        var company = pending();
        draft();
        mvc.perform(get(ADMIN).param("status", "PENDING").header("Authorization", auth(admin)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].id").value(company.getId()));
        mvc.perform(get(ADMIN + "/" + company.getId()).header("Authorization", auth(admin)))
            .andExpect(status().isOk());
        mvc.perform(get(ADMIN).param("size", "101").header("Authorization", auth(admin)))
            .andExpect(status().isBadRequest());
    }

    @Test void fieldValidationUsesExistingErrorEnvelope() throws Exception {
        var request = request();
        request.setCompanyName(" ");
        request.setEmail("invalid");
        request.setPhone("abc");
        mvc.perform(post(ROOT).header("Authorization", auth(owner)).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(request))).andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test void missingCompanyIs404() throws Exception {
        mvc.perform(get(ROOT + "/9223372036854775807").header("Authorization", auth(owner)))
            .andExpect(status().isNotFound());
    }

    @Test void serviceAlsoEnforcesAdminAndAuthentication() {
        assertThatThrownBy(() -> service.create(request(), null))
            .hasMessage("Authentication required");
        assertThatThrownBy(() -> service.applications(null, 0, 20, owner))
            .hasMessage("Admin access required");
        var company = pending();
        assertThatThrownBy(() -> service.review(company.getId(), VerificationStatus.APPROVED, null, owner))
            .hasMessage("Admin access required");
    }
}

