package com.ethioloadai.analytics.service;

import com.ethioloadai.analytics.dto.*;
import com.ethioloadai.booking.entity.Booking;
import com.ethioloadai.booking.repository.BookingRepository;
import com.ethioloadai.exception.AuthorizationException;
import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.freight.repository.FreightRepository;
import com.ethioloadai.payment.entity.Payment;
import com.ethioloadai.payment.repository.PaymentRepository;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.user.exception.UserException;
import com.ethioloadai.user.repository.UserRepository;
import com.ethioloadai.vehicle.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAnalyticsServiceImpl implements AdminAnalyticsService {

    private final UserRepository userRepository;
    private final VehicleRepository vehicleRepository;
    private final FreightRepository freightRepository;
    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;

    private static final String[] MONTH_NAMES = {
            "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    };

    private void ensureAdmin(User adminUser) {
        if (adminUser == null || adminUser.getRole() != User.Role.ADMIN) {
            log.warn("Access denied for non-admin user: {}", adminUser != null ? adminUser.getId() : "anonymous");
            throw new AuthorizationException("Only administrators can perform this operation");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public AdminStatsResponse getPlatformStats(User adminUser) {
        ensureAdmin(adminUser);
        log.info("Admin {} fetching platform stats", adminUser.getId());

        long totalUsers = userRepository.count();
        long totalDrivers = userRepository.countByRole(User.Role.DRIVER);
        long verifiedDrivers = userRepository.countByRoleAndVerificationStatus(User.Role.DRIVER, true);
        long pendingVerifications = userRepository.countByRoleAndVerificationStatus(User.Role.DRIVER, false);

        long activeFreight = freightRepository.countByStatusIn(List.of(
                Freight.FreightStatus.POSTED,
                Freight.FreightStatus.MATCHED,
                Freight.FreightStatus.IN_TRANSIT
        ));
        long completedDeliveries = freightRepository.countByStatusIn(List.of(
                Freight.FreightStatus.DELIVERED,
                Freight.FreightStatus.COMPLETED
        ));

        long totalPayments = paymentRepository.count();
        BigDecimal totalRevBd = paymentRepository.totalRevenue();
        double totalRevenue = totalRevBd != null ? totalRevBd.doubleValue() : 0.0;

        long activeVehicles = vehicleRepository.countByAvailabilityStatus("available");

        // Preserved legacy fixed/default values
        double escrowHeld = 0.0;
        double totalCommissions = 0.0;
        long openDisputes = 0L;
        double averageDeliveryTime = 4.2;
        int successRate = totalDrivers > 0 ? 85 : 0;

        // Construct nested maps for legacy frontend compatibility
        Map<String, Object> usersMap = new LinkedHashMap<>();
        usersMap.put("total", totalUsers);

        Map<String, Object> driversMap = new LinkedHashMap<>();
        driversMap.put("total", totalDrivers);
        driversMap.put("active", verifiedDrivers);

        Map<String, Object> freightMap = new LinkedHashMap<>();
        freightMap.put("posted", activeFreight);
        freightMap.put("completed", completedDeliveries);

        Map<String, Object> paymentsMap = new LinkedHashMap<>();
        paymentsMap.put("total", totalPayments);
        paymentsMap.put("revenue", totalRevenue);
        paymentsMap.put("escrowHeld", escrowHeld);
        paymentsMap.put("commissions", totalCommissions);
        paymentsMap.put("openDisputes", openDisputes);

        return AdminStatsResponse.builder()
                .totalUsers(totalUsers)
                .totalDrivers(totalDrivers)
                .verifiedDrivers(verifiedDrivers)
                .pendingVerifications(pendingVerifications)
                .activeFreight(activeFreight)
                .completedDeliveries(completedDeliveries)
                .totalRevenue(totalRevenue)
                .escrowHeld(escrowHeld)
                .totalCommissions(totalCommissions)
                .openDisputes(openDisputes)
                .activeVehicles(activeVehicles)
                .averageDeliveryTime(averageDeliveryTime)
                .successRate(successRate)
                .users(usersMap)
                .drivers(driversMap)
                .freight(freightMap)
                .payments(paymentsMap)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public AdminEscrowResponse getEscrowOverview(User adminUser) {
        ensureAdmin(adminUser);
        log.info("Admin {} fetching escrow overview", adminUser.getId());

        // Preserved legacy placeholder contract: all escrow buckets return count: 0, total: 0.0
        return AdminEscrowResponse.builder()
                .held(new EscrowMetricItem(0L, 0.0))
                .inTransit(new EscrowMetricItem(0L, 0.0))
                .released(new EscrowMetricItem(0L, 0.0))
                .disputed(new EscrowMetricItem(0L, 0.0))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MonthlyRevenueAnalyticsResponse> getRevenueAnalytics(User adminUser) {
        ensureAdmin(adminUser);
        log.info("Admin {} fetching revenue analytics", adminUser.getId());

        LocalDate now = LocalDate.now();
        int currentYear = now.getYear();
        int currentMonth = now.getMonthValue(); // 1 - 12

        LocalDateTime yearStart = LocalDate.of(currentYear, 1, 1).atStartOfDay();
        LocalDateTime nextYearStart = LocalDate.of(currentYear + 1, 1, 1).atStartOfDay();

        List<PaymentRepository.MonthlyPaymentAggregate> paymentAggregates =
                paymentRepository.aggregateMonthlyPaidRevenue(Payment.PaymentStatus.PAID, yearStart, nextYearStart);

        List<BookingRepository.MonthlyBookingCommissionAggregate> bookingAggregates =
                bookingRepository.aggregateMonthlyBookingCommissions(
                        List.of(Booking.BookingStatus.CONFIRMED, Booking.BookingStatus.COMPLETED),
                        yearStart,
                        nextYearStart);

        Map<Integer, PaymentRepository.MonthlyPaymentAggregate> paymentsByMonth = paymentAggregates.stream()
                .collect(Collectors.toMap(
                        PaymentRepository.MonthlyPaymentAggregate::getMonthNumber, aggregate -> aggregate));
        Map<Integer, BookingRepository.MonthlyBookingCommissionAggregate> commissionsByMonth = bookingAggregates.stream()
                .collect(Collectors.toMap(
                        BookingRepository.MonthlyBookingCommissionAggregate::getMonthNumber, aggregate -> aggregate));

        List<MonthlyRevenueAnalyticsResponse> results = new ArrayList<>(12);

        for (int m = 1; m <= 12; m++) {
            String monthName = MONTH_NAMES[m - 1];

            if (m > currentMonth) {
                results.add(MonthlyRevenueAnalyticsResponse.builder()
                        .month(monthName)
                        .revenue(0.0)
                        .commissions(0.0)
                        .deliveries(0)
                        .build());
                continue;
            }

            PaymentRepository.MonthlyPaymentAggregate paymentAggregate = paymentsByMonth.get(m);
            double revenue = paymentAggregate == null ? 0.0 : paymentAggregate.getAmount().doubleValue();
            int deliveries = paymentAggregate == null ? 0 : paymentAggregate.getPaymentCount().intValue();

            BookingRepository.MonthlyBookingCommissionAggregate commissionAggregate = commissionsByMonth.get(m);
            double commissions = commissionAggregate == null ? 0.0 : commissionAggregate.getCommissions().doubleValue();

            results.add(MonthlyRevenueAnalyticsResponse.builder()
                    .month(monthName)
                    .revenue(revenue)
                    .commissions(commissions)
                    .deliveries(deliveries)
                    .build());
        }

        return results;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RouteAnalyticsResponse> getRouteAnalytics(User adminUser) {
        ensureAdmin(adminUser);
        log.info("Admin {} fetching route analytics", adminUser.getId());

        List<RouteCountProjection> projections = freightRepository.findTopRoutes(PageRequest.of(0, 10));

        return projections.stream()
                .map(p -> RouteAnalyticsResponse.builder()
                        .pickup(p.getPickup())
                        .delivery(p.getDelivery())
                        .route(p.getPickup() + " → " + p.getDelivery())
                        .count(p.getCount() != null ? p.getCount() : 0L)
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CargoAnalyticsResponse> getCargoAnalytics(User adminUser) {
        ensureAdmin(adminUser);
        log.info("Admin {} fetching cargo analytics", adminUser.getId());

        List<CargoTypeCountProjection> distribution = freightRepository.findCargoTypeDistribution();
        long total = distribution.stream()
                .mapToLong(p -> p.getCount() != null ? p.getCount() : 0L)
                .sum();

        if (total == 0) {
            return Collections.emptyList();
        }

        return distribution.stream()
                .map(p -> {
                    long count = p.getCount() != null ? p.getCount() : 0L;
                    int percentage = (int) Math.round(((double) count / total) * 100.0);
                    return CargoAnalyticsResponse.builder()
                            .cargoType(p.getCargoType())
                            .count(count)
                            .percentage(percentage)
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public DriverStatusUpdateResponse updateDriverStatus(Long driverId, UpdateDriverStatusRequest request, User adminUser) {
        ensureAdmin(adminUser);
        log.info("Admin {} updating status for driver {} to {}", adminUser.getId(), driverId, request.getStatus());

        User driver = userRepository.findById(driverId)
                .filter(u -> u.getRole() == User.Role.DRIVER)
                .orElseThrow(() -> new UserException.UserNotFoundException("Driver not found"));

        String rawStatus = request.getStatus();
        String normalizedStatus = rawStatus.trim().toLowerCase();

        if ("active".equals(normalizedStatus) || "approved".equals(normalizedStatus)) {
            driver.setVerificationStatus(true);
            driver.setIsActive(true);
        } else {
            driver.setVerificationStatus(false);
            if ("suspended".equals(normalizedStatus)) {
                driver.setIsActive(false);
            }
        }

        userRepository.save(driver);

        Map<String, Object> userMap = new LinkedHashMap<>();
        userMap.put("id", driver.getId());
        userMap.put("name", driver.getFullName());
        userMap.put("email", driver.getEmail());
        userMap.put("phone", driver.getPhone());
        userMap.put("role", driver.getRole() != null ? driver.getRole().name().toLowerCase() : "driver");
        userMap.put("isActive", driver.getIsActive());
        userMap.put("verificationStatus", driver.getVerificationStatus());

        return DriverStatusUpdateResponse.builder()
                .id(driver.getId())
                .userId(driver.getId())
                .status(rawStatus)
                .user(userMap)
                .build();
    }
}
