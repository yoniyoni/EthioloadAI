package com.ethioloadai.trip.entity;

import com.ethioloadai.booking.entity.Booking;
import com.ethioloadai.trip.converter.TripStatusConverter;
import com.ethioloadai.trip.converter.TripTypeConverter;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.vehicle.entity.Vehicle;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Represents an active or completed delivery trip.
 *
 * <p>One booking → at most one trip (enforced by DB unique constraint on booking_id).
 *
 * <p>route_data: stored as JSONB — a list of GPS breadcrumbs appended on each
 * driver location ping. The application layer MUST cap this list (e.g. last 500
 * points) before saving to prevent unbounded growth. Each element:
 * {@code {"lat": 9.03, "lng": 38.74, "ts": "2026-01-01T10:00:00Z"}}
 *
 * <p>For multi-stop trips, {@code totalStops} and {@code completedStops} track
 * overall progress. The actual stop records live in {@link TripStop}.
 */
@Entity
@Table(name = "trips")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Trip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** One-to-one with Booking — enforced by unique constraint on booking_id. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false, unique = true)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id", nullable = false)
    private User driver;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @Column(name = "start_location", nullable = false, length = 255)
    private String startLocation;

    @Column(name = "destination", nullable = false, length = 255)
    private String destination;

    /**
     * GPS breadcrumbs as JSONB. Cap at ~500 points per trip in application code.
     * Format: list of {lat, lng, ts} objects.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "route_data", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private List<Map<String, Object>> routeData = new ArrayList<>();

    @Convert(converter = TripStatusConverter.class)
    @Column(name = "trip_status", nullable = false, length = 40)
    @Builder.Default
    private TripStatus tripStatus = TripStatus.ONGOING;

    @Convert(converter = TripTypeConverter.class)
    @Column(name = "trip_type", nullable = false, length = 20)
    @Builder.Default
    private TripType tripType = TripType.SINGLE;

    @Column(name = "total_stops", nullable = false)
    @Builder.Default
    private Integer totalStops = 1;

    @Column(name = "completed_stops", nullable = false)
    @Builder.Default
    private Integer completedStops = 0;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    /** Phase 7 — set when driver marks arrival at destination. */
    @Column(name = "arrived_at")
    private LocalDateTime arrivedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipper_delivery_confirmed_by")
    private User shipperDeliveryConfirmedBy;

    @Column(name = "shipper_delivery_confirmed_at")
    private LocalDateTime shipperDeliveryConfirmedAt;

    /** Ordered list of stops for multi-stop trips. LAZY — loaded only when needed. */
    @OneToMany(mappedBy = "trip", fetch = FetchType.LAZY,
               cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("stopOrder ASC")
    @Builder.Default
    private List<TripStop> tripStops = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // ── Convenience ───────────────────────────────────────────────────────────

    public boolean isMultiStop() {
        return TripType.MULTI_STOP.equals(this.tripType);
    }

    /**
     * Sum of all stop agreed prices. Use only when tripStops are already
     * loaded to avoid a hidden query.
     */
    public BigDecimal getTotalAmount() {
        return tripStops.stream()
                .map(TripStop::getAgreedPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ── Explicit accessors (supplement Lombok @Data which can fail on JSONB/Convert fields) ──

    public Long getId()                          { return id; }
    public Booking getBooking()                  { return booking; }
    public User getDriver()                      { return driver; }
    public Vehicle getVehicle()                  { return vehicle; }
    public String getStartLocation()             { return startLocation; }
    public String getDestination()               { return destination; }
    public List<Map<String, Object>> getRouteData() { return routeData; }
    public TripStatus getTripStatus()            { return tripStatus; }
    public TripType getTripType()                { return tripType; }
    public Integer getTotalStops()               { return totalStops; }
    public Integer getCompletedStops()           { return completedStops; }
    public LocalDateTime getStartTime()          { return startTime; }
    public LocalDateTime getEndTime()            { return endTime; }
    public List<TripStop> getTripStops()         { return tripStops; }
    public List<TripStop> getStops()             { return tripStops; }
    public LocalDateTime getCreatedAt()          { return createdAt; }
    public LocalDateTime getUpdatedAt()          { return updatedAt; }

    public void setId(Long id)                               { this.id = id; }
    public void setBooking(Booking booking)                  { this.booking = booking; }
    public void setDriver(User driver)                       { this.driver = driver; }
    public void setVehicle(Vehicle vehicle)                  { this.vehicle = vehicle; }
    public void setStartLocation(String startLocation)       { this.startLocation = startLocation; }
    public void setDestination(String destination)           { this.destination = destination; }
    public void setRouteData(List<Map<String, Object>> r)    { this.routeData = r; }
    public void setTripStatus(TripStatus tripStatus)         { this.tripStatus = tripStatus; }
    public void setTripType(TripType tripType)               { this.tripType = tripType; }
    public void setTotalStops(Integer totalStops)            { this.totalStops = totalStops; }
    public void setCompletedStops(Integer completedStops)    { this.completedStops = completedStops; }
    public void setStartTime(LocalDateTime startTime)        { this.startTime = startTime; }
    public void setEndTime(LocalDateTime endTime)            { this.endTime = endTime; }
    public void setArrivedAt(LocalDateTime arrivedAt)        { this.arrivedAt = arrivedAt; }
    public void setShipperDeliveryConfirmedBy(User user)     { this.shipperDeliveryConfirmedBy = user; }
    public void setShipperDeliveryConfirmedAt(LocalDateTime at) { this.shipperDeliveryConfirmedAt = at; }
    public LocalDateTime getArrivedAt()                      { return arrivedAt; }
    public User getShipperDeliveryConfirmedBy()              { return shipperDeliveryConfirmedBy; }
    public LocalDateTime getShipperDeliveryConfirmedAt()     { return shipperDeliveryConfirmedAt; }
    public void setTripStops(List<TripStop> tripStops)       { this.tripStops = tripStops; }

    // ── Enums ─────────────────────────────────────────────────────────────────

    public enum TripStatus {
        /** Trip is active and driver is en route. Default initial status. */
        ONGOING,
        /** Phase 7 — driver has arrived at the destination. */
        ARRIVED,
        /** Phase 7 — proof of delivery submitted; awaiting shipper verification. */
        DELIVERY_PENDING_VERIFICATION,
        /** Trip is finished — all stops complete, delivery verified. Terminal state. */
        COMPLETED
    }

    public enum TripType {
        SINGLE,
        MULTI_STOP
    }
}
