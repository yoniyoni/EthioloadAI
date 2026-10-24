package com.ethioloadai.e2e;

import com.ethioloadai.user.entity.User;
import com.ethioloadai.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class DemoAccountBootstrapTest {

    private UserRepository users;
    private PasswordEncoder encoder;
    private MockEnvironment environment;
    private DemoAccountBootstrap bootstrap;
    private String adminPassword;
    private Map<String, User> usersByEmail;
    private Map<String, User> usersByPhone;
    private AtomicLong ids;

    @BeforeEach
    void setUp() {
        adminPassword = UUID.randomUUID().toString();
        users = mock(UserRepository.class);
        encoder = new BCryptPasswordEncoder();
        environment = new MockEnvironment();
        environment.setActiveProfiles("e2e");
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
                users, encoder, DemoAccountBootstrap.ALLOWED_E2E_DATASOURCE_URL, environment,
                adminPassword);
    }

    @Test
    void createsExpectedAccountsWithEncodedPasswordsAndNormalRoleStates() {
        bootstrap.run(null);

        assertAccount("admin@freightlink.et", "+251911000001", User.Role.ADMIN,
                true, true, adminPassword);
        verify(users).saveAndFlush(any(User.class));
    }

    @Test
    void rerunPreservesExistingAdminPasswordAndState() {
        bootstrap.run(null);
        User admin = usersByEmail.get("admin@freightlink.et");
        String originalPasswordHash = admin.getPassword();
        admin.setVerificationStatus(false);
        admin.setIsActive(false);

        bootstrap.run(null);

        verify(users, times(1)).saveAndFlush(any(User.class));
        assertThat(admin.getPassword()).isEqualTo(originalPasswordHash);
        assertThat(admin.getVerificationStatus()).isFalse();
        assertThat(admin.getIsActive()).isFalse();
    }

    @Test
    void refusesToChangeAnExistingIdentityWithAnotherRole() {
        User existing = User.builder()
                .id(41L)
                .fullName("Existing shipper")
                .email("admin@freightlink.et")
                .phone("+251911000001")
                .password("existing-hash")
                .role(User.Role.SHIPPER)
                .verificationStatus(true)
                .isActive(true)
                .build();
        usersByEmail.put(existing.getEmail(), existing);
        usersByPhone.put(existing.getPhone(), existing);

        assertThatThrownBy(() -> bootstrap.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("identity conflicts");

        assertThat(existing.getRole()).isEqualTo(User.Role.SHIPPER);
        assertThat(existing.getPassword()).isEqualTo("existing-hash");
        verify(users, never()).saveAndFlush(any(User.class));
    }

    @Test
    void refusesAnyDatasourceOtherThanTheIsolatedE2EDatabaseBeforeRepositoryAccess() {
        DemoAccountBootstrap unsafe = new DemoAccountBootstrap(
                users, encoder, "jdbc:postgresql://127.0.0.1:5433/ethioloadai_spring",
                environment, adminPassword);

        assertThatThrownBy(() -> unsafe.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("restricted");
        verifyNoInteractions(users);
    }

    @Test
    void refusesToSeedWhenAnyDemoPasswordIsMissing() {
        DemoAccountBootstrap missingPassword = new DemoAccountBootstrap(
                users, encoder, DemoAccountBootstrap.ALLOWED_E2E_DATASOURCE_URL,
                environment, "");

        assertThatThrownBy(() -> missingPassword.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("E2E_DEMO_ADMIN_PASSWORD");
        verifyNoInteractions(users);
    }

    @Test
    void onlyLoadsForTheExplicitE2eProfileAndEnableFlag() {
        assertThat(DemoAccountBootstrap.class.getAnnotation(Profile.class).value())
                .containsExactly("e2e & !prod");
        ConditionalOnProperty condition =
                DemoAccountBootstrap.class.getAnnotation(ConditionalOnProperty.class);
        assertThat(condition.name()).containsExactly("app.demo-seed.enabled");
        assertThat(condition.havingValue()).isEqualTo("true");
    }

    @Test
    void e2eTargetIsAcceptedOnlyForTheE2eProfile() {
        MockEnvironment e2eEnvironment = new MockEnvironment();
        e2eEnvironment.setActiveProfiles("e2e");
        assertThat(DemoAccountBootstrap.ALLOWED_E2E_DATASOURCE_URL)
                .isEqualTo("jdbc:postgresql://127.0.0.1:15433/ethioloadai_e2e");
        DemoAccountBootstrap e2e = new DemoAccountBootstrap(
                users, encoder, DemoAccountBootstrap.ALLOWED_E2E_DATASOURCE_URL,
                e2eEnvironment, adminPassword);

        e2e.run(null);

        verify(users).saveAndFlush(any(User.class));
    }

    @Test
    void e2eBootstrapRejectsNonExactDatabaseTargetsBeforeRepositoryAccess() {
        MockEnvironment e2eEnvironment = new MockEnvironment();
        e2eEnvironment.setActiveProfiles("e2e");
        for (String datasource : new String[] {
                "jdbc:postgresql://localhost:15433/ethioloadai_e2e",
                "jdbc:postgresql://127.0.0.1:15432/ethioloadai_e2e",
                "jdbc:postgresql://127.0.0.1:15433/other_database",
                "jdbc:postgresql://127.0.0.1:5433/ethioloadai_e2e",
                "jdbc:postgresql://db.example.test:15433/ethioloadai_e2e"
        }) {
            DemoAccountBootstrap unsafe = new DemoAccountBootstrap(
                    users, encoder, datasource, e2eEnvironment, adminPassword);

            assertThatThrownBy(() -> unsafe.run(null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("approved profile and datasource");
        }

        verifyNoInteractions(users);
    }

    @Test
    void e2eTargetIsRejectedOutsideTheE2eProfile() {
        MockEnvironment devEnvironment = new MockEnvironment();
        devEnvironment.setActiveProfiles("dev");
        DemoAccountBootstrap unsafe = new DemoAccountBootstrap(
                users, encoder, DemoAccountBootstrap.ALLOWED_E2E_DATASOURCE_URL, devEnvironment,
                adminPassword);

        assertThatThrownBy(() -> unsafe.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("approved profile and datasource");
        verifyNoInteractions(users);
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
