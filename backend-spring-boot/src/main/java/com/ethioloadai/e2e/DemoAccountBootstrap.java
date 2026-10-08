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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Component
@Profile("dev & !prod")
@ConditionalOnProperty(name = "app.demo-seed.enabled", havingValue = "true")
public class DemoAccountBootstrap implements ApplicationRunner {

    static final String ALLOWED_DATASOURCE_URL =
            "jdbc:postgresql://127.0.0.1:5433/ethioloadai_e2e";

    private static final Logger log = LoggerFactory.getLogger(DemoAccountBootstrap.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String datasourceUrl;
    private final String adminPassword;
    private final String shipperPassword;
    private final String driverPassword;

    public DemoAccountBootstrap(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${spring.datasource.url:}") String datasourceUrl,
            @Value("${e2e.demo.admin-password:}") String adminPassword,
            @Value("${e2e.demo.shipper-password:}") String shipperPassword,
            @Value("${e2e.demo.driver-password:}") String driverPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.datasourceUrl = datasourceUrl;
        this.adminPassword = adminPassword;
        this.shipperPassword = shipperPassword;
        this.driverPassword = driverPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        requireAllowedDatasource();
        requireConfiguredPasswords();

        List<DemoAccount> accounts = List.of(
                new DemoAccount("Demo Administrator", "+251911000001",
                        "admin@freightlink.et", User.Role.ADMIN, true, true, adminPassword),
                new DemoAccount("Demo Shipper", "+251911000002",
                        "tigist@shipper.et", User.Role.SHIPPER, true, true, shipperPassword),
                new DemoAccount("Demo Driver", "+251911000003",
                        "bekele@driver.et", User.Role.DRIVER, false, false, driverPassword)
        );

        for (DemoAccount account : accounts) {
            createIfAbsent(account);
        }
    }

    private void requireAllowedDatasource() {
        if (!ALLOWED_DATASOURCE_URL.equals(datasourceUrl)) {
            throw new IllegalStateException(
                    "DEV/E2E demo seeding is restricted to " + ALLOWED_DATASOURCE_URL);
        }
    }

    private void requireConfiguredPasswords() {
        if (isBlank(adminPassword) || isBlank(shipperPassword) || isBlank(driverPassword)) {
            throw new IllegalStateException(
                    "Set E2E_DEMO_ADMIN_PASSWORD, E2E_DEMO_SHIPPER_PASSWORD, and "
                            + "E2E_DEMO_DRIVER_PASSWORD to run the DEV/E2E demo seed.");
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
                log.info("Preserving existing DEV/E2E demo account email={} role={}",
                        account.email(), account.role());
                return;
            }

            throw new IllegalStateException(
                    "DEV/E2E demo account identity conflicts with an existing user: "
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
        log.info("Created DEV/E2E demo account email={} role={}", account.email(), account.role());
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
