package com.ethioloadai.booking.service;

import com.ethioloadai.booking.dto.CompanyBookingResponse;
import com.ethioloadai.booking.entity.Booking;
import com.ethioloadai.booking.exception.BookingNotFoundException;
import com.ethioloadai.booking.repository.BookingRepository;
import com.ethioloadai.booking.repository.BookingVisibilitySpecifications;
import com.ethioloadai.carrier.team.entity.CompanyRole;
import com.ethioloadai.carrier.team.service.CompanyContext;
import com.ethioloadai.carrier.team.service.CompanyContextService;
import com.ethioloadai.freight.service.FreightVisibilityProperties;
import com.ethioloadai.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class CompanyBookingService {

    private final BookingRepository bookingRepository;
    private final CompanyContextService companyContextService;
    private final FreightVisibilityProperties visibilityProperties;

    @Transactional(readOnly = true)
    public Page<CompanyBookingResponse> getBookings(
            Long companyId, Booking.BookingStatus status, Pageable pageable, User caller) {
        CompanyContext context = companyContextService.resolveForCompany(companyId, caller);
        Specification<Booking> specification = visibility(companyId, status);
        boolean includeFinancial = canViewFinancials(context.role());
        return bookingRepository.findAll(specification, pageable)
                .map(booking -> toResponse(booking, companyId, includeFinancial));
    }

    @Transactional(readOnly = true)
    public CompanyBookingResponse getBooking(Long companyId, Long bookingId, User caller) {
        CompanyContext context = companyContextService.resolveForCompany(companyId, caller);
        Booking booking = bookingRepository.findOne(
                        visibility(companyId, null)
                                .and((root, query, builder) -> builder.equal(root.get("id"), bookingId)))
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
        return toResponse(booking, companyId, canViewFinancials(context.role()));
    }

    private Specification<Booking> visibility(Long companyId, Booking.BookingStatus status) {
        Specification<Booking> specification = BookingVisibilitySpecifications.companyScope(companyId)
                .and(BookingVisibilitySpecifications.withinHistoryWindow(
                        visibilityProperties.getCarrierHistoryDays(), LocalDateTime.now()));
        return status == null
                ? specification
                : specification.and(BookingVisibilitySpecifications.withStatus(status));
    }

    private CompanyBookingResponse toResponse(
            Booking booking, Long companyId, boolean includeFinancial) {
        return new CompanyBookingResponse(
                booking.getId(),
                companyId,
                booking.getCargoRequest().getId(),
                booking.getCargoRequest().getCargoDescription(),
                booking.getCargoRequest().getShipper() == null
                        ? null : booking.getCargoRequest().getShipper().getId(),
                booking.getCargoRequest().getShipper() == null
                        ? null : booking.getCargoRequest().getShipper().getFullName(),
                booking.getDriver() == null ? null : booking.getDriver().getId(),
                booking.getDriver() == null ? null : booking.getDriver().getFullName(),
                booking.getVehicle() == null ? null : booking.getVehicle().getId(),
                booking.getVehicle() == null ? null : booking.getVehicle().getPlateNumber(),
                booking.getVehicle() == null ? null : booking.getVehicle().getTruckType(),
                booking.getTrip() == null ? null : booking.getTrip().getId(),
                booking.getTrip() == null || booking.getTrip().getTripStatus() == null
                        ? null : booking.getTrip().getTripStatus().name(),
                booking.getBookingStatus() == null ? null : booking.getBookingStatus().name(),
                includeFinancial ? booking.getEstimatedPrice() : null,
                includeFinancial ? booking.getCommissionFee() : null,
                booking.getCreatedAt(),
                booking.getUpdatedAt());
    }

    private boolean canViewFinancials(CompanyRole role) {
        return role == CompanyRole.CARRIER_OWNER || role == CompanyRole.FLEET_MANAGER;
    }
}
