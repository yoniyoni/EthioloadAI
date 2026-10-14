package com.ethioloadai.tracking.service;

import com.ethioloadai.tracking.dto.LocationUpdateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 12 / Phase 19 – LocationUpdateFilterService unit tests.
 *
 * Uses the real implementation (no mocks needed – it is stateless except for
 * the per-trip cache that is reset between tests).
 */
class LocationUpdateFilterServiceTest {

    private LocationUpdateFilterServiceImpl filter;

    private static final Long   TRIP_ID    = 1L;
    private static final Long   VEHICLE_ID = 10L;

    // Addis Ababa, clearly inside Ethiopia
    private static final BigDecimal LAT_ADDIS = new BigDecimal("9.0300000");
    private static final BigDecimal LON_ADDIS = new BigDecimal("38.7400000");

    @BeforeEach
    void setUp() {
        filter = new LocationUpdateFilterServiceImpl();
    }

    // ── Bounding-box ──────────────────────────────────────────────────────────

    @Test
    void accept_validEthiopianCoordinates() {
        LocationUpdateFilterService.FilterResult r =
            filter.filter(TRIP_ID, request(LAT_ADDIS, LON_ADDIS, null));
        assertThat(r.isAccepted()).isTrue();
        assertThat(r.filterCode()).isEqualTo("OK");
    }

    @Test
    void discard_coordinatesOutsideEthiopia() {
        // London
        LocationUpdateFilterService.FilterResult r =
            filter.filter(TRIP_ID, request(new BigDecimal("51.5074"), new BigDecimal("-0.1278"), null));
        assertThat(r.isAccepted()).isFalse();
        assertThat(r.filterCode()).isEqualTo("OUT_OF_BOUNDS");
    }

    @Test
    void discard_latitudeAboveEthiopia() {
        LocationUpdateFilterService.FilterResult r =
            filter.filter(TRIP_ID, request(new BigDecimal("16.0"), LON_ADDIS, null));
        assertThat(r.isAccepted()).isFalse();
        assertThat(r.filterCode()).isEqualTo("OUT_OF_BOUNDS");
    }

    // ── Accuracy ──────────────────────────────────────────────────────────────

    @Test
    void accept_goodAccuracy() {
        LocationUpdateFilterService.FilterResult r =
            filter.filter(TRIP_ID, request(LAT_ADDIS, LON_ADDIS, new BigDecimal("30.0")));
        assertThat(r.isAccepted()).isTrue();
        assertThat(r.action()).isEqualTo(LocationUpdateFilterService.FilterAction.ACCEPT);
    }

    @Test
    void warnAndAccept_degradedAccuracy() {
        LocationUpdateFilterService.FilterResult r =
            filter.filter(TRIP_ID, request(LAT_ADDIS, LON_ADDIS, new BigDecimal("150.0")));
        assertThat(r.isAccepted()).isTrue();
        assertThat(r.action()).isEqualTo(LocationUpdateFilterService.FilterAction.WARN_AND_ACCEPT);
        assertThat(r.filterCode()).isEqualTo("ACCURACY_DEGRADED");
    }

    @Test
    void discard_accuracyTooPoor() {
        LocationUpdateFilterService.FilterResult r =
            filter.filter(TRIP_ID, request(LAT_ADDIS, LON_ADDIS, new BigDecimal("600.0")));
        assertThat(r.isAccepted()).isFalse();
        assertThat(r.filterCode()).isEqualTo("ACCURACY_TOO_POOR");
    }

    @Test
    void accept_nullAccuracy() {
        // No accuracy info → accept without warning
        LocationUpdateFilterService.FilterResult r =
            filter.filter(TRIP_ID, request(LAT_ADDIS, LON_ADDIS, null));
        assertThat(r.isAccepted()).isTrue();
    }

    // ── Duplicate detection ───────────────────────────────────────────────────

    @Test
    void discard_duplicateCoordinates() {
        LocalDateTime t = LocalDateTime.now();
        // First fix accepted
        filter.filter(TRIP_ID, requestAt(LAT_ADDIS, LON_ADDIS, null, t));

        // Same coords 5 seconds later — discard
        LocationUpdateFilterService.FilterResult r =
            filter.filter(TRIP_ID, requestAt(LAT_ADDIS, LON_ADDIS, null, t.plusSeconds(5)));
        assertThat(r.isAccepted()).isFalse();
        assertThat(r.filterCode()).isEqualTo("DUPLICATE_COORDS");
    }

    @Test
    void discard_sameTimestamp() {
        LocalDateTime t = LocalDateTime.now();
        filter.filter(TRIP_ID, requestAt(LAT_ADDIS, LON_ADDIS, null, t));

        // Slightly different coords but same timestamp
        LocationUpdateFilterService.FilterResult r =
            filter.filter(TRIP_ID, requestAt(
                LAT_ADDIS.add(new BigDecimal("0.001")), LON_ADDIS, null, t));
        assertThat(r.isAccepted()).isFalse();
        assertThat(r.filterCode()).isEqualTo("DUPLICATE_TIMESTAMP");
    }

    @Test
    void discard_tooFrequent() {
        LocalDateTime t = LocalDateTime.now();
        filter.filter(TRIP_ID, requestAt(LAT_ADDIS, LON_ADDIS, null, t));

        // 1 second gap — below minimum 2s
        LocationUpdateFilterService.FilterResult r =
            filter.filter(TRIP_ID, requestAt(
                LAT_ADDIS.add(new BigDecimal("0.001")), LON_ADDIS, null, t.plusSeconds(1)));
        assertThat(r.isAccepted()).isFalse();
        assertThat(r.filterCode()).isEqualTo("TOO_FREQUENT");
    }

    @Test
    void accept_legitimateMovement() {
        LocalDateTime t = LocalDateTime.now();
        filter.filter(TRIP_ID, requestAt(LAT_ADDIS, LON_ADDIS, null, t));

        // 30 seconds later, ~100m away
        LocationUpdateFilterService.FilterResult r =
            filter.filter(TRIP_ID, requestAt(
                new BigDecimal("9.0310000"), LON_ADDIS, null, t.plusSeconds(30)));
        assertThat(r.isAccepted()).isTrue();
    }

    // ── Teleportation guard ───────────────────────────────────────────────────

    @Test
    void discard_teleportation() {
        LocalDateTime t = LocalDateTime.now();
        // Start in Addis Ababa
        filter.filter(TRIP_ID, requestAt(LAT_ADDIS, LON_ADDIS, null, t));

        // "Jump" to Mekelle (~780km away in Ethiopia) in just 10 seconds — impossible speed
        // Mekelle is within Ethiopia bounding box so OUT_OF_BOUNDS won't fire first
        BigDecimal mekelleLatitude  = new BigDecimal("13.4967");
        BigDecimal mekelleLongitude = new BigDecimal("39.4753");
        LocationUpdateFilterService.FilterResult r =
            filter.filter(TRIP_ID, requestAt(mekelleLatitude, mekelleLongitude, null, t.plusSeconds(10)));
        assertThat(r.isAccepted()).isFalse();
        assertThat(r.filterCode()).isEqualTo("TELEPORTATION");
    }

    // ── State reset ───────────────────────────────────────────────────────────

    @Test
    void discard_outOfOrderFix() {
        LocalDateTime t = LocalDateTime.now();
        filter.filter(TRIP_ID, requestAt(LAT_ADDIS, LON_ADDIS, null, t));
        // Older fix arriving late — out-of-order
        BigDecimal slightlyDiff = LAT_ADDIS.add(new BigDecimal("0.001"));
        LocationUpdateFilterService.FilterResult r =
            filter.filter(TRIP_ID, requestAt(slightlyDiff, LON_ADDIS, null, t.minusSeconds(30)));
        assertThat(r.isAccepted()).isFalse();
        assertThat(r.filterCode()).isEqualTo("OUT_OF_ORDER");
    }

    @Test
    void accept_afterTripStateReset() {
        LocalDateTime t = LocalDateTime.now();
        filter.filter(TRIP_ID, requestAt(LAT_ADDIS, LON_ADDIS, null, t));

        filter.resetTripState(TRIP_ID);

        // Same coords accepted again after reset
        LocationUpdateFilterService.FilterResult r =
            filter.filter(TRIP_ID, requestAt(LAT_ADDIS, LON_ADDIS, null, t.plusSeconds(5)));
        assertThat(r.isAccepted()).isTrue();
    }

    @Test
    void differentTrips_independentState() {
        LocalDateTime t = LocalDateTime.now();
        // Trip 1 gets a fix
        filter.filter(1L, requestAt(LAT_ADDIS, LON_ADDIS, null, t));

        // Trip 2's first fix at the same coords should be accepted
        LocationUpdateFilterService.FilterResult r =
            filter.filter(2L, requestAt(LAT_ADDIS, LON_ADDIS, null, t.plusSeconds(5)));
        assertThat(r.isAccepted()).isTrue();
    }

    // ── FilterResult helpers ──────────────────────────────────────────────────

    @Test
    void filterResultAccept_isAccepted() {
        var r = LocationUpdateFilterService.FilterResult.accept();
        assertThat(r.isAccepted()).isTrue();
        assertThat(r.filterCode()).isEqualTo("OK");
    }

    @Test
    void filterResultDiscard_notAccepted() {
        var r = LocationUpdateFilterService.FilterResult.discard("bad", "BAD_CODE");
        assertThat(r.isAccepted()).isFalse();
        assertThat(r.filterCode()).isEqualTo("BAD_CODE");
    }

    @Test
    void filterResultWarnAndAccept_isAccepted() {
        var r = LocationUpdateFilterService.FilterResult.warnAndAccept("warn", "WARN_CODE");
        assertThat(r.isAccepted()).isTrue();
        assertThat(r.filterCode()).isEqualTo("WARN_CODE");
    }

    // ── Request builders ──────────────────────────────────────────────────────

    private LocationUpdateRequest request(BigDecimal lat, BigDecimal lon, BigDecimal accuracy) {
        return requestAt(lat, lon, accuracy, LocalDateTime.now());
    }

    private LocationUpdateRequest requestAt(BigDecimal lat, BigDecimal lon,
                                             BigDecimal accuracy, LocalDateTime timestamp) {
        return LocationUpdateRequest.builder()
            .vehicleId(VEHICLE_ID)
            .latitude(lat)
            .longitude(lon)
            .accuracy(accuracy)
            .timestamp(timestamp)
            .build();
    }
}
