package com.ethioloadai.auth.service;

import com.ethioloadai.auth.dto.AuthenticationResponse;
import com.ethioloadai.auth.dto.LoginRequest;
import com.ethioloadai.auth.dto.RegisterRequest;
import com.ethioloadai.auth.dto.UserResponse;
import com.ethioloadai.auth.mapper.AuthMapper;
import com.ethioloadai.exception.AuthenticationException;
import com.ethioloadai.exception.GlobalExceptionHandler;
import com.ethioloadai.exception.RegistrationConflictException;
import com.ethioloadai.security.jwt.JwtService;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthenticationAbuseProtectionTest {
    private UserRepository userRepository;
    private AuthMapper authMapper;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private LoginAttemptRateLimiter limiter;
    private AuthenticationServiceImpl authenticationService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        authMapper = mock(AuthMapper.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtService = mock(JwtService.class);
        limiter = limiter(5, 60, 30, 5, System::nanoTime);
        authenticationService = new AuthenticationServiceImpl(
                userRepository, authMapper, passwordEncoder, jwtService, limiter);
    }

    @Test
    void repeatedFailedLoginsAreLimitedBeforeAnotherPasswordCheck() {
        LoginRequest request = new LoginRequest("person@example.com", "wrong");
        User user = activeUser(42L, "encoded");
        when(userRepository.findByEmailOrPhone(request.getIdentifier())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(request.getPassword(), user.getPassword())).thenReturn(false);

        for (int attempt = 0; attempt < 5; attempt++) {
            assertThrows(AuthenticationException.InvalidCredentialsException.class,
                    () -> authenticationService.login(request, "192.0.2.10"));
        }
        AuthenticationException.TooManyLoginAttemptsException limited = assertThrows(
                AuthenticationException.TooManyLoginAttemptsException.class,
                () -> authenticationService.login(request, "192.0.2.10"));

        assertEquals(429, limited.getHttpStatus());
        verify(passwordEncoder, times(5)).matches(request.getPassword(), user.getPassword());
        verify(userRepository, times(6)).findByEmailOrPhone(request.getIdentifier());
    }

    @Test
    void identifierLimitPersistsAcrossIpRotationAndAccountAliases() {
        for (int attempt = 0; attempt < 5; attempt++) {
            String sourceIp = "192.0.2." + (attempt + 1);
            limiter.checkSource(sourceIp);
            limiter.checkIdentifier(attempt % 2 == 0 ? "account-email" : "+251900000001", 42L);
        }

        limiter.checkSource("192.0.2.6");
        assertThrows(AuthenticationException.TooManyLoginAttemptsException.class,
                () -> limiter.checkIdentifier("account-email", 42L));
    }

    @Test
    void sourceLimitAppliesAcrossDifferentIdentifiers() {
        for (int attempt = 0; attempt < 30; attempt++) {
            limiter.checkSource("198.51.100.20");
            limiter.checkIdentifier("user-" + attempt, null);
        }

        assertThrows(AuthenticationException.TooManyLoginAttemptsException.class,
                () -> limiter.checkSource("198.51.100.20"));
    }

    @Test
    void identifierNormalizationCannotBypassUnknownAccountLimit() {
        LoginAttemptRateLimiter isolated = limiter(2, 60, 30, 5, System::nanoTime);
        for (int attempt = 0; attempt < 2; attempt++) {
            isolated.checkSource("203.0.113." + attempt);
            isolated.checkIdentifier(
                    attempt == 0 ? " Person@Example.com " : "person@example.com", null);
        }

        isolated.checkSource("203.0.113.3");
        assertThrows(AuthenticationException.TooManyLoginAttemptsException.class,
                () -> isolated.checkIdentifier("PERSON@example.com", null));
    }

    @Test
    void successfulLoginClearsIdentityFailuresAndRefundsItsSourceToken() {
        LoginAttemptRateLimiter isolated = limiter(2, 60, 2, 60, System::nanoTime);
        isolated.checkSource("192.0.2.50");
        isolated.checkIdentifier("same@example.com", 50L);
        isolated.checkSource("192.0.2.50");
        isolated.checkIdentifier("same@example.com", 50L);

        isolated.recordSuccess("same@example.com", 50L, "192.0.2.50");
        assertDoesNotThrow(() -> isolated.checkSource("192.0.2.50"));
        assertDoesNotThrow(() -> isolated.checkIdentifier("same@example.com", 50L));
    }

    @Test
    void tokenBucketsRefillWithoutFixedWindowBoundaryBurst() {
        MutableNanoClock clock = new MutableNanoClock();
        LoginAttemptRateLimiter isolated = limiter(1, 60, 1, 60, clock);
        isolated.checkIdentifier("person@example.com", null);

        assertThrows(AuthenticationException.TooManyLoginAttemptsException.class,
                () -> isolated.checkIdentifier("person@example.com", null));
        clock.advance(Duration.ofSeconds(30));
        assertThrows(AuthenticationException.TooManyLoginAttemptsException.class,
                () -> isolated.checkIdentifier("person@example.com", null));
        clock.advance(Duration.ofSeconds(30));
        assertDoesNotThrow(() -> isolated.checkIdentifier("person@example.com", null));
    }

    @Test
    void registrationIsLimitedBySourceAddress() {
        LoginAttemptRateLimiter isolated = registrationLimiter(2, 60, 30, 5, 2, 3600, System::nanoTime);

        isolated.checkRegistration("198.51.100.10", "first@example.com", "+251911000001");
        isolated.checkRegistration("198.51.100.10", "second@example.com", "+251911000002");
        AuthenticationException.TooManyRegistrationAttemptsException limited = assertThrows(
                AuthenticationException.TooManyRegistrationAttemptsException.class,
                () -> isolated.checkRegistration("198.51.100.10", "third@example.com", "+251911000003"));

        assertEquals(429, limited.getHttpStatus());
        assertEquals(429, new GlobalExceptionHandler().handleBaseException(limited).getStatusCode().value());
    }

    @Test
    void repeatedRegistrationAttemptsAreStoppedBeforePersistence() {
        RegisterRequest request = new RegisterRequest(
                "Abebe Girma", "+251911223344", "abebe@example.com", "securePass1", "shipper");
        User candidate = User.builder().phone(request.getPhone()).build();
        when(authMapper.toEntity(request)).thenReturn(candidate);
        when(passwordEncoder.encode(request.getPassword())).thenReturn("encoded");
        doThrow(new DataIntegrityViolationException("duplicate"))
                .when(userRepository).saveAndFlush(candidate);

        for (int attempt = 0; attempt < 10; attempt++) {
            assertThrows(RegistrationConflictException.class,
                    () -> authenticationService.register(request, "192.0.2.70"));
        }
        AuthenticationException.TooManyRegistrationAttemptsException limited = assertThrows(
                AuthenticationException.TooManyRegistrationAttemptsException.class,
                () -> authenticationService.register(request, "192.0.2.70"));

        assertEquals(429, limited.getHttpStatus());
        verify(userRepository, times(10)).saveAndFlush(candidate);
    }

    @Test
    void registrationIdentifierNormalizationCoversEmailAndPhone() {
        LoginAttemptRateLimiter emailLimiter = registrationLimiter(30, 60, 30, 5, 2, 3600, System::nanoTime);

        emailLimiter.checkRegistration("192.0.2.1", " Person@Example.com ", "+251911000001");
        emailLimiter.checkRegistration("192.0.2.2", "PERSON@example.com", "+251911000002");

        assertThrows(AuthenticationException.TooManyRegistrationAttemptsException.class,
                () -> emailLimiter.checkRegistration("192.0.2.3", "person@example.com", "+251911000003"));

        LoginAttemptRateLimiter phoneLimiter = registrationLimiter(30, 60, 30, 5, 2, 3600, System::nanoTime);
        phoneLimiter.checkRegistration("192.0.2.1", "first@example.com", "+251911000001");
        phoneLimiter.checkRegistration("192.0.2.2", "second@example.com", "251911000001");
        assertThrows(AuthenticationException.TooManyRegistrationAttemptsException.class,
                () -> phoneLimiter.checkRegistration("192.0.2.3", "third@example.com", "+251911000001"));
    }

    @Test
    void missingAndMalformedSourceAddressesShareConservativeRegistrationBucket() {
        LoginAttemptRateLimiter isolated = registrationLimiter(30, 60, 30, 5, 2, 3600, System::nanoTime);

        isolated.checkRegistration(null, "first@example.com", "+251911000001");
        isolated.checkRegistration("", "second@example.com", "+251911000002");

        assertThrows(AuthenticationException.TooManyRegistrationAttemptsException.class,
                () -> isolated.checkRegistration("999.999.999.999", "third@example.com", "+251911000003"));
        assertThrows(AuthenticationException.TooManyRegistrationAttemptsException.class,
                () -> isolated.checkRegistration("not-an-ip", "fourth@example.com", "+251911000004"));
    }

    @Test
    void registrationLimitsExpireAfterConfiguredRefillDuration() {
        MutableNanoClock clock = new MutableNanoClock();
        LoginAttemptRateLimiter isolated = registrationLimiter(30, 60, 30, 5, 1, 10, clock);

        isolated.checkRegistration("192.0.2.44", "person@example.com", "+251911000001");
        assertThrows(AuthenticationException.TooManyRegistrationAttemptsException.class,
                () -> isolated.checkRegistration("192.0.2.44", "other@example.com", "+251911000002"));

        clock.advance(Duration.ofSeconds(10));
        assertDoesNotThrow(() -> isolated.checkRegistration(
                "192.0.2.44", "other@example.com", "+251911000002"));
    }

    @Test
    void staleSlotsAreReclaimedAfterAFullBucketRefillInterval() {
        MutableNanoClock clock = new MutableNanoClock();
        LoginAttemptRateLimiter isolated = limiter(2, 10, 2, 10, clock, 1, 1);
        for (long accountId = 1; accountId <= 4; accountId++) {
            isolated.checkIdentifier("account-" + accountId, accountId);
        }
        assertThrows(AuthenticationException.TooManyLoginAttemptsException.class,
                () -> isolated.checkIdentifier("account-5", 5L));
        for (int source = 1; source <= 4; source++) {
            isolated.checkSource("192.0.2." + source);
        }
        assertThrows(AuthenticationException.TooManyLoginAttemptsException.class,
                () -> isolated.checkSource("192.0.2.5"));

        clock.advance(Duration.ofSeconds(20));
        assertDoesNotThrow(() -> isolated.checkIdentifier("account-5", 5L));
        assertDoesNotThrow(() -> isolated.checkSource("192.0.2.5"));
    }

    @Test
    void reclaimedSlotStartsWithNewKeysFullTokenCapacity() {
        MutableNanoClock clock = new MutableNanoClock();
        LoginAttemptRateLimiter isolated = limiter(2, 10, 2, 10, clock, 1, 1);
        for (long accountId = 1; accountId <= 4; accountId++) {
            isolated.checkIdentifier("account-" + accountId, accountId);
            isolated.checkIdentifier("account-" + accountId, accountId);
        }
        clock.advance(Duration.ofSeconds(20));

        isolated.checkIdentifier("new-account", 99L);
        assertDoesNotThrow(() -> isolated.checkIdentifier("new-account", 99L));
        assertThrows(AuthenticationException.TooManyLoginAttemptsException.class,
                () -> isolated.checkIdentifier("new-account", 99L));
    }

    @Test
    void recentlyUsedOrThrottledSlotsAreNotReclaimed() {
        MutableNanoClock clock = new MutableNanoClock();
        LoginAttemptRateLimiter isolated = limiter(1, 60, 1, 60, clock, 1, 1);
        for (long accountId = 1; accountId <= 4; accountId++) {
            isolated.checkIdentifier("account-" + accountId, accountId);
        }
        clock.advance(Duration.ofSeconds(59));

        assertThrows(AuthenticationException.TooManyLoginAttemptsException.class,
                () -> isolated.checkIdentifier("new-account", 99L));
        assertThrows(AuthenticationException.TooManyLoginAttemptsException.class,
                () -> isolated.checkIdentifier("account-1", 1L));

        clock.advance(Duration.ofSeconds(1));
        assertDoesNotThrow(() -> isolated.checkIdentifier("new-account", 99L));
    }

    @Test
    void saturatedSetRemainsBoundedAndFailsClosedUntilAStaleSlotExists() {
        MutableNanoClock clock = new MutableNanoClock();
        LoginAttemptRateLimiter isolated = limiter(1, 60, 1, 60, clock, 1, 1);
        for (long accountId = 1; accountId <= 4; accountId++) {
            isolated.checkIdentifier("account-" + accountId, accountId);
        }
        for (long accountId = 5; accountId <= 100; accountId++) {
            long id = accountId;
            assertThrows(AuthenticationException.TooManyLoginAttemptsException.class,
                    () -> isolated.checkIdentifier("account-" + id, id));
        }
        clock.advance(Duration.ofSeconds(60));
        assertDoesNotThrow(() -> isolated.checkIdentifier("account-101", 101L));
    }

    @Test
    void manyUniqueKeysDoNotGrowLimiterStateWithoutBound() {
        LoginAttemptRateLimiter isolated = limiter(5, 60, 30, 5, System::nanoTime);
        for (int attempt = 0; attempt < 20_000; attempt++) {
            isolated.checkIdentifier("unique-" + attempt, null);
        }
    }

    @Test
    void concurrentAllocationsNeverExceedSetAssociativity() throws Exception {
        int attempts = 32;
        LoginAttemptRateLimiter isolated = limiter(5, 60, 30, 5,
                System::nanoTime, 1, 1);
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger allocated = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        try {
            for (long accountId = 0; accountId < attempts; accountId++) {
                long id = accountId;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    isolated.checkIdentifier("account-" + id, id);
                    allocated.incrementAndGet();
                    return null;
                }));
            }
            ready.await();
            start.countDown();
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (java.util.concurrent.ExecutionException exception) {
                    assertThat(exception.getCause())
                            .isInstanceOf(AuthenticationException.TooManyLoginAttemptsException.class);
                }
            }
        } finally {
            executor.shutdownNow();
        }
        assertEquals(4, allocated.get());
    }

    @Test
    void concurrentAttemptsCannotExceedAccountBucketCapacity() throws Exception {
        int threadCount = 32;
        int attempts = 128;
        LoginAttemptRateLimiter isolated = limiter(7, 60, 1_000, 5, System::nanoTime);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger accepted = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        try {
            for (int attempt = 0; attempt < attempts; attempt++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    isolated.checkSource("192.0.2.99");
                    isolated.checkIdentifier("user", 77L);
                    accepted.incrementAndGet();
                    return null;
                }));
            }
            ready.await();
            start.countDown();
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (java.util.concurrent.ExecutionException exception) {
                    assertThat(exception.getCause())
                            .isInstanceOf(AuthenticationException.TooManyLoginAttemptsException.class);
                }
            }
        } finally {
            executor.shutdownNow();
        }
        assertEquals(7, accepted.get());
    }

    @Test
    void unknownWrongPasswordAndInactiveAccountUseSameFailure() {
        LoginRequest unknown = new LoginRequest("missing@example.com", "wrong");
        LoginRequest wrongPassword = new LoginRequest("person@example.com", "wrong");
        LoginRequest inactiveLogin = new LoginRequest("inactive@example.com", "correct");
        User existing = activeUser(14L, "encoded");
        User inactive = User.builder()
                .id(15L).role(User.Role.SHIPPER).isActive(false)
                .verificationStatus(true).password("encoded").build();
        when(userRepository.findByEmailOrPhone(unknown.getIdentifier())).thenReturn(Optional.empty());
        when(userRepository.findByEmailOrPhone(wrongPassword.getIdentifier())).thenReturn(Optional.of(existing));
        when(userRepository.findByEmailOrPhone(inactiveLogin.getIdentifier())).thenReturn(Optional.of(inactive));
        when(passwordEncoder.matches("wrong", existing.getPassword())).thenReturn(false);
        when(passwordEncoder.matches("correct", inactive.getPassword())).thenReturn(true);

        AuthenticationException.InvalidCredentialsException unknownFailure = assertThrows(
                AuthenticationException.InvalidCredentialsException.class,
                () -> authenticationService.login(unknown, "192.0.2.11"));
        AuthenticationException.InvalidCredentialsException passwordFailure = assertThrows(
                AuthenticationException.InvalidCredentialsException.class,
                () -> authenticationService.login(wrongPassword, "192.0.2.11"));
        AuthenticationException.InvalidCredentialsException inactiveFailure = assertThrows(
                AuthenticationException.InvalidCredentialsException.class,
                () -> authenticationService.login(inactiveLogin, "192.0.2.11"));

        assertSameFailure(unknownFailure, passwordFailure);
        assertSameFailure(unknownFailure, inactiveFailure);
        verify(passwordEncoder).matches(unknown.getPassword(), AuthenticationServiceImpl.DUMMY_PASSWORD_HASH);
    }

    @Test
    void throttledResponseDoesNotRevealWhichLimiterRejectedTheRequest() {
        LoginAttemptRateLimiter isolated = limiter(1, 60, 1, 60, System::nanoTime);
        isolated.checkSource("192.0.2.70");
        isolated.checkIdentifier("known@example.com", 42L);

        AuthenticationException.TooManyLoginAttemptsException identifierThrottle = assertThrows(
                AuthenticationException.TooManyLoginAttemptsException.class,
                () -> isolated.checkIdentifier("known@example.com", 42L));
        AuthenticationException.TooManyLoginAttemptsException sourceThrottle = assertThrows(
                AuthenticationException.TooManyLoginAttemptsException.class,
                () -> isolated.checkSource("192.0.2.70"));
        isolated.checkIdentifier("unknown@example.com", null);
        AuthenticationException.TooManyLoginAttemptsException unknownIdentifierThrottle = assertThrows(
                AuthenticationException.TooManyLoginAttemptsException.class,
                () -> isolated.checkIdentifier("unknown@example.com", null));

        assertSameFailure(identifierThrottle, sourceThrottle);
        assertSameFailure(identifierThrottle, unknownIdentifierThrottle);
    }

    @Test
    void unknownAccountDummyHashIsAValidBcryptHash() {
        BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();
        assertDoesNotThrow(() -> bcrypt.matches(
                "not-a-real-password", AuthenticationServiceImpl.DUMMY_PASSWORD_HASH));
        assertFalse(bcrypt.matches(
                "not-a-real-password", AuthenticationServiceImpl.DUMMY_PASSWORD_HASH));
    }

    @Test
    void validLoginReturnsNormallyAndClearsPriorFailureState() {
        LoginRequest request = new LoginRequest("person@example.com", "correct");
        User user = activeUser(12L, "encoded");
        when(userRepository.findByEmailOrPhone(request.getIdentifier())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(request.getPassword(), user.getPassword())).thenReturn(true);
        when(jwtService.generateToken(user)).thenReturn("access-token");
        when(authMapper.toResponse(user)).thenReturn(UserResponse.builder().id(12L).role("SHIPPER").build());

        AuthenticationResponse response = authenticationService.login(request, "192.0.2.12");

        assertEquals("access-token", response.getToken());
        verify(jwtService).generateToken(user);
        verify(passwordEncoder).matches(request.getPassword(), user.getPassword());
    }

    @Test
    void registrationDuplicateConstraintsProduceOneGenericConflict() {
        RegisterRequest phoneDuplicate = new RegisterRequest(
                "Abebe Girma", "+251911223344", "new@example.com", "securePass1", "shipper");
        RegisterRequest emailDuplicate = new RegisterRequest(
                "Abebe Girma", "+251922334455", "existing@example.com", "securePass1", "shipper");
        User phoneCandidate = User.builder().phone("+251911223344").build();
        User emailCandidate = User.builder().phone("+251922334455").build();
        when(authMapper.toEntity(phoneDuplicate)).thenReturn(phoneCandidate);
        when(authMapper.toEntity(emailDuplicate)).thenReturn(emailCandidate);
        when(passwordEncoder.encode(phoneDuplicate.getPassword())).thenReturn("encoded");
        when(passwordEncoder.encode(emailDuplicate.getPassword())).thenReturn("encoded");
        doThrow(new DataIntegrityViolationException("phone unique constraint"))
                .when(userRepository).saveAndFlush(phoneCandidate);
        doThrow(new DataIntegrityViolationException("email unique constraint"))
                .when(userRepository).saveAndFlush(emailCandidate);

        RegistrationConflictException phoneException = assertThrows(
                RegistrationConflictException.class,
                () -> authenticationService.register(phoneDuplicate));
        RegistrationConflictException emailException = assertThrows(
                RegistrationConflictException.class,
                () -> authenticationService.register(emailDuplicate));

        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        var phoneResponse = handler.handleBaseException(phoneException);
        var emailResponse = handler.handleBaseException(emailException);
        assertEquals(phoneResponse.getStatusCode(), emailResponse.getStatusCode());
        assertNotNull(phoneResponse.getBody());
        assertNotNull(emailResponse.getBody());
        assertEquals(phoneResponse.getBody().message(), emailResponse.getBody().message());
        assertEquals(phoneResponse.getBody().code(), emailResponse.getBody().code());
        verify(userRepository).saveAndFlush(phoneCandidate);
        verify(userRepository).saveAndFlush(emailCandidate);
        verify(jwtService, never()).generateToken(any(User.class));
    }

    @Test
    void newRegistrationStillReturnsAuthenticationResponse() {
        RegisterRequest request = new RegisterRequest(
                "Abebe Girma", "+251911223344", "abebe@example.com", "securePass1", "shipper");
        User user = User.builder().id(13L).role(User.Role.SHIPPER).build();
        when(authMapper.toEntity(request)).thenReturn(user);
        when(passwordEncoder.encode(request.getPassword())).thenReturn("encoded");
        when(userRepository.saveAndFlush(any(User.class))).thenReturn(user);
        when(jwtService.generateToken(user)).thenReturn("registered-token");
        when(authMapper.toResponse(user)).thenReturn(UserResponse.builder().id(13L).build());

        AuthenticationResponse response = authenticationService.register(request);

        assertEquals("registered-token", response.getToken());
        assertEquals("encoded", user.getPassword());
    }

    private static User activeUser(Long id, String password) {
        return User.builder()
                .id(id).role(User.Role.SHIPPER).isActive(true)
                .verificationStatus(true).password(password).build();
    }

    private static void assertSameFailure(AuthenticationException first,
                                          AuthenticationException second) {
        assertEquals(first.getHttpStatus(), second.getHttpStatus());
        assertEquals(first.getCode(), second.getCode());
        assertEquals(first.getMessage(), second.getMessage());
    }

    private static LoginAttemptRateLimiter limiter(
            int identifierCapacity, long identifierRefillSeconds,
            int sourceCapacity, long sourceRefillSeconds, LongSupplier clock) {
        return new LoginAttemptRateLimiter(identifierCapacity, identifierRefillSeconds,
                sourceCapacity, sourceRefillSeconds, clock);
    }

    private static LoginAttemptRateLimiter registrationLimiter(
            int identifierCapacity, long identifierRefillSeconds,
            int sourceCapacity, long sourceRefillSeconds,
            int registrationCapacity, long registrationRefillSeconds, LongSupplier clock) {
        return new LoginAttemptRateLimiter(identifierCapacity, identifierRefillSeconds,
                sourceCapacity, sourceRefillSeconds, registrationCapacity, registrationRefillSeconds, clock);
    }

    private static LoginAttemptRateLimiter limiter(
            int identifierCapacity, long identifierRefillSeconds,
            int sourceCapacity, long sourceRefillSeconds, LongSupplier clock,
            int identifierSetCount, int sourceSetCount) {
        return new LoginAttemptRateLimiter(identifierCapacity, identifierRefillSeconds,
                sourceCapacity, sourceRefillSeconds, clock,
                identifierSetCount, sourceSetCount);
    }

    private static final class MutableNanoClock implements LongSupplier {
        private long nowNanos;

        @Override
        public long getAsLong() {
            return nowNanos;
        }

        private void advance(Duration duration) {
            nowNanos += duration.toNanos();
        }
    }
}
