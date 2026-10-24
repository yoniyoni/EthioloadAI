package com.ethioloadai.auth.service;

import com.ethioloadai.exception.AuthenticationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Locale;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

@Component
public class LoginAttemptRateLimiter {
    // Fixed keyed-hash tables keep memory bounded without retaining attacker-supplied keys.
    private static final int IDENTIFIER_SET_COUNT = 1 << 18;
    private static final int SOURCE_SET_COUNT = 1 << 16;
    private static final int WAYS_PER_SET = 4;
    private static final long UNINITIALIZED = Long.MIN_VALUE;
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final int identifierCapacity;
    private final long identifierRefillNanos;
    private final int sourceCapacity;
    private final long sourceRefillNanos;
    private final int registrationCapacity;
    private final long registrationRefillNanos;
    private final LongSupplier nanoTime;
    private final Mac hmac;
    private final BucketTable identifierBuckets;
    private final BucketTable sourceBuckets;

    @Autowired
    public LoginAttemptRateLimiter(
            @Value("${rate-limit.login.identifier-capacity:5}") int identifierCapacity,
            @Value("${rate-limit.login.identifier-refill-seconds:60}") long identifierRefillSeconds,
            @Value("${rate-limit.login.source-capacity:30}") int sourceCapacity,
            @Value("${rate-limit.login.source-refill-seconds:5}") long sourceRefillSeconds,
            @Value("${rate-limit.registration.capacity:10}") int registrationCapacity,
            @Value("${rate-limit.registration.refill-duration-seconds:3600}") long registrationRefillSeconds) {
        this(identifierCapacity, identifierRefillSeconds, sourceCapacity, sourceRefillSeconds,
                registrationCapacity, registrationRefillSeconds, System::nanoTime);
    }

    LoginAttemptRateLimiter(int identifierCapacity, long identifierRefillSeconds,
                            int sourceCapacity, long sourceRefillSeconds,
                            LongSupplier nanoTime) {
        this(identifierCapacity, identifierRefillSeconds, sourceCapacity, sourceRefillSeconds,
                10, 3600, nanoTime, IDENTIFIER_SET_COUNT, SOURCE_SET_COUNT);
    }

    LoginAttemptRateLimiter(int identifierCapacity, long identifierRefillSeconds,
                            int sourceCapacity, long sourceRefillSeconds,
                            LongSupplier nanoTime, int identifierSetCount, int sourceSetCount) {
        this(identifierCapacity, identifierRefillSeconds, sourceCapacity, sourceRefillSeconds,
                10, 3600, nanoTime, identifierSetCount, sourceSetCount);
    }

    LoginAttemptRateLimiter(int identifierCapacity, long identifierRefillSeconds,
                            int sourceCapacity, long sourceRefillSeconds,
                            int registrationCapacity, long registrationRefillSeconds,
                            LongSupplier nanoTime) {
        this(identifierCapacity, identifierRefillSeconds, sourceCapacity, sourceRefillSeconds,
                registrationCapacity, registrationRefillSeconds,
                nanoTime, IDENTIFIER_SET_COUNT, SOURCE_SET_COUNT);
    }

    LoginAttemptRateLimiter(int identifierCapacity, long identifierRefillSeconds,
                            int sourceCapacity, long sourceRefillSeconds,
                            int registrationCapacity, long registrationRefillSeconds,
                            LongSupplier nanoTime, int identifierSetCount, int sourceSetCount) {
        if (identifierCapacity < 1 || identifierRefillSeconds < 1
                || sourceCapacity < 1 || sourceRefillSeconds < 1
                || registrationCapacity < 1 || registrationRefillSeconds < 1
                || !isPositivePowerOfTwo(identifierSetCount)
                || !isPositivePowerOfTwo(sourceSetCount)) {
            throw new IllegalArgumentException("Login rate-limit settings must be positive");
        }
        this.identifierCapacity = identifierCapacity;
        this.identifierRefillNanos = Math.multiplyExact(identifierRefillSeconds, 1_000_000_000L);
        this.sourceCapacity = sourceCapacity;
        this.sourceRefillNanos = Math.multiplyExact(sourceRefillSeconds, 1_000_000_000L);
        this.registrationCapacity = registrationCapacity;
        this.registrationRefillNanos = Math.multiplyExact(registrationRefillSeconds, 1_000_000_000L);
        this.nanoTime = nanoTime;
        this.identifierBuckets = new BucketTable(identifierSetCount * WAYS_PER_SET);
        this.sourceBuckets = new BucketTable(sourceSetCount * WAYS_PER_SET);

        try {
            byte[] processKey = new byte[32];
            new SecureRandom().nextBytes(processKey);
            this.hmac = Mac.getInstance(HMAC_ALGORITHM);
            this.hmac.init(new SecretKeySpec(processKey, HMAC_ALGORITHM));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to initialize login rate limiting", exception);
        }
    }

    public synchronized void checkSource(String sourceIp) {
        consume(sourceBuckets, "source", normalizeSource(sourceIp),
                sourceCapacity, sourceRefillNanos);
    }

    public synchronized void checkIdentifier(String identifier, Long accountId) {
        String identity = accountId == null
                ? "identifier:" + normalizeIdentifier(identifier)
                : "account:" + accountId;
        consume(identifierBuckets, "identity", identity,
                identifierCapacity, identifierRefillNanos);
    }

    public synchronized void checkRegistration(String sourceIp, String email, String phone) {
        Supplier<AuthenticationException> limited =
                AuthenticationException.TooManyRegistrationAttemptsException::new;
        consume(sourceBuckets, "registration-source", normalizeSource(sourceIp),
                registrationCapacity, registrationRefillNanos, limited);
        if (email != null && !email.isBlank()) {
            consume(identifierBuckets, "registration-identity",
                    "email:" + normalizeIdentifier(email),
                    registrationCapacity, registrationRefillNanos, limited);
        }
        if (phone != null && !phone.isBlank()) {
            consume(identifierBuckets, "registration-identity",
                    "phone:" + normalizePhone(phone),
                    registrationCapacity, registrationRefillNanos, limited);
        }
    }

    public synchronized void recordSuccess(String identifier, Long accountId, String sourceIp) {
        String identity = accountId == null
                ? "identifier:" + normalizeIdentifier(identifier)
                : "account:" + accountId;
        clear(identifierBuckets, "identity", identity, identifierCapacity, identifierRefillNanos);
        refund(sourceBuckets, "source", normalizeSource(sourceIp),
                sourceCapacity, sourceRefillNanos);
    }

    public synchronized void refundSource(String sourceIp) {
        refund(sourceBuckets, "source", normalizeSource(sourceIp),
                sourceCapacity, sourceRefillNanos);
    }

    private void consume(BucketTable table, String domain, String value,
                         int capacity, long refillNanos) {
        consume(table, domain, value, capacity, refillNanos,
                AuthenticationException.TooManyLoginAttemptsException::new);
    }

    private void consume(BucketTable table, String domain, String value,
                         int capacity, long refillNanos,
                         Supplier<? extends AuthenticationException> limitException) {
        Key key = key(domain, value, table.tokens.length / WAYS_PER_SET);
        long now = nanoTime.getAsLong();
        int slot = resolveSlot(table, key, now, capacity, refillNanos);
        refill(table, slot, key, now, capacity, refillNanos);
        if (table.tokens[slot] < 1.0d) {
            throw limitException.get();
        }
        table.tokens[slot] -= 1.0d;
    }

    private void refund(BucketTable table, String domain, String value,
                        int capacity, long refillNanos) {
        Key key = key(domain, value, table.tokens.length / WAYS_PER_SET);
        long now = nanoTime.getAsLong();
        int slot = resolveSlot(table, key, now, capacity, refillNanos);
        refill(table, slot, key, now, capacity, refillNanos);
        table.tokens[slot] = Math.min(capacity, table.tokens[slot] + 1.0d);
    }

    private void clear(BucketTable table, String domain, String value,
                       int capacity, long refillNanos) {
        Key key = key(domain, value, table.tokens.length / WAYS_PER_SET);
        long now = nanoTime.getAsLong();
        int slot = resolveSlot(table, key, now, capacity, refillNanos);
        if (table.lastRefillNanos[slot] != UNINITIALIZED
                && table.fingerprints[slot] == key.fingerprint) {
            table.tokens[slot] = capacity;
            table.lastRefillNanos[slot] = now;
        }
    }

    private int resolveSlot(BucketTable table, Key key, long now,
                            int capacity, long refillNanos) {
        int first = key.setIndex * WAYS_PER_SET;
        int availableSlot = -1;
        int reclaimableSlot = -1;
        long fullRefillNanos = Math.multiplyExact((long) capacity, refillNanos);
        for (int way = 0; way < WAYS_PER_SET; way++) {
            int slot = first + way;
            if (table.lastRefillNanos[slot] == UNINITIALIZED) {
                if (availableSlot == -1) {
                    availableSlot = slot;
                }
            } else if (table.fingerprints[slot] == key.fingerprint) {
                return slot;
            } else if (reclaimableSlot == -1
                    && now - table.lastRefillNanos[slot] >= fullRefillNanos) {
                reclaimableSlot = slot;
            }
        }
        if (availableSlot != -1) {
            return availableSlot;
        }
        if (reclaimableSlot != -1) {
            table.clear(reclaimableSlot);
            return reclaimableSlot;
        }
        throw new AuthenticationException.TooManyLoginAttemptsException();
    }

    private static boolean isPositivePowerOfTwo(int value) {
        return value > 0 && (value & (value - 1)) == 0;
    }

    private static void refill(BucketTable table, int slot, Key key, long now,
                               int capacity, long refillNanos) {
        long last = table.lastRefillNanos[slot];
        if (last == UNINITIALIZED) {
            table.tokens[slot] = capacity;
            table.fingerprints[slot] = key.fingerprint;
        } else {
            long elapsed = Math.max(0L, now - last);
            table.tokens[slot] = Math.min(
                    capacity, table.tokens[slot] + (double) elapsed / refillNanos);
        }
        table.lastRefillNanos[slot] = now;
    }

    private Key key(String domain, String value, int bucketCount) {
        byte[] domainBytes = domain.getBytes(StandardCharsets.UTF_8);
        byte[] valueBytes = value.getBytes(StandardCharsets.UTF_8);
        ByteBuffer input = ByteBuffer.allocate(domainBytes.length + valueBytes.length + 1);
        input.put(domainBytes).put((byte) 0).put(valueBytes);
        byte[] digest = hmac.doFinal(input.array());
        ByteBuffer digestBuffer = ByteBuffer.wrap(digest);
        int setIndex = digestBuffer.getInt() & (bucketCount - 1);
        long fingerprint = digestBuffer.getLong();
        return new Key(setIndex, fingerprint);
    }

    private static String normalizeIdentifier(String identifier) {
        if (identifier == null) {
            return "";
        }
        return identifier.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeSource(String sourceIp) {
        if (sourceIp == null || sourceIp.isBlank()) {
            return "unknown";
        }
        String address = sourceIp.trim();
        if (address.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}")) {
            String[] octets = address.split("\\.");
            StringBuilder normalized = new StringBuilder();
            for (String octet : octets) {
                int value = Integer.parseInt(octet);
                if (value > 255) {
                    return "unknown";
                }
                if (normalized.length() > 0) {
                    normalized.append('.');
                }
                normalized.append(value);
            }
            return normalized.toString();
        }
        if (!address.contains(":") || !address.matches("[0-9A-Fa-f:.]+")) {
            return "unknown";
        }
        try {
            InetAddress parsed = InetAddress.getByName(address);
            return parsed instanceof Inet6Address
                    ? parsed.getHostAddress().toLowerCase(Locale.ROOT)
                    : "unknown";
        } catch (UnknownHostException exception) {
            return "unknown";
        }
    }

    private static String normalizePhone(String phone) {
        String normalized = phone.trim();
        return normalized.startsWith("+") ? normalized.substring(1) : normalized;
    }

    private record Key(int setIndex, long fingerprint) {
    }

    private static final class BucketTable {
        private final double[] tokens;
        private final long[] lastRefillNanos;
        private final long[] fingerprints;

        private BucketTable(int size) {
            tokens = new double[size];
            lastRefillNanos = new long[size];
            Arrays.fill(lastRefillNanos, UNINITIALIZED);
            fingerprints = new long[size];
        }

        private void clear(int slot) {
            tokens[slot] = 0.0d;
            lastRefillNanos[slot] = UNINITIALIZED;
            fingerprints[slot] = 0L;
        }
    }
}
