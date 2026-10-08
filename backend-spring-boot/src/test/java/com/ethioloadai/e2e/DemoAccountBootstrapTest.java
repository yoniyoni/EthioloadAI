package com.ethioloadai.e2e;

import com.ethioloadai.user.entity.User;
import com.ethioloadai.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class DemoAccountBootstrapTest {

    private static final String ADMIN_PASSWORD = "admin-test-password";
    private static final String SHIPPER_PASSWORD = "shipper-test-password";
    private static final String DRIVER_PASSWORD = "driver-test-password";

    private UserRepository users;
    private PasswordEncoder encoder;
    private DemoAccountBootstrap bootstrap;
    private Map<String, User> usersByEmail;
    private Map<String, User> usersByPhone;
    private AtomicLong ids;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        encoder = new BCryptPasswordEncoder();
        usersByEmail = new HashMap<>();
        usersByPhone = new HashMap<>();
        ids = new AtomicLong();

        when(users.findByEmail(anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(usersByEmail.get(invocation.getArgument(0))));
        when(users.findByPhone(anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(usersByPhone.get(invocation.getArgument(0))));
        when(users.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(ids.incrementAndGet());
            usersByEmail.put(user.getEmail(), user);
            usersByPhone.put(user.getPhone(), user);
            return user;
        });

        bootstrap = new DemoAccountBootstrap(
                users, encoder, DemoAccountBootstrap.ALLOWED_DATASOURCE_URL,
                ADMIN_PASSWORD, SHIPPER_PASSWORD, DRIVER_PASSWORD);
    }

    @Test
    void createsExpectedAccountsWithEncodedPasswordsAndNormalRoleStates() {
        bootstrap.run(null);

        assertAccount("admin@freightlink.et", "+251911000001", User.Role.ADMIN,
                true, true, ADMIN_PASSWORD);
        assertAccount("tigist@shipper.et", "+251911000002", User.Role.SHIPPER,
                true, true, SHIPPER_PASSWORD);
        assertAccount("bekele@driver.et", "+251911000003", User.Role.DRIVER,
                false, false, DRIVER_PASSWORD);
        verify(users, times(3)).saveAndFlush(any(User.class));
    }

    @Test
    void rerunPreservesExistingAccountsAndDoesNotResetKycOrPasswords() {
        bootstrap.run(null);
        User driver = usersByEmail.get("bekele@driver.et");
        String originalPasswordHash = driver.getPassword();
        driver.setVerificationStatus(true);
        driver.setIsActive(true);

        bootstrap.run(null);

        verify(users, times(3)).saveAndFlush(any(User.class));
        assertThat(driver.getPassword()).isEqualTo(originalPasswordHash);
        assertThat(driver.getVerificationStatus()).isTrue();
        assertThat(driver.getIsActive()).isTrue();
    }

    @Test
    void refusesAnyDatasourceOtherThanTheIsolatedE2EDatabaseBeforeRepositoryAccess() {
        DemoAccountBootstrap unsafe = new DemoAccountBootstrap(
                users, encoder, "jdbc:postgresql://127.0.0.1:5433/ethioloadai_spring",
                ADMIN_PASSWORD, SHIPPER_PASSWORD, DRIVER_PASSWORD);

        assertThatThrownBy(() -> unsafe.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("restricted");
        verifyNoInteractions(users);
    }

    @Test
    void refusesToSeedWhenAnyDemoPasswordIsMissing() {
        DemoAccountBootstrap missingPassword = new DemoAccountBootstrap(
                users, encoder, DemoAccountBootstrap.ALLOWED_DATASOURCE_URL,
                ADMIN_PASSWORD, "", DRIVER_PASSWORD);

        assertThatThrownBy(() -> missingPassword.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("E2E_DEMO_SHIPPER_PASSWORD");
        verifyNoInteractions(users);
    }

    @Test
    void onlyLoadsForTheExplicitDevProfileAndEnableFlag() {
        assertThat(DemoAccountBootstrap.class.getAnnotation(Profile.class).value())
                .containsExactly("dev & !prod");
        ConditionalOnProperty condition =
                DemoAccountBootstrap.class.getAnnotation(ConditionalOnProperty.class);
        assertThat(condition.name()).containsExactly("app.demo-seed.enabled");
        assertThat(condition.havingValue()).isEqualTo("true");
    }

    private void assertAccount(
            String email,
            String phone,
            User.Role role,
            boolean verified,
            boolean active,
            String rawPassword) {
        User user = usersByEmail.get(email);
        assertThat(user).isNotNull();
        assertThat(user.getPhone()).isEqualTo(phone);
        assertThat(user.getRole()).isEqualTo(role);
        assertThat(user.getVerificationStatus()).isEqualTo(verified);
        assertThat(user.getIsActive()).isEqualTo(active);
        assertThat(user.getPassword()).isNotEqualTo(rawPassword);
        assertThat(encoder.matches(rawPassword, user.getPassword())).isTrue();
    }
}
