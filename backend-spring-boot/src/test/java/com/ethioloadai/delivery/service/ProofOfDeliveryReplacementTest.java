package com.ethioloadai.delivery.service;

import com.ethioloadai.booking.entity.Booking;
import com.ethioloadai.carrier.team.service.CompanySecurityService;
import com.ethioloadai.delivery.dto.ProofOfDeliveryResponse;
import com.ethioloadai.delivery.dto.VerifyDeliveryRequest;
import com.ethioloadai.delivery.entity.ProofOfDelivery;
import com.ethioloadai.delivery.entity.ProofOfDelivery.ProofStatus;
import com.ethioloadai.delivery.exception.ProofOfDeliveryException;
import com.ethioloadai.delivery.repository.ProofOfDeliveryRepository;
import com.ethioloadai.document.storage.DocumentStorage;
import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.notification.service.NotificationStoreService;
import com.ethioloadai.settlement.service.SettlementOutboxService;
import com.ethioloadai.trip.entity.Trip;
import com.ethioloadai.trip.repository.TripRepository;
import com.ethioloadai.trip.service.TripLifecycleService;
import com.ethioloadai.user.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProofOfDeliveryReplacementTest {

    private static final Long TRIP_ID = 41L;
    private static final String OLD_KEY = "proof/41/old/proof.jpg";

    @Mock private ProofOfDeliveryRepository proofRepo;
    @Mock private TripRepository tripRepository;
    @Mock private TripLifecycleService tripLifecycleService;
    @Mock private SettlementOutboxService settlementOutboxService;
    @Mock private DocumentStorage documentStorage;
    @Mock private NotificationStoreService notificationService;
    @Mock private CompanySecurityService companySecurityService;

    private ProofOfDeliveryServiceImpl service;
    private Trip trip;
    private User driver;
    private ProofOfDelivery existing;

    @BeforeEach
    void setUp() {
        service = new ProofOfDeliveryServiceImpl(
                proofRepo, tripRepository, tripLifecycleService, settlementOutboxService,
                documentStorage, notificationService, companySecurityService);

        User shipper = User.builder().id(7L).fullName("Shipper").phone("shipper").password("unused")
                .role(User.Role.SHIPPER).build();
        driver = User.builder().id(8L).fullName("Driver").phone("driver").password("unused")
                .role(User.Role.DRIVER).build();
        Freight freight = Freight.builder().id(9L).shipper(shipper).build();
        Booking booking = Booking.builder().id(10L).cargoRequest(freight).build();
        trip = Trip.builder().id(TRIP_ID).booking(booking).driver(driver)
                .tripStatus(Trip.TripStatus.ARRIVED).build();
        existing = ProofOfDelivery.builder().id(11L).trip(trip).booking(booking).freight(freight)
                .uploadedBy(driver).storageKey(OLD_KEY).fileName("proof.jpg").contentType("image/jpeg")
                .fileSize(100L).status(ProofStatus.SUBMITTED).build();

        lenient().when(tripRepository.findById(TRIP_ID)).thenReturn(Optional.of(trip));
        lenient().when(proofRepo.findByTrip_IdForUpdate(TRIP_ID)).thenReturn(Optional.of(existing));
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void validSubmittedProofReplacementStoresNewProofBeforeReplacingRowAndDeletesOldObjectAfterCommit()
            throws Exception {
        stubSaveAndFlush();
        MockMultipartFile file = validFile();

        ProofOfDeliveryResponse response = service.submitProof(TRIP_ID, file, "Receiver", "Delivered", driver);

        assertThat(response.status()).isEqualTo("SUBMITTED");
        verify(proofRepo).findByTrip_IdForUpdate(TRIP_ID);
        InOrder order = inOrder(documentStorage, proofRepo);
        order.verify(documentStorage).put(anyString(), eq(file));
        order.verify(proofRepo).delete(existing);
        order.verify(proofRepo).flush();
        order.verify(proofRepo).saveAndFlush(any(ProofOfDelivery.class));
        verify(documentStorage, never()).delete(OLD_KEY);

        completeTransaction(TransactionSynchronization.STATUS_COMMITTED);
        verify(documentStorage).delete(OLD_KEY);
    }

    @Test
    void validRejectedProofReplacementRemainsSupported() throws Exception {
        stubSaveAndFlush();
        existing.setStatus(ProofStatus.REJECTED);

        ProofOfDeliveryResponse response = service.submitProof(TRIP_ID, validFile(), null, null, driver);

        assertThat(response.status()).isEqualTo("SUBMITTED");
        verify(proofRepo).delete(existing);
        verify(proofRepo).saveAndFlush(any(ProofOfDelivery.class));
        completeTransaction(TransactionSynchronization.STATUS_COMMITTED);
        verify(documentStorage).delete(OLD_KEY);
    }

    @Test
    void recipientNameAt150CharactersIsAccepted() throws Exception {
        stubSaveAndFlush();

        service.submitProof(TRIP_ID, validFile(), "R".repeat(150), null, driver);

        verify(documentStorage).put(anyString(), any());
        verify(proofRepo).saveAndFlush(argThat(proof ->
                proof.getRecipientName().length() == 150));
    }

    @Test
    void recipientNameAt151CharactersIsRejectedBeforeStorageOrPersistence() {
        assertThatThrownBy(() -> service.submitProof(TRIP_ID, validFile(), "R".repeat(151), null, driver))
                .isInstanceOf(ProofOfDeliveryException.MetadataValidationException.class)
                .hasMessageContaining("150 characters");

        verifyNoInteractions(documentStorage);
        verify(proofRepo, never()).delete(any(ProofOfDelivery.class));
        verify(proofRepo, never()).saveAndFlush(any(ProofOfDelivery.class));
    }

    @Test
    void noteAt1000CharactersIsAccepted() throws Exception {
        stubSaveAndFlush();

        service.submitProof(TRIP_ID, validFile(), null, "N".repeat(1000), driver);

        verify(documentStorage).put(anyString(), any());
        verify(proofRepo).saveAndFlush(argThat(proof -> proof.getNote().length() == 1000));
    }

    @Test
    void noteAt1001CharactersIsRejectedBeforeStorageOrPersistence() {
        assertThatThrownBy(() -> service.submitProof(TRIP_ID, validFile(), null, "N".repeat(1001), driver))
                .isInstanceOf(ProofOfDeliveryException.MetadataValidationException.class)
                .hasMessageContaining("1000 characters");

        verifyNoInteractions(documentStorage);
        verify(proofRepo, never()).delete(any(ProofOfDelivery.class));
        verify(proofRepo, never()).saveAndFlush(any(ProofOfDelivery.class));
    }

    @ParameterizedTest(name = "accepts valid signature for {0}")
    @MethodSource("validSignatures")
    void acceptsValidSignaturesForAllSupportedContentTypes(String contentType, byte[] signature)
            throws Exception {
        stubSaveAndFlush();
        MockMultipartFile file = new MockMultipartFile("file", "replacement", contentType, signature);

        ProofOfDeliveryResponse response = service.submitProof(TRIP_ID, file, null, null, driver);

        assertThat(response.status()).isEqualTo("SUBMITTED");
        verify(documentStorage).put(anyString(), eq(file));
        completeTransaction(TransactionSynchronization.STATUS_COMMITTED);
        verify(documentStorage).delete(OLD_KEY);
    }

    @ParameterizedTest(name = "rejects mismatched signature for {0}")
    @MethodSource("mismatchedSignatures")
    void rejectsMimeSignatureMismatchesForAllSupportedContentTypes(String contentType, byte[] signature) {
        MockMultipartFile file = new MockMultipartFile("file", "replacement", contentType, signature);

        assertThatThrownBy(() -> service.submitProof(TRIP_ID, file, null, null, driver))
                .isInstanceOf(ProofOfDeliveryException.FileValidationException.class)
                .hasMessageContaining("does not match");

        verify(proofRepo, never()).delete(any(ProofOfDelivery.class));
        verify(proofRepo, never()).saveAndFlush(any(ProofOfDelivery.class));
        verifyNoInteractions(documentStorage);
        assertThat(existing.getStorageKey()).isEqualTo(OLD_KEY);
    }

    @Test
    void invalidMimeDoesNotMutateOrDeleteExistingProof() {
        MockMultipartFile file = new MockMultipartFile("file", "proof.exe", "application/octet-stream",
                new byte[]{1, 2, 3});

        assertThatThrownBy(() -> service.submitProof(TRIP_ID, file, null, null, driver))
                .isInstanceOf(ProofOfDeliveryException.FileValidationException.class);

        verify(proofRepo, never()).delete(any(ProofOfDelivery.class));
        verify(proofRepo, never()).flush();
        verifyNoInteractions(documentStorage);
        assertThat(existing.getStorageKey()).isEqualTo(OLD_KEY);
    }

    @Test
    void oversizedReplacementDoesNotMutateOrDeleteExistingProof() {
        var file = mock(org.springframework.web.multipart.MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(10L * 1024 * 1024 + 1);

        assertThatThrownBy(() -> service.submitProof(TRIP_ID, file, null, null, driver))
                .isInstanceOf(ProofOfDeliveryException.FileValidationException.class);

        verify(proofRepo, never()).delete(any(ProofOfDelivery.class));
        verifyNoInteractions(documentStorage);
        assertThat(existing.getStorageKey()).isEqualTo(OLD_KEY);
    }

    @Test
    void newObjectStorageFailureLeavesExistingProofUntouched() throws Exception {
        doThrow(new IOException("storage unavailable")).when(documentStorage).put(anyString(), any());

        assertThatThrownBy(() -> service.submitProof(TRIP_ID, validFile(), null, null, driver))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to store proof of delivery file");

        verify(proofRepo, never()).delete(any(ProofOfDelivery.class));
        verify(documentStorage, never()).delete(OLD_KEY);
        assertThat(existing.getStorageKey()).isEqualTo(OLD_KEY);

        completeTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);
        verify(documentStorage).delete(argThat(key -> key.startsWith("proof/" + TRIP_ID + "/")));
        verify(documentStorage, never()).delete(OLD_KEY);
    }

    @Test
    void replacementPersistenceFailureDoesNotDeleteOldObject() throws Exception {
        doThrow(new DataIntegrityViolationException("database unavailable"))
                .when(proofRepo).saveAndFlush(any(ProofOfDelivery.class));

        assertThatThrownBy(() -> service.submitProof(TRIP_ID, validFile(), null, null, driver))
                .isInstanceOf(DataIntegrityViolationException.class);

        verify(proofRepo).delete(existing);
        verify(proofRepo).flush();
        verify(documentStorage, never()).delete(OLD_KEY);
        assertThat(existing.getStorageKey()).isEqualTo(OLD_KEY);

        completeTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);
        verify(documentStorage).delete(argThat(key -> key.startsWith("proof/" + TRIP_ID + "/")));
        verify(documentStorage, never()).delete(OLD_KEY);
    }

    @Test
    void definiteRollbackDeletesNewObjectAndRetainsOldObject() throws Exception {
        stubSaveAndFlush();

        service.submitProof(TRIP_ID, validFile(), null, null, driver);
        completeTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(documentStorage).delete(argThat(key -> key.startsWith("proof/" + TRIP_ID + "/")));
        verify(documentStorage, never()).delete(OLD_KEY);
        assertThat(existing.getStorageKey()).isEqualTo(OLD_KEY);
    }

    @Test
    void unknownTransactionOutcomeRetainsBothObjectsForReconciliation() throws Exception {
        stubSaveAndFlush();

        service.submitProof(TRIP_ID, validFile(), null, null, driver);
        completeTransaction(TransactionSynchronization.STATUS_UNKNOWN);

        verify(documentStorage, never()).delete(anyString());
        assertThat(existing.getStorageKey()).isEqualTo(OLD_KEY);
    }

    @Test
    void verifiedProofCannotBeReplaced() {
        existing.setStatus(ProofStatus.VERIFIED);

        assertThatThrownBy(() -> service.submitProof(TRIP_ID, validFile(), null, null, driver))
                .isInstanceOf(ProofOfDeliveryException.InvalidStateException.class);

        verify(proofRepo).findByTrip_IdForUpdate(TRIP_ID);
        verify(proofRepo, never()).delete(any(ProofOfDelivery.class));
        verifyNoInteractions(documentStorage);
        assertThat(existing.getStorageKey()).isEqualTo(OLD_KEY);
    }

    @Test
    void anotherDriverCannotReplaceProof() {
        User otherDriver = User.builder().id(99L).fullName("Other").phone("other").password("unused")
                .role(User.Role.DRIVER).build();

        assertThatThrownBy(() -> service.submitProof(TRIP_ID, validFile(), null, null, otherDriver))
                .isInstanceOf(ProofOfDeliveryException.AuthorizationException.class);

        verifyNoInteractions(proofRepo, documentStorage);
        assertThat(existing.getStorageKey()).isEqualTo(OLD_KEY);
    }

    @Test
    void verificationUsesLockedProofRead() {
        when(proofRepo.findByIdForUpdate(11L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.verifyOrRejectProof(11L,
                new VerifyDeliveryRequest("INVALID", null),
                existing.getFreight().getShipper()))
                .isInstanceOf(ProofOfDeliveryException.InvalidStateException.class);

        verify(proofRepo).findByIdForUpdate(11L);
    }

    private MockMultipartFile validFile() {
        return new MockMultipartFile("file", "replacement.jpg", "image/jpeg",
                new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff});
    }

    private static Stream<Arguments> validSignatures() {
        return Stream.of(
                Arguments.of("image/jpeg", new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff}),
                Arguments.of("image/jpg", new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff}),
                Arguments.of("image/png", new byte[]{
                        (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a}),
                Arguments.of("image/webp", new byte[]{
                        'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'}),
                Arguments.of("application/pdf", new byte[]{'%', 'P', 'D', 'F', '-'})
        );
    }

    private static Stream<Arguments> mismatchedSignatures() {
        return Stream.of(
                Arguments.of("image/jpeg", new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a}),
                Arguments.of("image/jpg", new byte[]{
                        'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'}),
                Arguments.of("image/png", new byte[]{'%', 'P', 'D', 'F', '-'}),
                Arguments.of("image/webp", new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff}),
                Arguments.of("application/pdf", new byte[]{
                        (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a})
        );
    }

    private void stubSaveAndFlush() {
        when(proofRepo.saveAndFlush(any(ProofOfDelivery.class))).thenAnswer(invocation -> {
            ProofOfDelivery proof = invocation.getArgument(0);
            proof.setId(12L);
            return proof;
        });
    }

    private void completeTransaction(int status) {
        List<TransactionSynchronization> synchronizations =
                TransactionSynchronizationManager.getSynchronizations();
        if (status == TransactionSynchronization.STATUS_COMMITTED) {
            synchronizations.forEach(TransactionSynchronization::afterCommit);
        }
        synchronizations.forEach(sync -> sync.afterCompletion(status));
        TransactionSynchronizationManager.clearSynchronization();
    }
}
