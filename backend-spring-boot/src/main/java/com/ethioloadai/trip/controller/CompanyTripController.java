package com.ethioloadai.trip.controller;

import com.ethioloadai.trip.dto.TripResponse;
import com.ethioloadai.trip.entity.Trip;
import com.ethioloadai.trip.service.CompanyTripService;
import com.ethioloadai.user.entity.User;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@RestController
@Validated
@RequestMapping("/carrier-companies/{companyId}/trips")
@RequiredArgsConstructor
public class CompanyTripController {

    private static final int MAX_PAGE_SIZE = 100;
    private static final List<String> SORT_FIELDS = List.of("id", "startTime", "tripStatus");
    private final CompanyTripService companyTripService;

    @GetMapping
    public ResponseEntity<Page<TripResponse>> getTrips(
            @PathVariable Long companyId,
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) int size,
            @RequestParam(defaultValue = "startTime") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @AuthenticationPrincipal User caller) {
        if (!SORT_FIELDS.contains(sortBy)) {
            return ResponseEntity.badRequest().build();
        }

        List<Trip.TripStatus> statuses;
        Trip.TripType tripType;
        try {
            statuses = parseStatuses(status);
            tripType = type == null || type.isBlank()
                    ? null
                    : Trip.TripType.valueOf(type.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().build();
        }

        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir)
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort sort = Sort.by(direction, sortBy);
        if (!"id".equals(sortBy)) {
            sort = sort.and(Sort.by(direction, "id"));
        }
        Pageable pageable = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE), sort);
        return ResponseEntity.ok(companyTripService.getTrips(
                companyId, statuses, tripType, pageable, caller));
    }

    @GetMapping("/{tripId}")
    public ResponseEntity<TripResponse> getTrip(
            @PathVariable Long companyId,
            @PathVariable Long tripId,
            @AuthenticationPrincipal User caller) {
        return ResponseEntity.ok(companyTripService.getTrip(companyId, tripId, caller));
    }

    private List<Trip.TripStatus> parseStatuses(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<Trip.TripStatus> statuses = new ArrayList<>();
        for (String value : values) {
            statuses.add(Trip.TripStatus.valueOf(value.trim().toUpperCase(Locale.ROOT)));
        }
        return statuses.stream().distinct().toList();
    }
}
