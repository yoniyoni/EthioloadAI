# Milestone 6: Phase 3 - Location Update Contract Design

## Objective

Design a dedicated location-update request/DTO that contains only information actually required by the tracking system. Do NOT expose JPA entities.

## Current Implementation Analysis

### Existing AddGpsLocationRequest
```java
public class AddGpsLocationRequest {
    @NotNull @DecimalMin("-90") @DecimalMax("90") 
    @Digits(integer = 2, fraction = 7)
    private BigDecimal latitude;
    
    @NotNull @DecimalMin("-180") @DecimalMax("180")
    @Digits(integer = 3, fraction = 7) 
    private BigDecimal longitude;
    
    @DecimalMin("0") @DecimalMax("200")
    private BigDecimal speed;
    
    @DecimalMin("0") @DecimalMax("1000")
    private BigDecimal accuracy;
    
    @NotNull
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    private LocalDateTime timestamp;
}
```

### Fields Required Analysis

Based on the audit findings and established system requirements:

**REQUIRED Fields:**
1. **vehicleId** - Explicit vehicle identification (Critical from Phase 2)
2. **latitude** - Geographic position (Essential for tracking)
3. **longitude** - Geographic position (Essential for tracking)  
4. **timestamp** - When GPS reading was taken (Essential for chronological ordering)

**OPTIONAL Fields (Established in system):**
5. **speed** - Vehicle speed in km/h (Used in RouteDataService analytics)
6. **accuracy** - GPS accuracy in meters (Used for data quality validation)
7. **heading** - Vehicle direction in degrees (Referenced in GpsLocationData record)

**NOT REQUIRED (No established purpose):**
- altitude - Not used in current system
- satelliteCount - Not referenced anywhere
- dilutionOfPrecision - Not implemented
- deviceId - Vehicle.id provides identification

## Enhanced Location Update Contract

### LocationUpdateRequest DTO

```java
package com.ethioloadai.tracking.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Real-time GPS location update request for vehicle tracking.
 * Used for WebSocket and REST GPS updates.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationUpdateRequest {
    
    /**
     * Vehicle transmitting the GPS location.
     * REQUIRED: Explicit vehicle identification prevents ambiguous selection.
     */
    @NotNull(message = "Vehicle ID is required for GPS tracking")
    @Positive(message = "Vehicle ID must be positive")
    private Long vehicleId;
    
    /**
     * GPS latitude in decimal degrees.
     * REQUIRED: Geographic position for tracking.
     * Precision: DECIMAL(10,8) allows ±90° with ~1mm accuracy.
     */
    @NotNull(message = "Latitude is required") 
    @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90 degrees")
    @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90 degrees")
    @Digits(integer = 2, fraction = 8, message = "Latitude precision: 2 integer, 8 decimal places")
    private BigDecimal latitude;
    
    /**
     * GPS longitude in decimal degrees.
     * REQUIRED: Geographic position for tracking.
     * Precision: DECIMAL(11,8) allows ±180° with ~1mm accuracy.
     */
    @NotNull(message = "Longitude is required")
    @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180 degrees") 
    @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180 degrees")
    @Digits(integer = 3, fraction = 8, message = "Longitude precision: 3 integer, 8 decimal places")
    private BigDecimal longitude;
    
    /**
     * GPS reading timestamp.
     * REQUIRED: Chronological ordering and out-of-order detection.
     */
    @NotNull(message = "Timestamp is required")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    private LocalDateTime timestamp;
    
    /**
     * Vehicle speed in km/h.
     * OPTIONAL: Used for route analytics and validation.
     */
    @DecimalMin(value = "0.0", message = "Speed cannot be negative")
    @DecimalMax(value = "200.0", message = "Speed cannot exceed 200 km/h") 
    @Digits(integer = 3, fraction = 2, message = "Speed precision: 3 integer, 2 decimal places")
    private BigDecimal speed;
    
    /**
     * GPS accuracy radius in meters.
     * OPTIONAL: Used for data quality assessment.
     */
    @DecimalMin(value = "0.0", message = "Accuracy cannot be negative")
    @DecimalMax(value = "1000.0", message = "Accuracy cannot exceed 1000 meters")
    @Digits(integer = 4, fraction = 2, message = "Accuracy precision: 4 integer, 2 decimal places")
    private BigDecimal accuracy;
    
    /**
     * Vehicle heading/direction in degrees (0-360).
     * OPTIONAL: Used for route analytics and direction tracking.
     */
    @DecimalMin(value = "0.0", message = "Heading must be between 0 and 360 degrees")
    @DecimalMax(value = "360.0", message = "Heading must be between 0 and 360 degrees")
    @Digits(integer = 3, fraction = 2, message = "Heading precision: 3 integer, 2 decimal places")  
    private BigDecimal heading;
}
```

### API Endpoint Design

**REST Endpoint:**
```java
POST /api/trips/{tripId}/location
Content-Type: application/json
Authorization: Bearer <jwt-token>

{
    "vehicleId": 123,
    "latitude": 9.145000,
    "longitude": 40.489673, 
    "timestamp": "2026-09-02T15:30:00Z",
    "speed": 45.5,
    "accuracy": 8.2,
    "heading": 270.0
}
```

**WebSocket Message (Future):**
```java
SEND /topic/trip/123/location
{
    "vehicleId": 123,
    "latitude": 9.145000,
    "longitude": 40.489673,
    "timestamp": "2026-09-02T15:30:00Z", 
    "speed": 45.5,
    "accuracy": 8.2,
    "heading": 270.0
}
```

## Field Validation Specification

### Latitude Validation
```java
@DecimalMin("-90.0") @DecimalMax("90.0")
@Digits(integer = 2, fraction = 8)
```
- **Range**: -90.0 to +90.0 degrees (valid geographic bounds)
- **Precision**: DECIMAL(10,8) = 2 integer + 8 decimal places
- **Accuracy**: ~1mm precision at equator

### Longitude Validation  
```java
@DecimalMin("-180.0") @DecimalMax("180.0")
@Digits(integer = 3, fraction = 8)
```
- **Range**: -180.0 to +180.0 degrees (valid geographic bounds)
- **Precision**: DECIMAL(11,8) = 3 integer + 8 decimal places  
- **Accuracy**: ~1mm precision at equator

### Timestamp Validation
```java
@NotNull
@JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
```
- **Format**: ISO 8601 UTC format (2026-09-02T15:30:00Z)
- **Required**: Prevents null/missing timestamps
- **Future Validation**: Server-side validation prevents future timestamps

### Speed Validation (Optional)
```java
@DecimalMin("0.0") @DecimalMax("200.0")
@Digits(integer = 3, fraction = 2)
```
- **Range**: 0.0 to 200.0 km/h (reasonable vehicle speed limits)
- **Precision**: DECIMAL(5,2) = 3 integer + 2 decimal places
- **Optional**: Null values allowed

### Accuracy Validation (Optional)
```java
@DecimalMin("0.0") @DecimalMax("1000.0")
@Digits(integer = 4, fraction = 2)
```
- **Range**: 0.0 to 1000.0 meters (GPS accuracy range)
- **Precision**: DECIMAL(6,2) = 4 integer + 2 decimal places
- **Optional**: Null values allowed

### Heading Validation (Optional)
```java
@DecimalMin("0.0") @DecimalMax("360.0") 
@Digits(integer = 3, fraction = 2)
```
- **Range**: 0.0 to 360.0 degrees (compass bearing)
- **Precision**: DECIMAL(5,2) = 3 integer + 2 decimal places
- **Optional**: Null values allowed

## Integration with Existing System

### RouteDataService.GpsLocationData Mapping
```java
// Convert DTO to existing value object
GpsLocationData gpsData = new GpsLocationData(
    request.getLatitude(),
    request.getLongitude(), 
    null, // altitude not provided
    request.getAccuracy(),
    request.getSpeed(),
    request.getHeading(),
    request.getTimestamp(),
    Map.of() // metadata empty for now
);
```

### Trip Route Data Format (JSONB)
```json
{
    "lat": 9.145000,
    "lng": 40.489673,
    "ts": "2026-09-02T15:30:00Z",
    "spd": 45.5,
    "acc": 8.2,
    "hdg": 270.0
}
```

### Vehicle Current Location Update
```java
// Update vehicle's current position
vehicle.setLatitude(request.getLatitude());
vehicle.setLongitude(request.getLongitude());
vehicle.setLastLocationAt(LocalDateTime.now()); // Server timestamp
```

## Error Response Contract

### Validation Error Response
```json
{
    "timestamp": "2026-09-02T15:30:00Z",
    "status": 400,
    "error": "Bad Request", 
    "message": "Validation failed",
    "path": "/api/trips/123/location",
    "violations": [
        {
            "field": "latitude",
            "message": "Latitude must be between -90 and 90 degrees",
            "rejectedValue": "91.5"
        },
        {
            "field": "vehicleId", 
            "message": "Vehicle ID is required for GPS tracking",
            "rejectedValue": null
        }
    ]
}
```

### Authorization Error Response
```json
{
    "timestamp": "2026-09-02T15:30:00Z",
    "status": 403,
    "error": "Forbidden",
    "message": "Vehicle 123 does not belong to user 456",
    "path": "/api/trips/123/location"
}
```

## Backward Compatibility

### Migration Strategy
1. **Phase 1**: Accept both old and new DTO formats
2. **Phase 2**: Deprecate old AddGpsLocationRequest  
3. **Phase 3**: Remove legacy DTO after client migration
4. **Phase 4**: WebSocket integration using same DTO

### Legacy Support (Temporary)
```java
// Support old format without vehicleId
if (request.getVehicleId() == null) {
    // Legacy fallback - select driver's primary vehicle
    // TODO: Remove after client migration
    Vehicle primaryVehicle = vehicleService.getPrimaryVehicleForDriver(user.getId());
    request.setVehicleId(primaryVehicle.getId());
}
```

## Usage Examples

### Valid Request
```bash
curl -X POST /api/trips/123/location \
  -H "Authorization: Bearer eyJ..." \
  -H "Content-Type: application/json" \
  -d '{
    "vehicleId": 456,
    "latitude": 9.145000,
    "longitude": 40.489673,
    "timestamp": "2026-09-02T15:30:00Z",
    "speed": 45.5,
    "accuracy": 8.2,
    "heading": 270.0
  }'
```

### Minimal Valid Request
```bash
curl -X POST /api/trips/123/location \
  -H "Authorization: Bearer eyJ..." \
  -H "Content-Type: application/json" \
  -d '{
    "vehicleId": 456,
    "latitude": 9.145000, 
    "longitude": 40.489673,
    "timestamp": "2026-09-02T15:30:00Z"
  }'
```

## Conclusion

The LocationUpdateRequest contract:

1. **Contains only required tracking information** - No unnecessary fields
2. **Explicitly identifies transmitting vehicle** - Eliminates vehicle selection ambiguity  
3. **Preserves existing validation patterns** - Compatible with current RouteDataService
4. **Supports optional enhancements** - Speed, accuracy, heading for analytics
5. **Enables WebSocket integration** - Same DTO works for REST and WebSocket
6. **Maintains security boundaries** - No JPA entity exposure

This contract provides a clean, validated interface for GPS tracking while building on established system patterns.