package com.ethioloadai.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link JwtConfig#validate()}.
 *
 * Covers Fix 10: the secret minimum-length check uses UTF-8 byte count,
 * not the Java {@code String.length()} character count.
 */
@DisplayName("JwtConfig secret byte-length validation")
class JwtConfigValidationTest {

    private JwtConfig configWith(String secret) {
        JwtConfig config = new JwtConfig();
        config.setSecret(secret);
        config.setExpiration(900_000L);
        config.setRefreshExpiration(604_800_000L);
        config.setHeader("Authorization");
        config.setPrefix("Bearer");
        return config;
    }

    // ── Fix 10: Byte-length validation ───────────────────────────────────────

    @Test
    @DisplayName("32-char ASCII secret (32 bytes) passes validation")
    void exactlyThirtyTwoAsciiCharsIsAccepted() {
        // 32 ASCII characters = 32 UTF-8 bytes — exactly the minimum
        String secret = "abcdefghijklmnopqrstuvwxyz123456"; // exactly 32 chars/bytes
        assertThatCode(() -> configWith(secret).validate()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("64-char ASCII secret passes validation")
    void longAsciiSecretIsAccepted() {
        String secret = "a".repeat(64);
        assertThatCode(() -> configWith(secret).validate()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("31-char ASCII secret (31 bytes) is rejected")
    void thirtyOneCharAsciiSecretIsRejected() {
        String secret = "a".repeat(31); // 31 UTF-8 bytes < 32
        assertThatThrownBy(() -> configWith(secret).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    @DisplayName("empty secret is rejected")
    void emptySecretIsRejected() {
        String secret = "";
        assertThatThrownBy(() -> configWith(secret).validate())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("11 Ethiopic chars (33 UTF-8 bytes) pass validation even though char count < 32")
    void multiByteCharsOver32BytesAreAccepted() {
        // Each 'ሀ' (Ethiopic script) is 3 UTF-8 bytes.
        // 11 chars × 3 bytes = 33 bytes ≥ 32 minimum.
        // Java's String.length() would return 11 — a char-count check would wrongly reject this.
        String secret = "ሀ".repeat(11);

        // Verify the test premise: char count < 32, byte count >= 32
        assertThat(secret.length()).isEqualTo(11);
        assertThat(secret.getBytes(StandardCharsets.UTF_8).length).isEqualTo(33);

        // Should pass: byte count meets the 32-byte minimum
        assertThatCode(() -> configWith(secret).validate()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("short ASCII secret below minimum is rejected")
    void asciiSecretBelowMinIsRejected() {
        // 10 chars — trivially short
        String secret = "shortkey1!";
        assertThatThrownBy(() -> configWith(secret).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bytes");
    }
}
