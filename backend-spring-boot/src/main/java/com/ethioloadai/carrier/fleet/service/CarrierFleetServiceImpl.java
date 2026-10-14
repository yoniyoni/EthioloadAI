package com.ethioloadai.carrier.fleet.service;

import com.ethioloadai.carrier.fleet.dto.*;
import com.ethioloadai.carrier.fleet.entity.*;
import com.ethioloadai.carrier.fleet.exception.CarrierFleetException;
import com.ethioloadai.carrier.fleet.repository.CarrierDriverRepository;
import com.ethioloadai.carrier.fleet.repository.VehicleDriverAssignmentRepository;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.user.repository.UserRepository;
import com.ethioloadai.vehicle.entity.Vehicle;
import com.ethioloadai.vehicle.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class CarrierFleetServiceImpl implements CarrierFleetService {

    private final CarrierDriverRepository driverRepo;
    private final VehicleDriverAssignmentRepository assignmentRepo;
    private final VehicleRepository vehicleRepo;
    private final UserRepository userRepo;

    // ── Carrier Drivers ───────────────────────────────────────────────────────

    @Override
    @Transactional
    public CarrierDriverResponse addDriver(Long companyId, AddCarrierDriverRequest req, User caller) {
        // Validate the User to be added
        User driverUser = userRepo.findById(req.getDriverUserId())
                .orElseThrow(() -> CarrierFleetException.notFound("Driver user"));
        if (driverUser.getRole() != User.Role.DRIVER) {
            throw CarrierFleetException.invalid(
                    "User " + req.getDriverUserId() + " does not have role DRIVER");
        }

        // Idempotent: if already an active driver in this company, return current state
        var existing = driverRepo.findByCarrierCompanyIdAndDriverUserId(companyId, req.getDriverUserId());
        if (existing.isPresent()) {
            CarrierDriver cd = existing.get();
            if (cd.getStatus() == CarrierDriverStatus.ACTIVE) {
                log.info("addDriver: driver {} already active in company {}", req.getDriverUserId(), companyId);
                return CarrierDriverResponse.from(cd, driverUser);
            }
            // Previously INACTIVE or REMOVED — reactivate
            cd.setStatus(CarrierDriverStatus.ACTIVE);
            cd.setAvailability(DriverAvailability.AVAILABLE);
            cd.setLeftAt(null);
            cd.setAddedBy(caller.getId());
            cd.setNotes(clean(req.getNotes()));
            cd = driverRepo.saveAndFlush(cd);
            log.info("addDriver: reactivated driver {} in company {}", req.getDriverUserId(), companyId);
            return CarrierDriverResponse.from(cd, driverUser);
        }

        CarrierDriver cd = CarrierDriver.builder()
                .carrierCompanyId(companyId)
                .driverUserId(req.getDriverUserId())
                .status(CarrierDriverStatus.ACTIVE)
                .availability(DriverAvailability.AVAILABLE)
                .addedBy(caller.getId())
                .joinedAt(LocalDateTime.now())
                .notes(clean(req.getNotes()))
                .build();
        try {
            cd = driverRepo.saveAndFlush(cd);
        } catch (DataIntegrityViolationException ex) {
            throw CarrierFleetException.conflict("Driver is already associated with this company");
        }
        log.info("addDriver: driver {} added to company {}", req.getDriverUserId(), companyId);
        return CarrierDriverResponse.from(cd, driverUser);
    }

    @Override
    public Page<CarrierDriverResponse> listDrivers(Long companyId, String status, Pageable pageable) {
        CarrierDriverStatus st = parseDriverStatus(status, CarrierDriverStatus.ACTIVE);
        return driverRepo.findByCarrierCompanyIdAndStatus(companyId, st, pageable)
                .map(cd -> CarrierDriverResponse.from(cd, requireUser(cd.getDriverUserId())));
    }

    @Override
    public CarrierDriverResponse getDriver(Long companyId, Long carrierDriverId) {
        CarrierDriver cd = requireDriver(companyId, carrierDriverId);
        return CarrierDriverResponse.from(cd, requireUser(cd.getDriverUserId()));
    }

    @Override
    @Transactional
    public CarrierDriverResponse updateDriver(Long companyId, Long carrierDriverId,
                                              UpdateCarrierDriverRequest req, User caller) {
        CarrierDriver cd = requireDriver(companyId, carrierDriverId);
        if (cd.getStatus() != CarrierDriverStatus.ACTIVE) {
            throw CarrierFleetException.invalid("Only ACTIVE drivers can be updated");
        }
        if (req.getAvailability() != null) {
            // Prevent manually setting ASSIGNED/ON_TRIP — those are system-managed
            if (req.getAvailability() == DriverAvailability.ASSIGNED
                    || req.getAvailability() == DriverAvailability.ON_TRIP) {
                throw CarrierFleetException.invalid(
                        "ASSIGNED and ON_TRIP availability are system-managed and cannot be set manually");
            }
            cd.setAvailability(req.getAvailability());
        }
        if (req.getNotes() != null) {
            cd.setNotes(clean(req.getNotes()));
        }
        cd = driverRepo.saveAndFlush(cd);
        return CarrierDriverResponse.from(cd, requireUser(cd.getDriverUserId()));
    }

    @Override
    @Transactional
    public CarrierDriverResponse activateDriver(Long companyId, Long carrierDriverId, User caller) {
        CarrierDriver cd = requireDriver(companyId, carrierDriverId);
        if (cd.getStatus() == CarrierDriverStatus.ACTIVE) {
            return CarrierDriverResponse.from(cd, requireUser(cd.getDriverUserId()));
        }
        if (cd.getStatus() == CarrierDriverStatus.REMOVED) {
            throw CarrierFleetException.invalid("REMOVED drivers must be re-added, not re-activated");
        }
        cd.setStatus(CarrierDriverStatus.ACTIVE);
        cd.setAvailability(DriverAvailability.AVAILABLE);
        cd.setLeftAt(null);
        cd = driverRepo.saveAndFlush(cd);
        log.info("activateDriver: carrierDriverId={} company={}", carrierDriverId, companyId);
        return CarrierDriverResponse.from(cd, requireUser(cd.getDriverUserId()));
    }

    @Override
    @Transactional
    public CarrierDriverResponse deactivateDriver(Long companyId, Long carrierDriverId, User caller) {
        CarrierDriver cd = requireDriver(companyId, carrierDriverId);
        if (cd.getStatus() == CarrierDriverStatus.INACTIVE) {
            return CarrierDriverResponse.from(cd, requireUser(cd.getDriverUserId()));
        }
        if (cd.getStatus() == CarrierDriverStatus.REMOVED) {
            throw CarrierFleetException.invalid("Cannot deactivate a REMOVED driver");
        }
        // End any active assignment before deactivating
        endActiveAssignmentIfPresent(cd, caller, "Driver deactivated");
        cd.setStatus(CarrierDriverStatus.INACTIVE);
        cd.setAvailability(DriverAvailability.UNAVAILABLE);
        cd = driverRepo.saveAndFlush(cd);
        log.info("deactivateDriver: carrierDriverId={} company={}", carrierDriverId, companyId);
        return CarrierDriverResponse.from(cd, requireUser(cd.getDriverUserId()));
    }

    @Override
    @Transactional
    public CarrierDriverResponse removeDriver(Long companyId, Long carrierDriverId, User caller) {
        CarrierDriver cd = requireDriver(companyId, carrierDriverId);
        if (cd.getStatus() == CarrierDriverStatus.REMOVED) {
            return CarrierDriverResponse.from(cd, requireUser(cd.getDriverUserId()));
        }
        // End any active assignment before removing
        endActiveAssignmentIfPresent(cd, caller, "Driver removed from company");
        cd.setStatus(CarrierDriverStatus.REMOVED);
        cd.setAvailability(DriverAvailability.UNAVAILABLE);
        cd.setLeftAt(LocalDateTime.now());
        cd = driverRepo.saveAndFlush(cd);
        log.info("removeDriver: carrierDriverId={} company={} — User account preserved",
                carrierDriverId, companyId);
        return CarrierDriverResponse.from(cd, requireUser(cd.getDriverUserId()));
    }

    // ── Company Vehicles ──────────────────────────────────────────────────────

    @Override
    @Transactional
    public CompanyVehicleResponse registerVehicle(Long companyId, RegisterCompanyVehicleRequest req, User caller) {
        if (vehicleRepo.existsByPlateNumber(req.getPlateNumber().toUpperCase(Locale.ROOT).strip())) {
            throw CarrierFleetException.conflict("Plate number already registered: " + req.getPlateNumber());
        }
        Vehicle.VehicleCategory category = null;
        if (req.getVehicleCategory() != null) {
            try {
                category = Vehicle.VehicleCategory.valueOf(req.getVehicleCategory().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw CarrierFleetException.invalid("vehicleCategory must be HEAVY or LIGHT");
            }
        }
        Vehicle v = Vehicle.builder()
                .userId(caller.getId())          // creator as placeholder until driver is assigned
                .fleetOwnerId(caller.getId())    // legacy fleet owner field
                .carrierCompanyId(companyId)
                .truckType(req.getTruckType().strip())
                .plateNumber(req.getPlateNumber().toUpperCase(Locale.ROOT).strip())
                .capacity(req.getCapacity())
                .vehicleCategory(category)
                .currentCity(req.getCurrentCity())
                .availabilityStatus("available")
                .build();
        v = vehicleRepo.saveAndFlush(v);
        log.info("registerVehicle: vehicleId={} plate={} company={}", v.getId(), v.getPlateNumber(), companyId);
        return CompanyVehicleResponse.from(v);
    }

    @Override
    public Page<CompanyVehicleResponse> listVehicles(Long companyId, Pageable pageable) {
        return vehicleRepo.findByCarrierCompanyId(companyId, pageable)
                .map(CompanyVehicleResponse::from);
    }

    @Override
    public CompanyVehicleResponse getVehicle(Long companyId, Long vehicleId) {
        return CompanyVehicleResponse.from(requireVehicle(companyId, vehicleId));
    }

    @Override
    @Transactional
    public CompanyVehicleResponse updateVehicle(Long companyId, Long vehicleId,
                                                UpdateCompanyVehicleRequest req, User caller) {
        Vehicle v = requireVehicle(companyId, vehicleId);
        if (req.getTruckType() != null)       v.setTruckType(req.getTruckType().strip());
        if (req.getCapacity() != null)        v.setCapacity(req.getCapacity());
        if (req.getCurrentCity() != null)     v.setCurrentCity(req.getCurrentCity().strip());
        if (req.getVehicleCategory() != null) {
            try {
                v.setVehicleCategory(Vehicle.VehicleCategory.valueOf(
                        req.getVehicleCategory().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                throw CarrierFleetException.invalid("vehicleCategory must be HEAVY or LIGHT");
            }
        }
        if (req.getAvailability() != null) {
            setVehicleAvailabilityInternal(v, req.getAvailability());
        }
        v = vehicleRepo.saveAndFlush(v);
        return CompanyVehicleResponse.from(v);
    }

    @Override
    @Transactional
    public CompanyVehicleResponse setVehicleAvailability(Long companyId, Long vehicleId,
                                                         VehicleAvailability availability, User caller) {
        Vehicle v = requireVehicle(companyId, vehicleId);
        // ASSIGNED and ON_TRIP are system-managed
        if (availability == VehicleAvailability.ASSIGNED || availability == VehicleAvailability.ON_TRIP) {
            throw CarrierFleetException.invalid(
                    "ASSIGNED and ON_TRIP availability are system-managed and cannot be set manually");
        }
        setVehicleAvailabilityInternal(v, availability);
        v = vehicleRepo.saveAndFlush(v);
        return CompanyVehicleResponse.from(v);
    }

    // ── Driver ↔ Vehicle Assignments ──────────────────────────────────────────

    @Override
    @Transactional
    public AssignmentResponse assign(Long companyId, CreateAssignmentRequest req, User caller) {
        // Validate driver belongs to company and is ACTIVE
        CarrierDriver cd = requireDriver(companyId, req.getCarrierDriverId());
        if (cd.getStatus() != CarrierDriverStatus.ACTIVE) {
            throw CarrierFleetException.invalid("Driver must be ACTIVE to be assigned");
        }
        if (cd.getAvailability() == DriverAvailability.ON_TRIP) {
            throw CarrierFleetException.invalid("Driver is currently ON_TRIP and cannot be reassigned");
        }
        if (cd.getAvailability() == DriverAvailability.UNAVAILABLE) {
            throw CarrierFleetException.invalid("Driver is marked UNAVAILABLE");
        }

        // Validate vehicle belongs to company and is approvable
        Vehicle vehicle = requireVehicle(companyId, req.getVehicleId());
        if (vehicle.getApprovalStatus() != Vehicle.VehicleApprovalStatus.APPROVED) {
            throw CarrierFleetException.invalid(
                    "Vehicle must be APPROVED before assignment (current: "
                    + vehicle.getApprovalStatus() + ")");
        }
        if ("on_trip".equalsIgnoreCase(vehicle.getAvailabilityStatus())) {
            throw CarrierFleetException.invalid("Vehicle is currently ON_TRIP and cannot be reassigned");
        }
        if ("maintenance".equalsIgnoreCase(vehicle.getAvailabilityStatus())) {
            throw CarrierFleetException.invalid("Vehicle is under MAINTENANCE");
        }
        if ("unavailable".equalsIgnoreCase(vehicle.getAvailabilityStatus())) {
            throw CarrierFleetException.invalid("Vehicle is marked UNAVAILABLE");
        }

        // End any existing active assignment for this driver (pessimistic lock)
        var existingDriverAssignment = assignmentRepo.lockByCarrierDriverIdActive(cd.getId());
        if (existingDriverAssignment.isPresent()) {
            var old = existingDriverAssignment.get();
            old.setStatus(AssignmentStatus.ENDED);
            old.setEndedAt(LocalDateTime.now());
            old.setEndedBy(caller.getId());
            old.setNotes("Superseded by new assignment");
            assignmentRepo.save(old);
            // Release old vehicle
            vehicleRepo.findById(old.getVehicleId()).ifPresent(ov -> {
                ov.setAvailabilityStatus("available");
                vehicleRepo.save(ov);
            });
        }

        // End any existing active assignment for this vehicle
        assignmentRepo.findByVehicleIdAndStatus(vehicle.getId(), AssignmentStatus.ACTIVE)
                .ifPresent(old -> {
                    old.setStatus(AssignmentStatus.ENDED);
                    old.setEndedAt(LocalDateTime.now());
                    old.setEndedBy(caller.getId());
                    old.setNotes("Superseded by new assignment");
                    assignmentRepo.save(old);
                    // Release old driver
                    driverRepo.findById(old.getCarrierDriverId()).ifPresent(od -> {
                        if (od.getStatus() == CarrierDriverStatus.ACTIVE) {
                            od.setAvailability(DriverAvailability.AVAILABLE);
                            driverRepo.save(od);
                        }
                    });
                });

        // Create the new assignment
        VehicleDriverAssignment assignment = VehicleDriverAssignment.builder()
                .carrierCompanyId(companyId)
                .carrierDriverId(cd.getId())
                .vehicleId(vehicle.getId())
                .status(AssignmentStatus.ACTIVE)
                .assignedBy(caller.getId())
                .assignedAt(LocalDateTime.now())
                .notes(clean(req.getNotes()))
                .build();
        try {
            assignment = assignmentRepo.saveAndFlush(assignment);
        } catch (DataIntegrityViolationException ex) {
            throw CarrierFleetException.conflict(
                    "Concurrent assignment conflict — please retry");
        }

        // Update driver and vehicle availability
        cd.setAvailability(DriverAvailability.ASSIGNED);
        driverRepo.save(cd);

        vehicle.setAvailabilityStatus("assigned");
        vehicle.setUserId(cd.getDriverUserId()); // keep legacy user_id in sync
        vehicleRepo.save(vehicle);

        log.info("assign: driverId={} vehicleId={} company={} by={}",
                cd.getId(), vehicle.getId(), companyId, caller.getId());
        return AssignmentResponse.from(assignment);
    }

    @Override
    @Transactional
    public AssignmentResponse endAssignment(Long companyId, Long assignmentId,
                                            EndAssignmentRequest req, User caller) {
        VehicleDriverAssignment a = assignmentRepo.findByIdAndCarrierCompanyId(assignmentId, companyId)
                .orElseThrow(() -> CarrierFleetException.notFound("Assignment"));
        if (a.getStatus() != AssignmentStatus.ACTIVE) {
            throw CarrierFleetException.invalid("Assignment is already " + a.getStatus());
        }
        a.setStatus(AssignmentStatus.ENDED);
        a.setEndedAt(LocalDateTime.now());
        a.setEndedBy(caller.getId());
        if (req != null && req.getNotes() != null) {
            a.setNotes(clean(req.getNotes()));
        }
        a = assignmentRepo.saveAndFlush(a);

        // Restore driver availability to AVAILABLE
        driverRepo.findById(a.getCarrierDriverId()).ifPresent(cd -> {
            if (cd.getStatus() == CarrierDriverStatus.ACTIVE
                    && cd.getAvailability() == DriverAvailability.ASSIGNED) {
                cd.setAvailability(DriverAvailability.AVAILABLE);
                driverRepo.save(cd);
            }
        });

        // Restore vehicle availability to available
        vehicleRepo.findById(a.getVehicleId()).ifPresent(v -> {
            if ("assigned".equalsIgnoreCase(v.getAvailabilityStatus())) {
                v.setAvailabilityStatus("available");
                vehicleRepo.save(v);
            }
        });

        log.info("endAssignment: assignmentId={} company={} by={}", assignmentId, companyId, caller.getId());
        return AssignmentResponse.from(a);
    }

    @Override
    public AssignmentResponse getAssignment(Long companyId, Long assignmentId) {
        return AssignmentResponse.from(
                assignmentRepo.findByIdAndCarrierCompanyId(assignmentId, companyId)
                        .orElseThrow(() -> CarrierFleetException.notFound("Assignment")));
    }

    @Override
    public Page<AssignmentResponse> listAssignments(Long companyId, Pageable pageable) {
        return assignmentRepo.findByCarrierCompanyIdOrderByAssignedAtDesc(companyId, pageable)
                .map(AssignmentResponse::from);
    }

    @Override
    public List<AssignmentResponse> driverAssignmentHistory(Long companyId, Long carrierDriverId) {
        requireDriver(companyId, carrierDriverId); // ownership check
        return assignmentRepo.findByCarrierDriverIdOrderByAssignedAtDesc(carrierDriverId)
                .stream().map(AssignmentResponse::from).toList();
    }

    @Override
    public List<AssignmentResponse> vehicleAssignmentHistory(Long companyId, Long vehicleId) {
        requireVehicle(companyId, vehicleId); // ownership check
        return assignmentRepo.findByVehicleIdOrderByAssignedAtDesc(vehicleId)
                .stream().map(AssignmentResponse::from).toList();
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private CarrierDriver requireDriver(Long companyId, Long carrierDriverId) {
        return driverRepo.findByIdAndCarrierCompanyId(carrierDriverId, companyId)
                .orElseThrow(() -> CarrierFleetException.notFound("Carrier driver"));
    }

    private Vehicle requireVehicle(Long companyId, Long vehicleId) {
        return vehicleRepo.findByIdAndCarrierCompanyId(vehicleId, companyId)
                .orElseThrow(() -> CarrierFleetException.notFound("Company vehicle"));
    }

    private User requireUser(Long userId) {
        return userRepo.findById(userId)
                .orElseThrow(() -> CarrierFleetException.notFound("User"));
    }

    /** Set vehicle availabilityStatus from enum (maps to lowercase DB strings). */
    private void setVehicleAvailabilityInternal(Vehicle v, VehicleAvailability avail) {
        v.setAvailabilityStatus(avail.name().toLowerCase(Locale.ROOT));
    }

    /** End any active assignment tied to this driver, freeing the vehicle. */
    private void endActiveAssignmentIfPresent(CarrierDriver cd, User caller, String reason) {
        assignmentRepo.findByCarrierDriverIdAndStatus(cd.getId(), AssignmentStatus.ACTIVE)
                .ifPresent(a -> {
                    a.setStatus(AssignmentStatus.ENDED);
                    a.setEndedAt(LocalDateTime.now());
                    a.setEndedBy(caller.getId());
                    a.setNotes(reason);
                    assignmentRepo.save(a);
                    // Release vehicle
                    vehicleRepo.findById(a.getVehicleId()).ifPresent(v -> {
                        if ("assigned".equalsIgnoreCase(v.getAvailabilityStatus())) {
                            v.setAvailabilityStatus("available");
                            vehicleRepo.save(v);
                        }
                    });
                });
    }

    private CarrierDriverStatus parseDriverStatus(String raw, CarrierDriverStatus defaultStatus) {
        if (raw == null || raw.isBlank()) return defaultStatus;
        try {
            return CarrierDriverStatus.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw CarrierFleetException.invalid("Invalid driver status: " + raw);
        }
    }

    private String clean(String s) {
        return (s == null || s.isBlank()) ? null : s.strip();
    }
}
