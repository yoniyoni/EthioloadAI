package com.ethioloadai.trip.entity;

import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.trip.converter.TripStopStatusConverter;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A single stop within a multi-stop trip.
 *
 * <p>stop_order must be unique within the trip (DB constraint).
 * agreed_price contributes to the trip's total_amount.
 * cargo_request_id is nullable — stops may be operational (fuel, rest) rather than cargo stops.
 */
@Entity
@Table(name = "trip_stops")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripStop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    /** Optional — stop may not correspond to a formal cargo request. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cargo_request_id")
    private Freight cargoRequest;

    @Column(name = "stop_order", nullable = false)
    private Integer stopOrder;

    @Column(name = "location_name", nullable = false, length = 255)
    private String locationName;

    /** DECIMAL(10,8) — 8 decimal places gives ≈1mm GPS precision. */
    @Column(name = "pickup_lat", precision = 10, scale = 8)
    private BigDecimal pickupLat;

    /** DECIMAL(11,8) — 11 digits covers ±180° longitude with 8dp precision. */
    @Column(name = "pickup_lng", precision = 11, scale = 8)
    private BigDecimal pickupLng;

    /** Per-stop agreed price in ETB. */
    @Column(name = "agreed_price", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal agreedPrice = BigDecimal.ZERO;

    @Convert(converter = TripStopStatusConverter.class)
    @Column(name = "stop_status", nullable = false, length = 20)
    @Builder.Default
    private StopStatus stopStatus = StopStatus.PENDING;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "arrived_at")
    private LocalDateTime arrivedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // ── Enum ──────────────────────────────────────────────────────────────────

    public enum StopStatus {
        PENDING,
        ARRIVED,
        LOADED,
        COMPLETED
    }

    // ── Explicit accessors (supplement Lombok @Data for @Convert fields) ──────

    public Long getId()                        { return id; }
    public Trip getTrip()                      { return trip; }
    public Freight getCargoRequest()           { return cargoRequest; }
    public Integer getStopOrder()              { return stopOrder; }
    public String getLocationName()            { return locationName; }
    public BigDecimal getPickupLat()           { return pickupLat; }
    public BigDecimal getPickupLng()           { return pickupLng; }
    public BigDecimal getAgreedPrice()         { return agreedPrice; }
    public StopStatus getStopStatus()          { return stopStatus; }
    public String getNotes()                   { return notes; }
    public LocalDateTime getArrivedAt()        { return arrivedAt; }
    public LocalDateTime getCompletedAt()      { return completedAt; }
    public LocalDateTime getCreatedAt()        { return createdAt; }
    public LocalDateTime getUpdatedAt()        { return updatedAt; }
    public LocalDateTime getArrivalTime()      { return arrivedAt; }

    public void setId(Long id)                                   { this.id = id; }
    public void setTrip(Trip trip)                               { this.trip = trip; }
    public void setCargoRequest(Freight c)                       { this.cargoRequest = c; }
    public void setStopOrder(Integer stopOrder)                  { this.stopOrder = stopOrder; }
    public void setLocationName(String locationName)             { this.locationName = locationName; }
    public void setPickupLat(BigDecimal pickupLat)               { this.pickupLat = pickupLat; }
    public void setPickupLng(BigDecimal pickupLng)               { this.pickupLng = pickupLng; }
    public void setAgreedPrice(BigDecimal agreedPrice)           { this.agreedPrice = agreedPrice; }
    public void setStopStatus(StopStatus stopStatus)             { this.stopStatus = stopStatus; }
    public void setNotes(String notes)                           { this.notes = notes; }
    public void setArrivedAt(LocalDateTime arrivedAt)            { this.arrivedAt = arrivedAt; }
    public void setCompletedAt(LocalDateTime completedAt)        { this.completedAt = completedAt; }
}
