# Milestone 6: Phase 2 - Critical Vehicle Identity Design

## Problem Identified

The current implementation has a critical flaw in vehicle identification for GPS tracking:

### Current Problematic Patterns

**Laravel (Reference - DO NOT COPY):**
```php
// WRONG: Selects vehicle by "latest" update time
$vehicle = Vehicle::where('user_id', $user->id)->latest()->first();
```

**Spring Boot Current:**
```java
// Current TripService.updateLocation() accepts tripId but NO vehicleId
// Risk of wrong vehicle selection if driver has multiple vehicles
```

### Why This Is Critical

In a real fleet environment:
1. **Driver may have multiple vehicles** (owned vehicles + fleet vehicles)
2. **GPS device must identify the actual transmitting vehicle** 
3. **Cannot infer vehicle from "latest update"** - this is unreliable
4. **Location updates must be attributed to correct vehicle**

## Vehicle Identity Mechanism Analysis

### Current Vehicle Entity Structure
```java
@Entity
public class Vehicle {
    @Id
    private Long id;                    // Primary key - RELIABLE
    
    @Column(unique = true)
    private String plateNumber;         // Unique identifier - RELIABLE
    
    private Long userId;                // Owner relationship
    private Long fleetOwnerId;         // Fleet relationship
    
    private BigDecimal latitude;        // Current location
    private BigDecimal longitude;       // Current location  
    private LocalDateTime lastLocationAt; // Last update time
}
```

### Established Vehicle Identification Options

**Option 1: Vehicle ID (Primary Key)**
- ✅ **Reliable**: Database-enforced uniqueness
- ✅ **Simple**: Direct entity reference
- ✅ **Efficient**: Single database lookup
- ✅ **Already Established**: Used throughout existing codebase

**Option 2: Plate Number (Unique Business Key)**
- ✅ **Human-Readable**: Drivers know their plate numbers
- ✅ **Unique**: Database constraint enforced
- ✅ **Business Relevant**: Real-world vehicle identifier
- ⚠️ **Additional Lookup**: Requires plateNumber → ID resolution

**Option 3: Hardware Device ID (NOT ESTABLISHED)**
- ❌ **Not Implemented**: No device_id or hardware_serial in Vehicle entity
- ❌ **Would Require**: New database schema and device provisioning
- ❌ **Out of Scope**: No existing hardware device protocol

## Recommended Solution: Vehicle ID

### Decision: Use Vehicle.id for GPS Tracking

**Rationale:**
1. **Already Established** - Vehicle.id is the primary key used throughout the system
2. **Database Enforced** - Guaranteed uniqueness and referential integrity  
3. **Performance Optimized** - Direct entity lookup without additional queries
4. **Security Ready** - Existing ownership validation patterns work with Vehicle.id
5. **Minimal Changes** - Extends existing patterns rather than introducing new concepts

### Implementation Strategy

**GPS Update Contract:**
```java
// NEW: Explicit vehicle identification required
POST /api/trips/{tripId}/gps
{
    "vehicleId": 123,           // REQUIRED: Explicitly identify transmitting vehicle
    "latitude": 9.145000,
    "longitude": 40.489673,
    "speed": 45.5,
    "accuracy": 8.2,
    "timestamp": "2026-09-02T15:30:00Z"
}
```

**Validation Flow:**
1. **Authenticate User** - Extract user from JWT
2. **Validate Vehicle Exists** - Verify vehicleId exists
3. **Validate Vehicle Ownership** - User must own or be authorized for vehicle
4. **Validate Trip Association** - Trip must use this vehicle
5. **Validate Trip Active** - Trip must be ONGOING status
6. **Update Both** - Vehicle current location + Trip route_data

### Vehicle Ownership Validation

**Driver-Owned Vehicles:**
```java
// Verify driver owns the vehicle
Vehicle vehicle = vehicleRepository.findById(vehicleId)
    .orElseThrow(() -> new VehicleNotFoundException(vehicleId));
    
if (!vehicle.getUserId().equals(user.getId())) {
    throw new UnauthorizedVehicleAccessException(vehicleId, user.getId());
}
```

**Fleet-Owned Vehicles:**
```java
// Verify driver is authorized for fleet vehicle
if (vehicle.getFleetOwnerId() != null) {
    // Driver must be associated with this fleet
    if (!user.getFleetOwnerId().equals(vehicle.getFleetOwnerId())) {
        throw new UnauthorizedFleetVehicleAccessException(vehicleId, user.getId());
    }
}
```

### Trip-Vehicle Consistency

**Booking-Vehicle Relationship:**
```java
// Verify trip uses this vehicle
Trip trip = tripRepository.findById(tripId)
    .orElseThrow(() -> new TripNotFoundException(tripId));
    
Booking booking = trip.getBooking();
if (!booking.getVehicle().getId().equals(vehicleId)) {
    throw new TripVehicleMismatchException(tripId, vehicleId, booking.getVehicle().getId());
}
```

### API Enhancement Required

**Current AddGpsLocationRequest:**
```java
// CURRENT - Missing vehicle identification
public class AddGpsLocationRequest {
    private BigDecimal latitude;
    private BigDecimal longitude; 
    private BigDecimal speed;
    private BigDecimal accuracy;
    private LocalDateTime timestamp;
    // Missing: vehicleId
}
```

**Enhanced AddGpsLocationRequest:**
```java
// ENHANCED - Explicit vehicle identification
public class AddGpsLocationRequest {
    @NotNull(message = "Vehicle ID is required")
    private Long vehicleId;         // NEW: Explicit vehicle identification
    
    @NotNull @DecimalMin("-90") @DecimalMax("90")
    private BigDecimal latitude;
    
    @NotNull @DecimalMin("-180") @DecimalMax("180") 
    private BigDecimal longitude;
    
    @DecimalMin("0") @DecimalMax("200")
    private BigDecimal speed;
    
    @DecimalMin("0") @DecimalMax("1000")
    private BigDecimal accuracy;
    
    @NotNull
    private LocalDateTime timestamp;
}
```

## Implementation Plan

### Step 1: Enhance GPS Request DTO
- Add `vehicleId` field to AddGpsLocationRequest
- Add validation annotations
- Update API documentation

### Step 2: Update TripService.updateLocation()
- Accept vehicleId parameter
- Add vehicle existence validation
- Add vehicle ownership validation  
- Add trip-vehicle consistency validation

### Step 3: Update Vehicle Location
- Update Vehicle.latitude, longitude, lastLocationAt
- Maintain existing current location functionality

### Step 4: Update Trip Route Data  
- Continue appending to Trip.routeData JSONB
- Maintain existing 500-point boundary
- Preserve RouteDataService integration

### Step 5: Error Handling
- VehicleNotFoundException - Vehicle ID doesn't exist
- UnauthorizedVehicleAccessException - User doesn't own vehicle
- UnauthorizedFleetVehicleAccessException - User not authorized for fleet vehicle
- TripVehicleMismatchException - Trip doesn't use this vehicle

## Database Schema Impact

**No Schema Changes Required** - Vehicle.id already exists and is properly indexed as primary key.

**Existing Relationships Preserved:**
- Vehicle.userId → User.id (owner relationship)
- Vehicle.fleetOwnerId → User.id (fleet relationship)  
- Booking.vehicleId → Vehicle.id (trip-vehicle relationship)

## Security Implications

**Enhanced Security:**
1. **Explicit Authorization** - Must validate vehicle ownership per GPS update
2. **Trip-Vehicle Binding** - Prevents GPS updates to wrong trip's vehicle
3. **Fleet Isolation** - Fleet drivers can only update fleet vehicles
4. **IDOR Protection** - Cannot update arbitrary vehicle locations

**Authorization Matrix:**
| User Role | Can Update Vehicle | Conditions |
|-----------|-------------------|------------|
| DRIVER | Own vehicles | vehicle.userId = user.id |  
| DRIVER | Fleet vehicles | vehicle.fleetOwnerId = user.fleetOwnerId |
| FLEET_OWNER | Fleet vehicles | vehicle.fleetOwnerId = user.id |
| ADMIN | Any vehicle | No restrictions |
| SHIPPER | None | GPS updates not allowed |

## Migration Strategy

**Phase 1: Add vehicleId to API**
- Update AddGpsLocationRequest DTO
- Make vehicleId required in new GPS updates
- Maintain backward compatibility temporarily

**Phase 2: Update Validation Logic**
- Implement vehicle ownership validation
- Add trip-vehicle consistency checks
- Enhanced error responses

**Phase 3: Remove Legacy Patterns**  
- Remove any "latest vehicle" selection logic
- Ensure all GPS updates use explicit vehicle identification

**Phase 4: Documentation**
- Update API documentation
- Document vehicle identification requirements
- Provide client integration examples

## Conclusion

**Vehicle.id is the established and optimal vehicle identification mechanism** for GPS tracking. This approach:

1. **Uses existing infrastructure** - No new schemas or concepts
2. **Maintains security** - Ownership validation enforced
3. **Ensures accuracy** - No ambiguous vehicle selection
4. **Scales properly** - Works for individual and fleet vehicles
5. **Minimal complexity** - Straightforward implementation

This design eliminates the critical vehicle identification issue while building on the existing robust foundation.