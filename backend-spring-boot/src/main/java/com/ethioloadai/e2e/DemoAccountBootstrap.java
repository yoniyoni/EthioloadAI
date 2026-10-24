package com.ethioloadai.e2e;

import com.ethioloadai.user.entity.User;
import com.ethioloadai.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Component
@Profile("e2e & !prod")
@ConditionalOnProperty(name = "app.demo-seed.enabled", havingValue = "true")
public class DemoAccountBootstrap implements ApplicationRunner {

    static final String ALLOWED_E2E_DATASOURCE_URL =
            E2eDatasourceGuardInitializer.ALLOWED_DATASOURCE_URL;

    private static final Logger log = LoggerFactory.getLogger(DemoAccountBootstrap.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String datasourceUrl;
    private final Environment environment;
    private final String adminPassword;

    public DemoAccountBootstrap(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${spring.datasource.url:}") String datasourceUrl,
            Environment environment,
            @Value("${e2e.demo.admin-password:}") String adminPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.datasourceUrl = datasourceUrl;
        this.environment = environment;
        this.adminPassword = adminPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        requireAllowedDatasource();
        requireConfiguredPasswords();

        createIfAbsent(new DemoAccount("Demo Administrator", "+251911000001",
                "admin@freightlink.et", User.Role.ADMIN, true, true, adminPassword));
    }

    private void requireAllowedDatasource() {
        List<String> profiles = Arrays.asList(environment.getActiveProfiles());
        boolean allowedE2eTarget =
                profiles.contains("e2e") && !profiles.contains("prod")
                        && ALLOWED_E2E_DATASOURCE_URL.equals(datasourceUrl);
        if (!allowedE2eTarget) {
            throw new IllegalStateException(
                    "Demo seeding is restricted to its approved profile and datasource");
        }
    }

    private void requireConfiguredPasswords() {
        if (isBlank(adminPassword)) {
            throw new IllegalStateException(
                    "Set E2E_DEMO_ADMIN_PASSWORD to run the E2E admin bootstrap.");
        }
    }

    private void createIfAbsent(DemoAccount account) {
        Optional<User> byEmail = userRepository.findByEmail(account.email());
        Optional<User> byPhone = userRepository.findByPhone(account.phone());

        if (byEmail.isPresent() || byPhone.isPresent()) {
            if (byEmail.isPresent()
                    && byPhone.isPresent()
                    && Objects.equals(byEmail.get().getId(), byPhone.get().getId())
                    && matchesIdentity(byEmail.get(), account)) {
                log.info("Preserving existing E2E admin account email={} role={}",
                        account.email(), account.role());
                return;
            }

            throw new IllegalStateException(
                    "E2E admin bootstrap identity conflicts with an existing user: "
                            + account.email());
        }

        User user = User.builder()
                .fullName(account.fullName())
                .phone(account.phone())
                .email(account.email())
                .password(passwordEncoder.encode(account.password()))
                .role(account.role())
                .verificationStatus(account.verificationStatus())
                .isActive(account.isActive())
                .build();

        userRepository.saveAndFlush(user);
        log.info("Created E2E admin account email={} role={}", account.email(), account.role());
    }

    private boolean matchesIdentity(User user, DemoAccount account) {
        return Objects.equals(user.getEmail(), account.email())
                && Objects.equals(user.getPhone(), account.phone())
                && user.getRole() == account.role();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record DemoAccount(
            String fullName,
            String phone,
            String email,
            User.Role role,
            boolean verificationStatus,
            boolean isActive,
            String password) {
    }
}
