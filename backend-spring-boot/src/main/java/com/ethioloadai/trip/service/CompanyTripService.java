package com.ethioloadai.trip.service;

import com.ethioloadai.carrier.team.entity.CompanyRole;
import com.ethioloadai.carrier.team.service.CompanyContext;
import com.ethioloadai.carrier.team.service.CompanyContextService;
import com.ethioloadai.freight.service.FreightVisibilityProperties;
import com.ethioloadai.trip.dto.TripResponse;
import com.ethioloadai.trip.entity.Trip;
import com.ethioloadai.trip.exception.TripNotFoundException;
import com.ethioloadai.trip.mapper.TripMapper;
import com.ethioloadai.trip.repository.TripRepository;
import com.ethioloadai.trip.repository.TripVisibilitySpecifications;
import com.ethioloadai.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;

@Service
@RequiredArgsConstructor
public class CompanyTripService {

    private final TripRepository tripRepository;
    private final CompanyContextService companyContextService;
    private final FreightVisibilityProperties visibilityProperties;
    private final TripMapper tripMapper;

    @Transactional(readOnly = true)
    public Page<TripResponse> getTrips(
            Long companyId,
            Collection<Trip.TripStatus> statuses,
            Trip.TripType type,
            Pageable pageable,
            User caller) {
        CompanyContext context = companyContextService.resolveForCompany(companyId, caller);
        Specification<Trip> specification = visibility(companyId);
        if (statuses != null && !statuses.isEmpty()) {
            specification = specification.and(TripVisibilitySpecifications.withStatuses(statuses));
        }
        if (type != null) {
            specification = specification.and(TripVisibilitySpecifications.withType(type));
        }
        boolean includeFinancial = canViewFinancials(context.role());
        Page<Trip> trips = tripRepository.findAll(specification, pageable);
        loadStops(trips);
        return trips.map(trip -> tripMapper.toCompanyResponse(trip, companyId, includeFinancial));
    }

    @Transactional(readOnly = true)
    public TripResponse getTrip(Long companyId, Long tripId, User caller) {
        CompanyContext context = companyContextService.resolveForCompany(companyId, caller);
        Specification<Trip> specification = visibility(companyId)
                .and((root, query, builder) -> builder.equal(root.get("id"), tripId));
        Trip trip = tripRepository.findAll(specification, PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .orElseThrow(() -> TripNotFoundException.withId(tripId));
        loadStops(java.util.List.of(trip));
        return tripMapper.toCompanyResponse(trip, companyId, canViewFinancials(context.role()));
    }

    private Specification<Trip> visibility(Long companyId) {
        return TripVisibilitySpecifications.companyScope(companyId)
                .and(TripVisibilitySpecifications.withinHistoryWindow(
                        visibilityProperties.getCarrierHistoryDays(), LocalDateTime.now()));
    }

    private void loadStops(Page<Trip> trips) {
        loadStops(trips.getContent());
    }

    private void loadStops(Collection<Trip> trips) {
        var tripIds = trips.stream().map(Trip::getId).toList();
        if (!tripIds.isEmpty()) {
            tripRepository.findAllWithStopsByIdIn(tripIds);
        }
    }

    private boolean canViewFinancials(CompanyRole role) {
        return role == CompanyRole.CARRIER_OWNER || role == CompanyRole.FLEET_MANAGER;
    }
}
