package com.ethioloadai.user.repository;

import com.ethioloadai.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByPhone(String phone);

    @Query("SELECT u FROM User u WHERE u.email = :identifier OR u.phone = :identifier")
    Optional<User> findByEmailOrPhone(String identifier);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    boolean existsByEmailAndIdNot(String email, Long id);

    boolean existsByPhoneAndIdNot(String phone, Long id);

    /** All drivers (or any users) whose fleet_owner_id = fleetOwnerId. */
    List<User> findByFleetOwnerId(Long fleetOwnerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :userId")
    Optional<User> findByIdForUpdate(@Param("userId") Long userId);

    /** Batch lookup for drivers belonging to any of the specified fleet owner IDs. */
    List<User> findByFleetOwnerIdIn(java.util.Collection<Long> fleetOwnerIds);

    /** All users with a specific role, ordered newest first. */
    List<User> findByRoleOrderByCreatedAtDesc(User.Role role);

    /** Count users by role. */
    long countByRole(User.Role role);

    /** Count users by role and verification status. */
    long countByRoleAndVerificationStatus(User.Role role, Boolean verificationStatus);

    // ── Driver Listing (B1) ───────────────────────────────────────────────────

    /**
     * Paginated query for all users with role=DRIVER.
     * <p>Used by the public driver directory endpoint.
     * <p>Callers must apply additional filters (verification, active status) in the service layer.
     *
     * @param role The role to filter by (typically User.Role.DRIVER)
     * @param pageable Pagination parameters (page, size, sort)
     * @return Page of users with the specified role
     */
    Page<User> findByRole(User.Role role, Pageable pageable);

    /**
     * Paginated query for drivers filtered by verification and active status.
     * <p>Used for "active" status filter (verified=true AND active=true).
     *
     * @param role The role to filter by
     * @param verificationStatus Verification status (true/false)
     * @param isActive Active status (true/false)
     * @param pageable Pagination parameters
     * @return Page of filtered drivers
     */
    Page<User> findByRoleAndVerificationStatusAndIsActive(
            User.Role role,
            Boolean verificationStatus,
            Boolean isActive,
            Pageable pageable
    );

    /**
     * Paginated query for drivers filtered by verification status only.
     * <p>Used for "approved" status filter (verified=true, any active status).
     *
     * @param role The role to filter by
     * @param verificationStatus Verification status (true/false)
     * @param pageable Pagination parameters
     * @return Page of filtered drivers
     */
    Page<User> findByRoleAndVerificationStatus(
            User.Role role,
            Boolean verificationStatus,
            Pageable pageable
    );

    /**
     * Paginated query for drivers filtered by active status only.
     * <p>Used for "suspended" status filter (active=false, any verification status).
     *
     * @param role The role to filter by
     * @param isActive Active status (true/false)
     * @param pageable Pagination parameters
     * @return Page of filtered drivers
     */
    Page<User> findByRoleAndIsActive(
            User.Role role,
            Boolean isActive,
            Pageable pageable
    );
}
