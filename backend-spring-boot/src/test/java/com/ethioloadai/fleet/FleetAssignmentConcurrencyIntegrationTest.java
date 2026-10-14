package com.ethioloadai.fleet;

import com.ethioloadai.fleet.dto.AddDriverRequest;
import com.ethioloadai.fleet.exception.FleetException;
import com.ethioloadai.fleet.service.FleetService;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.user.entity.User.Role;
import com.ethioloadai.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class FleetAssignmentConcurrencyIntegrationTest {

    @Autowired private FleetService fleetService;
    @Autowired private UserRepository userRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void concurrentAssignmentWaitsForCurrentOwnerAndRejectsConflictingFleet() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        User owner1 = userRepository.save(user("Owner One", "0911" + suffix, Role.FLEET_OWNER));
        User owner2 = userRepository.save(user("Owner Two", "0912" + suffix, Role.FLEET_OWNER));
        User driver = userRepository.save(user("Driver", "0913" + suffix, Role.DRIVER));

        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        CountDownLatch contenderStarted = new CountDownLatch(1);
        CountDownLatch contenderFinished = new CountDownLatch(1);
        AtomicReference<Throwable> contenderFailure = new AtomicReference<>();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        try {
            var lockHolder = executor.submit(() -> transaction.executeWithoutResult(status -> {
                User lockedDriver = userRepository.findByIdForUpdate(driver.getId()).orElseThrow();
                lockedDriver.setFleetOwnerId(owner2.getId());
                lockAcquired.countDown();
                try {
                    if (!releaseLock.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting to release driver row lock");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while holding driver row lock", e);
                }
            }));

            assertThat(lockAcquired.await(5, TimeUnit.SECONDS)).isTrue();
            executor.submit(() -> {
                contenderStarted.countDown();
                try {
                    fleetService.addDriver(owner1.getId(), new AddDriverRequest(driver.getId()));
                } catch (RuntimeException failure) {
                    contenderFailure.set(failure);
                } finally {
                    contenderFinished.countDown();
                }
            });

            assertThat(contenderStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(contenderFinished.await(200, TimeUnit.MILLISECONDS)).isFalse();
            releaseLock.countDown();

            lockHolder.get(5, TimeUnit.SECONDS);
            assertThat(contenderFinished.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(contenderFailure.get()).isInstanceOf(FleetException.ResourceAlreadyAssignedException.class);
            assertThat(userRepository.findById(driver.getId()).orElseThrow().getFleetOwnerId())
                    .isEqualTo(owner2.getId());
        } finally {
            releaseLock.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
            userRepository.deleteById(driver.getId());
            userRepository.deleteById(owner1.getId());
            userRepository.deleteById(owner2.getId());
        }
    }

    private User user(String name, String phone, Role role) {
        return User.builder()
                .fullName(name)
                .phone(phone)
                .password("$2a$12$dummy.hashed.password")
                .role(role)
                .isActive(true)
                .verificationStatus(false)
                .build();
    }
}
