package com.ethioloadai.e2e;

import com.ethioloadai.user.entity.User;
import com.ethioloadai.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final boolean rotateAdminPassword;
    private final String shipperPassword;
    private final String driverPassword;

    @Autowired
    public DemoAccountBootstrap(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${spring.datasource.url:}") String datasourceUrl,
            Environment environment,
            @Value("${e2e.demo.admin-password:}") String adminPassword,
            @Value("${app.demo-seed.rotate-admin-password:false}") boolean rotateAdminPassword,
            @Value("${e2e.demo.shipper-password:}") String shipperPassword,
            @Value("${e2e.demo.driver-password:}") String driverPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.datasourceUrl = datasourceUrl;
        this.environment = environment;
        this.adminPassword = adminPassword;
        this.rotateAdminPassword = rotateAdminPassword;
        this.shipperPassword = shipperPassword;
        this.driverPassword = driverPassword;
    }

    DemoAccountBootstrap(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            String datasourceUrl,
            Environment environment,
            String adminPassword,
            boolean rotateAdminPassword) {
        this(userRepository, passwordEncoder, datasourceUrl, environment, adminPassword,
                rotateAdminPassword, "", "");
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        requireAllowedDatasource();
        requireConfiguredPasswords();

        DemoAccount admin = new DemoAccount("Demo Administrator", "+251911000001",
                "admin@freightlink.et", User.Role.ADMIN, true, true, adminPassword);
        if (rotateAdminPassword) {
            rotateExistingAdminPassword(admin);
        } else {
            createIfAbsent(admin);
        }

        seedConfiguredAccount(new DemoAccount("Tigist Mesfin", "+251911000012",
                "tigist@shipper.et", User.Role.SHIPPER, true, true, shipperPassword));
        seedConfiguredAccount(new DemoAccount("Bekele Girma", "+251911000013",
                "bekele@driver.et", User.Role.DRIVER, false, false, driverPassword));
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
        return Objects.equals(user.getFullName(), account.fullName())
                && Objects.equals(user.getEmail(), account.email())
                && Objects.equals(user.getPhone(), account.phone())
                && user.getRole() == account.role();
    }

    private void rotateExistingAdminPassword(DemoAccount account) {
        Optional<User> byEmail = userRepository.findByEmail(account.email());
        Optional<User> byPhone = userRepository.findByPhone(account.phone());

        if (byEmail.isEmpty()
                || byPhone.isEmpty()
                || !Objects.equals(byEmail.get().getId(), byPhone.get().getId())
                || !matchesIdentity(byEmail.get(), account)) {
            throw new IllegalStateException(
                    "E2E admin password rotation requires the exact existing bootstrap admin");
        }

        if (passwordEncoder.matches(account.password(), byEmail.get().getPassword())) {
            log.info("Preserving matching E2E admin password email={} role={}",
                    account.email(), account.role());
            return;
        }

        int updated = userRepository.updateBootstrapAccountPassword(
                byEmail.get().getId(),
                account.email(),
                account.phone(),
                User.Role.ADMIN,
                passwordEncoder.encode(account.password()));
        if (updated != 1) {
            throw new IllegalStateException(
                    "E2E admin password rotation did not update exactly one administrator");
        }

        log.info("Rotated password for E2E admin account email={} role={}",
                account.email(), account.role());
    }

    private void seedConfiguredAccount(DemoAccount account) {
        if (isBlank(account.password())) {
            return;
        }

        Optional<User> byEmail = userRepository.findByEmail(account.email());
        Optional<User> byPhone = userRepository.findByPhone(account.phone());
        if (byEmail.isEmpty() && byPhone.isEmpty()) {
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
            log.info("Created E2E demo account email={} role={}",
                    account.email(), account.role());
            return;
        }

        if (byEmail.isEmpty()
                || byPhone.isEmpty()
                || !Objects.equals(byEmail.get().getId(), byPhone.get().getId())
                || !matchesIdentity(byEmail.get(), account)) {
            throw new IllegalStateException(
                    "E2E demo account identity conflicts with an existing user: "
                            + account.email());
        }

        User user = byEmail.get();
        if (passwordEncoder.matches(account.password(), user.getPassword())) {
            log.info("Preserving matching E2E demo account email={} role={}",
                    account.email(), account.role());
            return;
        }

        int updated = userRepository.updateBootstrapAccountPassword(
                user.getId(),
                account.email(),
                account.phone(),
                account.role(),
                passwordEncoder.encode(account.password()));
        if (updated != 1) {
            throw new IllegalStateException(
                    "E2E demo password repair did not update exactly one matching account");
        }
        log.info("Repaired password for E2E demo account email={} role={}",
                account.email(), account.role());
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
