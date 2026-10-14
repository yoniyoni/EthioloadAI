package com.ethioloadai.vehicle;

import com.ethioloadai.vehicle.entity.Vehicle;
import com.ethioloadai.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class VehicleAvailabilityRepositoryTest {

    @Autowired private VehicleRepository vehicleRepository;

    private String platePrefix;

    @BeforeEach
    void setUp() {
        platePrefix = UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void cityAvailabilityIncludesOnlyApprovedVehicles() {
        save("approved-city", Vehicle.VehicleApprovalStatus.APPROVED, "available", "Addis Ababa");
        save("review-city", Vehicle.VehicleApprovalStatus.UNDER_REVIEW, "available", "Addis Ababa");
        save("rejected-city", Vehicle.VehicleApprovalStatus.REJECTED, "available", "Addis Ababa");
        save("suspended-city", Vehicle.VehicleApprovalStatus.SUSPENDED, "available", "Addis Ababa");
        save("unavailable-city", Vehicle.VehicleApprovalStatus.APPROVED, "assigned", "Addis Ababa");
        save("approved-other-city", Vehicle.VehicleApprovalStatus.APPROVED, "available", "Adama");

        List<Vehicle> available = vehicleRepository.findAvailableVehiclesInCity(
                "Addis Ababa", Vehicle.VehicleApprovalStatus.APPROVED);

        assertThat(available).extracting(Vehicle::getPlateNumber)
                .containsExactly(plate("approved-city"));
    }

    @Test
    void recommendationCandidatesIncludeOnlyApprovedAvailableVehicles() {
        save("approved-recommendation", Vehicle.VehicleApprovalStatus.APPROVED, "available", "Addis Ababa");
        save("review-recommendation", Vehicle.VehicleApprovalStatus.UNDER_REVIEW, "available", "Addis Ababa");
        save("rejected-recommendation", Vehicle.VehicleApprovalStatus.REJECTED, "available", "Addis Ababa");
        save("suspended-recommendation", Vehicle.VehicleApprovalStatus.SUSPENDED, "available", "Addis Ababa");
        save("unavailable-recommendation", Vehicle.VehicleApprovalStatus.APPROVED, "assigned", "Addis Ababa");

        List<Vehicle> candidates = vehicleRepository.findByAvailabilityStatusAndApprovalStatus(
                "available", Vehicle.VehicleApprovalStatus.APPROVED);

        assertThat(candidates).extracting(Vehicle::getPlateNumber)
                .containsExactly(plate("approved-recommendation"));
    }

    @Test
    void nearbyAvailabilityIncludesOnlyApprovedVehicles() {
        saveNearby("approved-nearby", Vehicle.VehicleApprovalStatus.APPROVED, "available");
        saveNearby("review-nearby", Vehicle.VehicleApprovalStatus.UNDER_REVIEW, "available");
        saveNearby("rejected-nearby", Vehicle.VehicleApprovalStatus.REJECTED, "available");
        saveNearby("suspended-nearby", Vehicle.VehicleApprovalStatus.SUSPENDED, "available");
        saveNearby("unavailable-nearby", Vehicle.VehicleApprovalStatus.APPROVED, "assigned");

        List<Vehicle> nearby = vehicleRepository.findNearbyAvailableVehicles(
                new BigDecimal("9.0320"), new BigDecimal("38.7469"), 10.0,
                Vehicle.VehicleApprovalStatus.APPROVED);

        assertThat(nearby).extracting(Vehicle::getPlateNumber)
                .containsExactly(plate("approved-nearby"));
    }

    private void save(String suffix, Vehicle.VehicleApprovalStatus approvalStatus,
                      String availabilityStatus, String city) {
        vehicleRepository.saveAndFlush(vehicle(plate(suffix), approvalStatus, availabilityStatus, city,
                new BigDecimal("9.0320"), new BigDecimal("38.7469")));
    }

    private void saveNearby(String suffix, Vehicle.VehicleApprovalStatus approvalStatus,
                            String availabilityStatus) {
        vehicleRepository.saveAndFlush(vehicle(plate(suffix), approvalStatus, availabilityStatus,
                "Addis Ababa", new BigDecimal("9.0320"), new BigDecimal("38.7469")));
    }

    private Vehicle vehicle(String plate, Vehicle.VehicleApprovalStatus approvalStatus,
                            String availabilityStatus, String city, BigDecimal latitude, BigDecimal longitude) {
        return Vehicle.builder()
                .userId(1L)
                .truckType("Test truck")
                .plateNumber(plate)
                .capacity(10.0)
                .currentCity(city)
                .latitude(latitude)
                .longitude(longitude)
                .availabilityStatus(availabilityStatus)
                .approvalStatus(approvalStatus)
                .build();
    }

    private String plate(String suffix) {
        return platePrefix + "-" + suffix;
    }
}
