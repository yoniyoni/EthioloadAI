# Spring Boot Migration Roadmap

**Purpose:** Phase-by-phase implementation plan for missing API endpoints  
**Strategy:** Build based on domain dependencies to minimize refactoring  
**Total Phases:** 8  
**Estimated Timeline:** 4-6 weeks

---

## Phase 1: Vehicle Management

**Goal:** Enable drivers to register and manage their vehicles  
**Duration:** 3-4 days  
**Dependencies:** None (standalone feature)  
**Frontend Impact:** Vehicles page becomes functional

### Dependency Analysis

**Domain Entities:**
- User (already exists from Auth phase)
- Vehicle (new)

**Database Dependencies:**
- users table (already exists)
- vehicles table (new)

**Authentication Dependencies:**
- DRIVER role (already exists in User entity)
- FLEET_OWNER role (already exists in User entity)

**Frontend Dependencies:**
- Vehicles page (frontend exists, needs backend)

**APIs Required by Later Phases:**
- Phase 3 (Bidding): Vehicle entity referenced in Bid.vehicle_id
- Phase 2 (Freight): Vehicle entity referenced in Freight.matched_vehicle_id

**Architectural Notes:**
- This phase is correctly placed first as it has no dependencies on other new entities
- Vehicle is a foundational entity referenced by both Freight and Bid
- No refactoring expected in later phases

### 1. Required Entities

#### Vehicle
```java
@Entity
@Table(name = "vehicles")
public class Vehicle {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "owner_id")
    private User owner;
    
    @Column(name = "truck_type", nullable = false)
    private String truckType; // pickup, light_truck, medium_truck, heavy_truck, tanker, refrigerated, flatbed, tipper
    
    @Column(name = "plate_number", nullable = false, unique = true)
    private String plateNumber;
    
    @Column(name = "capacity_tons", nullable = false)
    private BigDecimal capacityTons;
    
    @Column(name = "volume_m3")
    private BigDecimal volumeM3;
    
    @Column(name = "fuel_type")
    private String fuelType; // diesel, petrol, electric
    
    @Column(name = "current_city")
    private String currentCity;
    
    @Column(name = "is_available")
    private Boolean isAvailable = true;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
```

### 2. DTOs

#### CreateVehicleRequest
```java
public record CreateVehicleRequest(
    @NotBlank String truckType,
    @NotBlank String plateNumber,
    @NotNull @DecimalMin("0.1") BigDecimal capacityTons,
    BigDecimal volumeM3,
    String fuelType,
    @NotBlank String currentCity
) {}
```

#### VehicleResponse
```java
public record VehicleResponse(
    Long id,
    Long ownerId,
    String ownerName,
    String truckType,
    String plateNumber,
    BigDecimal capacityTons,
    BigDecimal volumeM3,
    String fuelType,
    String currentCity,
    Boolean isAvailable,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
```

#### UpdateVehicleRequest
```java
public record UpdateVehicleRequest(
    String truckType,
    String plateNumber,
    BigDecimal capacityTons,
    BigDecimal volumeM3,
    String fuelType,
    String currentCity,
    Boolean isAvailable
) {}
```

### 3. Repositories

#### VehicleRepository
```java
@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, Long> {
    List<Vehicle> findByOwnerId(Long ownerId);
    Optional<Vehicle> findByPlateNumber(String plateNumber);
    boolean existsByPlateNumber(String plateNumber);
}
```

### 4. Services

#### VehicleService
```java
public interface VehicleService {
    VehicleResponse createVehicle(Long userId, CreateVehicleRequest request);
    List<VehicleResponse> getUserVehicles(Long userId);
    VehicleResponse getVehicleById(Long id, Long userId);
    VehicleResponse updateVehicle(Long id, Long userId, UpdateVehicleRequest request);
    void deleteVehicle(Long id, Long userId);
    void toggleAvailability(Long id, Long userId);
}
```

#### VehicleServiceImpl
- Validate user is DRIVER or FLEET_OWNER
- Validate plate number uniqueness
- Map between entities and DTOs
- Handle ownership validation

### 5. Controllers

#### VehicleController
```java
@RestController
@RequestMapping("/api/vehicles")
@RequiredArgsConstructor
@Tag(name = "Vehicles", description = "Vehicle management endpoints")
public class VehicleController {
    
    @GetMapping("/my-vehicles")
    @Operation(summary = "Get current user's vehicles")
    public ResponseEntity<List<VehicleResponse>> getMyVehicles(Authentication authentication);
    
    @PostMapping
    @Operation(summary = "Create a new vehicle")
    public ResponseEntity<VehicleResponse> createVehicle(
        @Valid @RequestBody CreateVehicleRequest request,
        Authentication authentication
    );
    
    @GetMapping("/{id}")
    @Operation(summary = "Get vehicle by ID")
    public ResponseEntity<VehicleResponse> getVehicle(
        @PathVariable Long id,
        Authentication authentication
    );
    
    @PatchMapping("/{id}")
    @Operation(summary = "Update vehicle")
    public ResponseEntity<VehicleResponse> updateVehicle(
        @PathVariable Long id,
        @Valid @RequestBody UpdateVehicleRequest request,
        Authentication authentication
    );
    
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete vehicle")
    public ResponseEntity<Void> deleteVehicle(
        @PathVariable Long id,
        Authentication authentication
    );
    
    @PatchMapping("/{id}/availability")
    @Operation(summary = "Toggle vehicle availability")
    public ResponseEntity<VehicleResponse> toggleAvailability(
        @PathVariable Long id,
        Authentication authentication
    );
}
```

### 6. Validation

- Truck type must be one of: pickup, light_truck, medium_truck, heavy_truck, tanker, refrigerated, flatbed, tipper
- Plate number must be unique across all vehicles
- Capacity must be >= 0.1 tons
- Plate number format validation (Ethiopian format: AA-12345 or similar)
- User must have role DRIVER or FLEET_OWNER
- Ownership validation for update/delete operations

### 7. Security Requirements

- **GET /my-vehicles:** Authenticated (DRIVER or FLEET_OWNER)
- **POST /vehicles:** Authenticated (DRIVER or FLEET_OWNER)
- **GET /vehicles/{id}:** Authenticated (owner only)
- **PATCH /vehicles/{id}:** Authenticated (owner only)
- **DELETE /vehicles/{id}:** Authenticated (owner only)
- **PATCH /vehicles/{id}/availability:** Authenticated (owner only)

### 8. Flyway Migrations

#### V2__create_vehicles_table.sql
```sql
CREATE TABLE IF NOT EXISTS vehicles (
    id BIGSERIAL PRIMARY KEY,
    owner_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    truck_type VARCHAR(50) NOT NULL CHECK (truck_type IN ('pickup', 'light_truck', 'medium_truck', 'heavy_truck', 'tanker', 'refrigerated', 'flatbed', 'tipper')),
    plate_number VARCHAR(20) NOT NULL UNIQUE,
    capacity_tons DECIMAL(10, 2) NOT NULL CHECK (capacity_tons >= 0.1),
    volume_m3 DECIMAL(10, 2),
    fuel_type VARCHAR(20) CHECK (fuel_type IN ('diesel', 'petrol', 'electric')),
    current_city VARCHAR(100),
    is_available BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_vehicles_owner ON vehicles(owner_id);
CREATE INDEX idx_vehicles_available ON vehicles(is_available) WHERE is_available = TRUE;
CREATE INDEX idx_vehicles_type ON vehicles(truck_type);
```

### 9. API Endpoints

| Method | Endpoint | Purpose | Auth |
|--------|----------|---------|------|
| GET | `/api/vehicles/my-vehicles` | Get user's vehicles | Driver/FleetOwner |
| POST | `/api/vehicles` | Create vehicle | Driver/FleetOwner |
| GET | `/api/vehicles/{id}` | Get vehicle by ID | Owner |
| PATCH | `/api/vehicles/{id}` | Update vehicle | Owner |
| DELETE | `/api/vehicles/{id}` | Delete vehicle | Owner |
| PATCH | `/api/vehicles/{id}/availability` | Toggle availability | Owner |

**Path Alias:** Add `/my-vehicles` → `/api/vehicles/my-vehicles` for frontend compatibility

### 10. Testing Checklist

- [ ] Create vehicle with valid data
- [ ] Create vehicle with duplicate plate number (should fail)
- [ ] Create vehicle with invalid truck type (should fail)
- [ ] Create vehicle with capacity < 0.1 (should fail)
- [ ] Get user's vehicles (returns only owned vehicles)
- [ ] Get another user's vehicle (should fail)
- [ ] Update own vehicle (success)
- [ ] Update another user's vehicle (should fail)
- [ ] Delete own vehicle (success)
- [ ] Delete vehicle with active bids (should fail or cascade)
- [ ] Toggle availability (success)
- [ ] Non-driver/fleet owner cannot create vehicles (should fail)

---

## Phase 2: Freight/Cargo Management

**Goal:** Enable shippers to post freight requests  
**Duration:** 5-7 days  
**Dependencies:** Phase 1 (Vehicles)  
**Frontend Impact:** Freight list, freight detail, freight-new pages become functional

### Dependency Analysis

**Domain Entities:**
- User (already exists from Auth phase)
- Vehicle (from Phase 1)
- Freight (new)

**Database Dependencies:**
- users table (already exists)
- vehicles table (from Phase 1)
- freight table (new)

**Authentication Dependencies:**
- SHIPPER role (already exists in User entity)
- ADMIN role (already exists in User entity)

**Frontend Dependencies:**
- Freight list page (frontend exists, needs backend)
- Freight detail page (frontend exists, needs backend)
- Freight-new page (frontend exists, needs backend)

**APIs Required by Later Phases:**
- Phase 3 (Bidding): Freight entity referenced in Bid.freight_id
- Phase 4 (Tracking): Freight entity referenced in LocationUpdate.freight_id
- Phase 5 (Payment): Freight entity referenced in Payment.freight_id
- Phase 6 (Messaging): Freight entity referenced in Message.freight_id
- Phase 7 (AI): Freight entity used for price prediction and truck recommendation
- Phase 8 (Admin): Freight entity used for analytics and dispute resolution

**Architectural Notes:**
- Freight is the central domain entity - almost all other phases depend on it
- matched_vehicle_id is nullable, so Freight could theoretically exist without Vehicles, but having Vehicles first enables the full matching flow
- Freight.status enum drives the state machine for the entire system
- No refactoring expected - Freight is designed as the core aggregate root

### 1. Required Entities

#### Freight (Cargo Request)
```java
@Entity
@Table(name = "freight")
public class Freight {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "shipper_id")
    private User shipper;
    
    @Column(name = "pickup_location", nullable = false)
    private String pickupLocation;
    
    @Column(name = "pickup_latitude", precision = 10, scale = 7)
    private BigDecimal pickupLatitude;
    
    @Column(name = "pickup_longitude", precision = 10, scale = 7)
    private BigDecimal pickupLongitude;
    
    @Column(name = "destination", nullable = false)
    private String destination;
    
    @Column(name = "destination_latitude", precision = 10, scale = 7)
    private BigDecimal destinationLatitude;
    
    @Column(name = "destination_longitude", precision = 10, scale = 7)
    private BigDecimal destinationLongitude;
    
    @Column(name = "material_type", nullable = false)
    private String materialType; // grain, cement, construction, perishables, electronics, livestock, fuel, general, other
    
    @Column(name = "cargo_description")
    private String cargoDescription;
    
    @Column(name = "weight_tons", nullable = false)
    private BigDecimal weightTons;
    
    @Column(name = "volume_m3")
    private BigDecimal volumeM3;
    
    @Column(name = "budget", nullable = false)
    private BigDecimal budget;
    
    @Column(name = "distance_km")
    private BigDecimal distanceKm;
    
    @Column(name = "deadline")
    private LocalDate deadline;
    
    @Column(name = "urgency_level", nullable = false)
    private String urgencyLevel; // low, normal, high, urgent
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private FreightStatus status; // posted, matched, in_transit, delivered, completed, cancelled
    
    @ManyToOne
    @JoinColumn(name = "matched_driver_id")
    private User matchedDriver;
    
    @ManyToOne
    @JoinColumn(name = "matched_vehicle_id")
    private Vehicle matchedVehicle;
    
    @Column(name = "matched_price")
    private BigDecimal matchedPrice;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    public enum FreightStatus {
        POSTED, MATCHED, IN_TRANSIT, DELIVERED, COMPLETED, CANCELLED
    }
}
```

### 2. DTOs

#### CreateFreightRequest
```java
public record CreateFreightRequest(
    @NotBlank String pickupLocation,
    BigDecimal pickupLatitude,
    BigDecimal pickupLongitude,
    @NotBlank String destination,
    BigDecimal destinationLatitude,
    BigDecimal destinationLongitude,
    @NotBlank String materialType,
    String cargoDescription,
    @NotNull @DecimalMin("0.1") BigDecimal weightTons,
    BigDecimal volumeM3,
    @NotNull @DecimalMin("1") BigDecimal budget,
    BigDecimal distanceKm,
    LocalDate deadline,
    @NotBlank String urgencyLevel
) {}
```

#### FreightResponse
```java
public record FreightResponse(
    Long id,
    Long shipperId,
    String shipperName,
    String pickupLocation,
    BigDecimal pickupLatitude,
    BigDecimal pickupLongitude,
    String destination,
    BigDecimal destinationLatitude,
    BigDecimal destinationLongitude,
    String materialType,
    String cargoDescription,
    BigDecimal weightTons,
    BigDecimal volumeM3,
    BigDecimal budget,
    BigDecimal distanceKm,
    LocalDate deadline,
    String urgencyLevel,
    String status,
    Long matchedDriverId,
    String matchedDriverName,
    Long matchedVehicleId,
    BigDecimal matchedPrice,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
```

#### UpdateFreightRequest
```java
public record UpdateFreightRequest(
    String cargoDescription,
    BigDecimal budget,
    LocalDate deadline,
    String urgencyLevel,
    FreightStatus status
) {}
```

#### FreightFilterRequest
```java
public record FreightFilterRequest(
    String status,
    String materialType,
    String pickupLocation,
    String destination,
    BigDecimal minWeight,
    BigDecimal maxWeight,
    BigDecimal minBudget,
    BigDecimal maxBudget,
    LocalDate deadlineAfter,
    Integer page,
    Integer size
) {}
```

### 3. Repositories

#### FreightRepository
```java
@Repository
public interface FreightRepository extends JpaRepository<Freight, Long>, JpaSpecificationExecutor<Freight> {
    List<Freight> findByShipperId(Long shipperId);
    List<Freight> findByStatus(FreightStatus status);
    List<Freight> findByStatusAndMaterialType(FreightStatus status, String materialType);
    List<Freight> findByMatchedDriverId(Long driverId);
    boolean existsByShipperIdAndId(Long shipperId, Long id);
}
```

### 4. Services

#### FreightService
```java
public interface FreightService {
    FreightResponse createFreight(Long userId, CreateFreightRequest request);
    FreightResponse getFreightById(Long id);
    List<FreightResponse> getUserFreights(Long userId);
    List<FreightResponse> searchFreights(FreightFilterRequest filter);
    FreightResponse updateFreight(Long id, Long userId, UpdateFreightRequest request);
    void cancelFreight(Long id, Long userId);
    Page<FreightResponse> getPaginatedFreights(FreightFilterRequest filter);
}
```

#### FreightServiceImpl
- Validate user is SHIPPER or ADMIN
- Validate status transitions
- Calculate distance if coordinates provided
- Handle ownership validation
- Implement search/filter logic

### 5. Controllers

#### FreightController
```java
@RestController
@RequestMapping("/api/freight")
@RequiredArgsConstructor
@Tag(name = "Freight", description = "Freight management endpoints")
public class FreightController {
    
    @GetMapping
    @Operation(summary = "List freight with filters")
    public ResponseEntity<Map<String, Object>> listFreight(
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String materialType,
        @RequestParam(required = false) Integer limit = 20
    );
    
    @GetMapping("/{id}")
    @Operation(summary = "Get freight by ID")
    public ResponseEntity<FreightResponse> getFreight(@PathVariable Long id);
    
    @PostMapping
    @Operation(summary = "Create freight")
    public ResponseEntity<FreightResponse> createFreight(
        @Valid @RequestBody CreateFreightRequest request,
        Authentication authentication
    );
    
    @PatchMapping("/{id}")
    @Operation(summary = "Update freight")
    public ResponseEntity<FreightResponse> updateFreight(
        @PathVariable Long id,
        @Valid @RequestBody UpdateFreightRequest request,
        Authentication authentication
    );
    
    @DeleteMapping("/{id}")
    @Operation(summary = "Cancel/delete freight")
    public ResponseEntity<Void> cancelFreight(
        @PathVariable Long id,
        Authentication authentication
    );
    
    @GetMapping("/my-freights")
    @Operation(summary = "Get current user's freight")
    public ResponseEntity<List<FreightResponse>> getMyFreights(Authentication authentication);
}
```

### 6. Validation

- Material type must be one of: grain, cement, construction, perishables, electronics, livestock, fuel, general, other
- Urgency level must be one of: low, normal, high, urgent
- Weight must be >= 0.1 tons
- Budget must be >= 1 ETB
- Status transition validation (e.g., can't go from COMPLETED to POSTED)
- User must be SHIPPER or ADMIN to create/update
- Ownership validation for update/delete
- Cannot delete freight with active bids or in transit

### 7. Security Requirements

- **GET /freight:** Public (with optional auth for personalized results)
- **GET /freight/{id}:** Public
- **POST /freight:** Authenticated (SHIPPER or ADMIN)
- **PATCH /freight/{id}:** Authenticated (owner or ADMIN)
- **DELETE /freight/{id}:** Authenticated (owner or ADMIN)
- **GET /freight/my-freights:** Authenticated (SHIPPER or ADMIN)

### 8. Flyway Migrations

#### V3__create_freight_table.sql
```sql
CREATE TABLE IF NOT EXISTS freight (
    id BIGSERIAL PRIMARY KEY,
    shipper_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    pickup_location VARCHAR(255) NOT NULL,
    pickup_latitude DECIMAL(10, 7),
    pickup_longitude DECIMAL(10, 7),
    destination VARCHAR(255) NOT NULL,
    destination_latitude DECIMAL(10, 7),
    destination_longitude DECIMAL(10, 7),
    material_type VARCHAR(50) NOT NULL CHECK (material_type IN ('grain', 'cement', 'construction', 'perishables', 'electronics', 'livestock', 'fuel', 'general', 'other')),
    cargo_description TEXT,
    weight_tons DECIMAL(10, 2) NOT NULL CHECK (weight_tons >= 0.1),
    volume_m3 DECIMAL(10, 2),
    budget DECIMAL(15, 2) NOT NULL CHECK (budget >= 1),
    distance_km DECIMAL(10, 2),
    deadline DATE,
    urgency_level VARCHAR(20) NOT NULL CHECK (urgency_level IN ('low', 'normal', 'high', 'urgent')),
    status VARCHAR(20) NOT NULL DEFAULT 'posted' CHECK (status IN ('posted', 'matched', 'in_transit', 'delivered', 'completed', 'cancelled')),
    matched_driver_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    matched_vehicle_id BIGINT REFERENCES vehicles(id) ON DELETE SET NULL,
    matched_price DECIMAL(15, 2),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_freight_shipper ON freight(shipper_id);
CREATE INDEX idx_freight_status ON freight(status);
CREATE INDEX idx_freight_material ON freight(material_type);
CREATE INDEX idx_freight_driver ON freight(matched_driver_id) WHERE matched_driver_id IS NOT NULL;
CREATE INDEX idx_freight_deadline ON freight(deadline) WHERE deadline IS NOT NULL;
```

### 9. API Endpoints

| Method | Endpoint | Purpose | Auth |
|--------|----------|---------|------|
| GET | `/api/freight` | List freight with filters | Public |
| GET | `/api/freight/{id}` | Get freight by ID | Public |
| POST | `/api/freight` | Create freight | Shipper/Admin |
| PATCH | `/api/freight/{id}` | Update freight | Owner/Admin |
| DELETE | `/api/freight/{id}` | Cancel freight | Owner/Admin |
| GET | `/api/freight/my-freights` | Get user's freight | Shipper/Admin |

**Path Aliases:** Add `/freight` → `/api/freight`, `/cargo-requests` → `/api/freight` for frontend compatibility

### 10. Testing Checklist

- [ ] Create freight with valid data
- [ ] Create freight with invalid material type (should fail)
- [ ] Create freight with budget < 1 (should fail)
- [ ] List freight with status filter
- [ ] List freight with material type filter
- [ ] List freight with pagination
- [ ] Get freight by ID (public access)
- [ ] Update own freight (success)
- [ ] Update another user's freight (should fail)
- [ ] Cancel own freight in POSTED status (success)
- [ ] Cancel freight in IN_TRANSIT status (should fail)
- [ ] Non-shipper cannot create freight (should fail)
- [ ] Search freight by pickup location
- [ ] Search freight by destination
- [ ] Search freight by weight range
- [ ] Search freight by budget range

---

## Phase 3: Bidding System

**Goal:** Enable drivers to bid on freight and shippers to accept bids  
**Duration:** 4-5 days  
**Dependencies:** Phase 1 (Vehicles), Phase 2 (Freight)  
**Frontend Impact:** Freight detail page bidding functionality becomes functional

### Dependency Analysis

**Domain Entities:**
- User (already exists from Auth phase)
- Vehicle (from Phase 1)
- Freight (from Phase 2)
- Bid (new)

**Database Dependencies:**
- users table (already exists)
- vehicles table (from Phase 1)
- freight table (from Phase 2)
- bids table (new)

**Authentication Dependencies:**
- DRIVER role (already exists in User entity)
- SHIPPER role (already exists in User entity)
- ADMIN role (already exists in User entity)

**Frontend Dependencies:**
- Freight detail page bidding UI (frontend exists, needs backend)

**APIs Required by Later Phases:**
- Phase 5 (Payment): Bid acceptance triggers payment initialization
- Phase 8 (Admin): Bids used for analytics and dispute resolution

**Architectural Notes:**
- Bid is a junction entity connecting Freight, Driver (User), and Vehicle
- Bid acceptance is a critical state transition that updates Freight.status to MATCHED
- This phase must come after Freight because bids cannot exist without freight
- This phase must come after Vehicles because bids specify which vehicle will be used
- No refactoring expected - Bid is designed as a dependent entity

### 1. Required Entities

#### Bid
```java
@Entity
@Table(name = "bids")
public class Bid {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "freight_id")
    private Freight freight;
    
    @ManyToOne
    @JoinColumn(name = "driver_id")
    private User driver;
    
    @ManyToOne
    @JoinColumn(name = "vehicle_id")
    private Vehicle vehicle;
    
    @Column(name = "amount", nullable = false)
    private BigDecimal amount;
    
    @Column(name = "note")
    private String note;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private BidStatus status; // pending, accepted, rejected, withdrawn
    
    @Column(name = "is_recommended")
    private Boolean isRecommended = false;
    
    @Column(name = "distance_km")
    private BigDecimal distanceKm;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    public enum BidStatus {
        PENDING, ACCEPTED, REJECTED, WITHDRAWN
    }
}
```

### 2. DTOs

#### CreateBidRequest
```java
public record CreateBidRequest(
    @NotNull Long vehicleId,
    @NotNull @DecimalMin("1") BigDecimal amount,
    String note
) {}
```

#### BidResponse
```java
public record BidResponse(
    Long id,
    Long freightId,
    Long driverId,
    String driverName,
    String driverPhone,
    Long vehicleId,
    String truckType,
    String plateNumber,
    BigDecimal amount,
    String note,
    String status,
    Boolean isRecommended,
    BigDecimal distanceKm,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
```

#### AcceptBidRequest
```java
public record AcceptBidRequest(
    @NotNull Long bidId
) {}
```

### 3. Repositories

#### BidRepository
```java
@Repository
public interface BidRepository extends JpaRepository<Bid, Long> {
    List<Bid> findByFreightId(Long freightId);
    List<Bid> findByDriverId(Long driverId);
    List<Bid> findByFreightIdAndStatus(Long freightId, BidStatus status);
    Optional<Bid> findByFreightIdAndDriverId(Long freightId, Long driverId);
    boolean existsByFreightIdAndDriverId(Long freightId, Long driverId);
}
```

### 4. Services

#### BidService
```java
public interface BidService {
    BidResponse createBid(Long freightId, Long driverId, CreateBidRequest request);
    List<BidResponse> getFreightBids(Long freightId);
    List<BidResponse> getDriverBids(Long driverId);
    BidResponse acceptBid(Long freightId, Long bidId, Long shipperId);
    BidResponse rejectBid(Long bidId, Long shipperId);
    BidResponse withdrawBid(Long bidId, Long driverId);
    BidResponse getBidById(Long id);
}
```

#### BidServiceImpl
- Validate user is DRIVER
- Validate freight is in POSTED status
- Validate vehicle belongs to driver
- Validate driver hasn't already bid
- Calculate distance between driver and pickup
- Implement bid acceptance logic (updates freight status)
- Handle bid withdrawal
- AI integration for bid recommendation

### 5. Controllers

#### BidController
```java
@RestController
@RequestMapping("/api/bids")
@RequiredArgsConstructor
@Tag(name = "Bids", description = "Bidding system endpoints")
public class BidController {
    
    @PostMapping("/freight/{freightId}")
    @Operation(summary = "Submit bid for freight")
    public ResponseEntity<BidResponse> createBid(
        @PathVariable Long freightId,
        @Valid @RequestBody CreateBidRequest request,
        Authentication authentication
    );
    
    @GetMapping("/freight/{freightId}")
    @Operation(summary = "Get bids for freight")
    public ResponseEntity<List<BidResponse>> getFreightBids(@PathVariable Long freightId);
    
    @GetMapping("/my-bids")
    @Operation(summary = "Get current user's bids")
    public ResponseEntity<List<BidResponse>> getMyBids(Authentication authentication);
    
    @PatchMapping("/{bidId}/accept")
    @Operation(summary = "Accept bid")
    public ResponseEntity<BidResponse> acceptBid(
        @PathVariable Long bidId,
        Authentication authentication
    );
    
    @PatchMapping("/{bidId}/reject")
    @Operation(summary = "Reject bid")
    public ResponseEntity<BidResponse> rejectBid(
        @PathVariable Long bidId,
        Authentication authentication
    );
    
    @PatchMapping("/{bidId}/withdraw")
    @Operation(summary = "Withdraw bid")
    public ResponseEntity<BidResponse> withdrawBid(
        @PathVariable Long bidId,
        Authentication authentication
    );
}
```

### 6. Validation

- Bid amount must be >= 1 ETB
- Freight must be in POSTED status
- Vehicle must belong to driver
- Driver cannot bid on own freight
- Only one active bid per driver per freight
- Only shipper can accept/reject bids
- Only driver can withdraw own bid
- Cannot accept bid if freight already matched
- Cannot withdraw accepted bid

### 7. Security Requirements

- **POST /bids/freight/{freightId}:** Authenticated (DRIVER)
- **GET /bids/freight/{freightId}:** Authenticated (shipper or ADMIN)
- **GET /bids/my-bids:** Authenticated (DRIVER)
- **PATCH /bids/{bidId}/accept:** Authenticated (shipper or ADMIN)
- **PATCH /bids/{bidId}/reject:** Authenticated (shipper or ADMIN)
- **PATCH /bids/{bidId}/withdraw:** Authenticated (bid owner)

### 8. Flyway Migrations

#### V4__create_bids_table.sql
```sql
CREATE TABLE IF NOT EXISTS bids (
    id BIGSERIAL PRIMARY KEY,
    freight_id BIGINT NOT NULL REFERENCES freight(id) ON DELETE CASCADE,
    driver_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    vehicle_id BIGINT NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE,
    amount DECIMAL(15, 2) NOT NULL CHECK (amount >= 1),
    note TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'accepted', 'rejected', 'withdrawn')),
    is_recommended BOOLEAN DEFAULT FALSE,
    distance_km DECIMAL(10, 2),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(freight_id, driver_id)
);

CREATE INDEX idx_bids_freight ON bids(freight_id);
CREATE INDEX idx_bids_driver ON bids(driver_id);
CREATE INDEX idx_bids_status ON bids(status);
CREATE INDEX idx_bids_freight_status ON bids(freight_id, status) WHERE status = 'pending';
```

### 9. API Endpoints

| Method | Endpoint | Purpose | Auth |
|--------|----------|---------|------|
| POST | `/api/bids/freight/{freightId}` | Submit bid | Driver |
| GET | `/api/bids/freight/{freightId}` | Get freight bids | Shipper/Admin |
| GET | `/api/bids/my-bids` | Get driver's bids | Driver |
| PATCH | `/api/bids/{bidId}/accept` | Accept bid | Shipper/Admin |
| PATCH | `/api/bids/{bidId}/reject` | Reject bid | Shipper/Admin |
| PATCH | `/api/bids/{bidId}/withdraw` | Withdraw bid | Bid owner |

**Path Aliases:** Add `/cargo-requests/{id}/bids` → `/api/bids/freight/{id}` for frontend compatibility

### 10. Testing Checklist

- [ ] Driver submits valid bid (success)
- [ ] Driver submits bid with amount < 1 (should fail)
- [ ] Driver submits bid on own freight (should fail)
- [ ] Driver submits bid with invalid vehicle (should fail)
- [ ] Driver submits duplicate bid (should fail)
- [ ] Shipper views freight bids (success)
- [ ] Shipper accepts bid (success, freight status changes to MATCHED)
- [ ] Shipper accepts bid on already matched freight (should fail)
- [ ] Driver withdraws pending bid (success)
- [ ] Driver withdraws accepted bid (should fail)
- [ ] Non-driver cannot submit bid (should fail)
- [ ] Non-shipper cannot accept bid (should fail)
- [ ] Get driver's own bids (success)
- [ ] Bid acceptance updates freight matched_driver and matched_vehicle

---

## Phase 4: Tracking & Location Updates

**Goal:** Enable live tracking of freight in transit  
**Duration:** 3-4 days  
**Dependencies:** Phase 2 (Freight)  
**Frontend Impact:** Tracking page becomes functional

### Dependency Analysis

**Domain Entities:**
- User (already exists from Auth phase)
- Freight (from Phase 2)
- LocationUpdate (new)

**Database Dependencies:**
- users table (already exists)
- freight table (from Phase 2)
- location_updates table (new)

**Authentication Dependencies:**
- DRIVER role (already exists in User entity)
- SHIPPER role (already exists in User entity)
- ADMIN role (already exists in User entity)

**Frontend Dependencies:**
- Tracking page (frontend exists, needs backend)

**APIs Required by Later Phases:**
- Phase 8 (Admin): Location data used for analytics and route optimization

**Architectural Notes:**
- LocationUpdate is a time-series entity dependent on Freight
- Tracking only becomes relevant after Freight.status is IN_TRANSIT (set by Phase 3 bid acceptance)
- Could theoretically be implemented before Phase 3, but would be unusable until freight is matched
- No refactoring expected - LocationUpdate is designed as a dependent entity

### 1. Required Entities

#### LocationUpdate
```java
@Entity
@Table(name = "location_updates")
public class LocationUpdate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "freight_id")
    private Freight freight;
    
    @ManyToOne
    @JoinColumn(name = "driver_id")
    private User driver;
    
    @Column(name = "latitude", precision = 10, scale = 7, nullable = false)
    private BigDecimal latitude;
    
    @Column(name = "longitude", precision = 10, scale = 7, nullable = false)
    private BigDecimal longitude;
    
    @Column(name = "address")
    private String address;
    
    @Column(name = "speed_kmh")
    private BigDecimal speedKmh;
    
    @Column(name = "heading")
    private BigDecimal heading;
    
    @CreationTimestamp
    @Column(name = "timestamp", nullable = false, updatable = false)
    private LocalDateTime timestamp;
}
```

### 2. DTOs

#### CreateLocationUpdateRequest
```java
public record CreateLocationUpdateRequest(
    @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
    @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
    String address,
    BigDecimal speedKmh,
    BigDecimal heading
) {}
```

#### LocationUpdateResponse
```java
public record LocationUpdateResponse(
    Long id,
    Long freightId,
    Long driverId,
    BigDecimal latitude,
    BigDecimal longitude,
    String address,
    BigDecimal speedKmh,
    BigDecimal heading,
    LocalDateTime timestamp
) {}
```

#### MarkDeliveredRequest
```java
public record MarkDeliveredRequest(
    @NotNull Long freightId,
    String deliveryNotes,
    String proofImageUrl
) {}
```

#### ConfirmDeliveryRequest
```java
public record ConfirmDeliveryRequest(
    @NotNull Long freightId,
    Boolean confirmed
) {}
```

### 3. Repositories

#### LocationUpdateRepository
```java
@Repository
public interface LocationUpdateRepository extends JpaRepository<LocationUpdate, Long> {
    List<LocationUpdate> findByFreightIdOrderByTimestampDesc(Long freightId);
    Optional<LocationUpdate> findFirstByFreightIdOrderByTimestampDesc(Long freightId);
    List<LocationUpdate> findByFreightIdAndTimestampAfter(Long freightId, LocalDateTime timestamp);
}
```

### 4. Services

#### TrackingService
```java
public interface TrackingService {
    LocationUpdateResponse createLocationUpdate(Long freightId, Long driverId, CreateLocationUpdateRequest request);
    LocationUpdateResponse getLatestLocation(Long freightId);
    List<LocationUpdateResponse> getLocationHistory(Long freightId);
    void markDelivered(Long freightId, Long driverId, MarkDeliveredRequest request);
    void confirmDelivery(Long freightId, Long shipperId, ConfirmDeliveryRequest request);
    List<LocationUpdateResponse> getRouteHistory(Long freightId, LocalDateTime since);
}
```

#### TrackingServiceImpl
- Validate freight is in IN_TRANSIT status
- Validate driver is matched driver
- Validate coordinates are valid
- Handle delivery marking (status change to DELIVERED)
- Handle delivery confirmation (status change to COMPLETED)
- Calculate distance traveled
- Implement geocoding (optional - call external service)

### 5. Controllers

#### TrackingController
```java
@RestController
@RequestMapping("/api/tracking")
@RequiredArgsConstructor
@Tag(name = "Tracking", description = "Location tracking endpoints")
public class TrackingController {
    
    @PostMapping
    @Operation(summary = "Update location")
    public ResponseEntity<LocationUpdateResponse> updateLocation(
        @Valid @RequestBody CreateLocationUpdateRequest request,
        @RequestParam Long freightId,
        Authentication authentication
    );
    
    @GetMapping("/{freightId}/latest")
    @Operation(summary = "Get latest location")
    public ResponseEntity<LocationUpdateResponse> getLatestLocation(@PathVariable Long freightId);
    
    @GetMapping("/{freightId}")
    @Operation(summary = "Get location history")
    public ResponseEntity<List<LocationUpdateResponse>> getLocationHistory(@PathVariable Long freightId);
    
    @PostMapping("/freight/{freightId}/deliver")
    @Operation(summary = "Mark as delivered")
    public ResponseEntity<Void> markDelivered(
        @PathVariable Long freightId,
        @RequestBody MarkDeliveredRequest request,
        Authentication authentication
    );
    
    @PostMapping("/freight/{freightId}/confirm-delivery")
    @Operation(summary = "Confirm delivery")
    public ResponseEntity<Void> confirmDelivery(
        @PathVariable Long freightId,
        @RequestBody ConfirmDeliveryRequest request,
        Authentication authentication
    );
}
```

### 6. Validation

- Latitude must be between -90 and 90
- Longitude must be between -180 and 180
- Freight must be in IN_TRANSIT status to update location
- Only matched driver can update location
- Only matched driver can mark delivered
- Only shipper can confirm delivery
- Cannot mark delivered if not in transit
- Cannot confirm if not delivered

### 7. Security Requirements

- **POST /tracking:** Authenticated (matched DRIVER)
- **GET /tracking/{freightId}/latest:** Authenticated (shipper, driver, or ADMIN)
- **GET /tracking/{freightId}:** Authenticated (shipper, driver, or ADMIN)
- **POST /tracking/freight/{freightId}/deliver:** Authenticated (matched DRIVER)
- **POST /tracking/freight/{freightId}/confirm-delivery:** Authenticated (shipper or ADMIN)

### 8. Flyway Migrations

#### V5__create_location_updates_table.sql
```sql
CREATE TABLE IF NOT EXISTS location_updates (
    id BIGSERIAL PRIMARY KEY,
    freight_id BIGINT NOT NULL REFERENCES freight(id) ON DELETE CASCADE,
    driver_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    latitude DECIMAL(10, 7) NOT NULL CHECK (latitude >= -90 AND latitude <= 90),
    longitude DECIMAL(10, 7) NOT NULL CHECK (longitude >= -180 AND longitude <= 180),
    address VARCHAR(255),
    speed_kmh DECIMAL(6, 2),
    heading DECIMAL(5, 2),
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_location_freight ON location_updates(freight_id);
CREATE INDEX idx_location_freight_time ON location_updates(freight_id, timestamp DESC);
CREATE INDEX idx_location_driver ON location_updates(driver_id);
```

### 9. API Endpoints

| Method | Endpoint | Purpose | Auth |
|--------|----------|---------|------|
| POST | `/api/tracking` | Update location | Matched Driver |
| GET | `/api/tracking/{freightId}/latest` | Get latest location | Shipper/Driver/Admin |
| GET | `/api/tracking/{freightId}` | Get location history | Shipper/Driver/Admin |
| POST | `/api/tracking/freight/{freightId}/deliver` | Mark delivered | Matched Driver |
| POST | `/api/tracking/freight/{freightId}/confirm-delivery` | Confirm delivery | Shipper/Admin |

**Path Aliases:** Add `/freight/{id}/deliver` → `/api/tracking/freight/{id}/deliver`, `/freight/{id}/confirm-delivery` → `/api/tracking/freight/{id}/confirm-delivery`

### 10. Testing Checklist

- [ ] Driver updates location with valid coordinates (success)
- [ ] Driver updates location with invalid latitude (should fail)
- [ ] Non-matched driver updates location (should fail)
- [ ] Update location on freight not in transit (should fail)
- [ ] Get latest location (success)
- [ ] Get location history (success)
- [ ] Get location history with time filter (success)
- [ ] Driver marks delivered (success, status changes to DELIVERED)
- [ ] Driver marks delivered before in transit (should fail)
- [ ] Shipper confirms delivery (success, status changes to COMPLETED)
- [ ] Shipper confirms before delivered (should fail)
- [ ] Non-shipper confirms delivery (should fail)
- [ ] Location updates are ordered by timestamp DESC

---

## Phase 5: Payment & Escrow System

**Goal:** Enable secure payment processing with escrow  
**Duration:** 5-6 days  
**Dependencies:** Phase 2 (Freight), Phase 3 (Bidding)  
**Frontend Impact:** Payment page becomes functional

### Dependency Analysis

**Domain Entities:**
- User (already exists from Auth phase)
- Freight (from Phase 2)
- Payment (new)

**Database Dependencies:**
- users table (already exists)
- freight table (from Phase 2)
- payments table (new)

**Authentication Dependencies:**
- SHIPPER role (already exists in User entity)
- ADMIN role (already exists in User entity)

**Frontend Dependencies:**
- Payment page (frontend exists, needs backend)

**APIs Required by Later Phases:**
- Phase 8 (Admin): Payment data used for revenue analytics and escrow overview

**Architectural Notes:**
- Payment is dependent on Freight but does not directly reference Bid
- Payment initialization typically happens after bid acceptance (Phase 3), but the entity itself only needs Freight
- Payment.escrow_status enum drives the payment state machine
- No refactoring expected - Payment is designed as a dependent entity

### 1. Required Entities

#### Payment
```java
@Entity
@Table(name = "payments")
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "freight_id")
    private Freight freight;
    
    @ManyToOne
    @JoinColumn(name = "shipper_id")
    private User shipper;
    
    @Column(name = "amount", nullable = false)
    private BigDecimal amount;
    
    @Column(name = "provider")
    private String provider; // chapa, cbe_birr, telebirr, cash
    
    @Column(name = "provider_transaction_id")
    private String providerTransactionId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "escrow_status", nullable = false)
    private EscrowStatus escrowStatus; // pending_payment, payment_held, in_transit, delivered, released, refunded
    
    @Column(name = "platform_fee")
    private BigDecimal platformFee;
    
    @Column(name = "driver_amount")
    private BigDecimal driverAmount;
    
    @Column(name = "refund_amount")
    private BigDecimal refundAmount;
    
    @Column(name = "refund_reason")
    private String refundReason;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    public enum EscrowStatus {
        PENDING_PAYMENT, PAYMENT_HELD, IN_TRANSIT, DELIVERED, RELEASED, REFUNDED
    }
}
```

### 2. DTOs

#### InitializePaymentRequest
```java
public record InitializePaymentRequest(
    @NotNull Long freightId,
    @NotNull @DecimalMin("1") BigDecimal amount,
    @NotBlank String provider
) {}
```

#### PaymentResponse
```java
public record PaymentResponse(
    Long id,
    Long freightId,
    Long shipperId,
    BigDecimal amount,
    String provider,
    String providerTransactionId,
    String escrowStatus,
    BigDecimal platformFee,
    BigDecimal driverAmount,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
```

#### VerifyPaymentRequest
```java
public record VerifyPaymentRequest(
    @NotNull Long freightId,
    String transactionId
) {}
```

#### ReleasePaymentRequest
```java
public record ReleasePaymentRequest(
    @NotNull Long freightId
) {}
```

#### RefundPaymentRequest
```java
public record RefundPaymentRequest(
    @NotNull Long freightId,
    String reason
) {}
```

### 3. Repositories

#### PaymentRepository
```java
@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByFreightId(Long freightId);
    List<Payment> findByShipperId(Long shipperId);
    List<Payment> findByEscrowStatus(EscrowStatus status);
}
```

### 4. Services

#### PaymentService
```java
public interface PaymentService {
    PaymentResponse initializePayment(Long shipperId, InitializePaymentRequest request);
    PaymentResponse verifyPayment(Long freightId, VerifyPaymentRequest request);
    PaymentResponse getPaymentByFreightId(Long freightId);
    PaymentResponse releasePayment(Long freightId, Long shipperId);
    PaymentResponse refundPayment(Long freightId, Long adminId, RefundPaymentRequest request);
    List<PaymentResponse> getShipperPayments(Long shipperId);
    List<PaymentResponse> getPaymentsByStatus(EscrowStatus status);
}
```

#### PaymentServiceImpl
- Validate freight is in MATCHED status
- Calculate platform fee (e.g., 5%)
- Calculate driver amount (amount - fee)
- Integrate with payment providers (Chapa, CBE Birr, Telebirr)
- Handle payment verification callbacks
- Implement escrow logic
- Handle refunds
- Implement idempotency for payment operations

### 5. Controllers

#### PaymentController
```java
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Payment and escrow endpoints")
public class PaymentController {
    
    @PostMapping("/initialize")
    @Operation(summary = "Initialize payment")
    public ResponseEntity<PaymentResponse> initializePayment(
        @Valid @RequestBody InitializePaymentRequest request,
        Authentication authentication
    );
    
    @PostMapping("/{freightId}/verify")
    @Operation(summary = "Verify payment")
    public ResponseEntity<PaymentResponse> verifyPayment(
        @PathVariable Long freightId,
        @RequestBody VerifyPaymentRequest request,
        Authentication authentication
    );
    
    @GetMapping("/{freightId}")
    @Operation(summary = "Get payment by freight ID")
    public ResponseEntity<PaymentResponse> getPayment(@PathVariable Long freightId);
    
    @PostMapping("/{freightId}/release")
    @Operation(summary = "Release payment from escrow")
    public ResponseEntity<PaymentResponse> releasePayment(
        @PathVariable Long freightId,
        Authentication authentication
    );
    
    @PostMapping("/{freightId}/refund")
    @Operation(summary = "Refund payment")
    public ResponseEntity<PaymentResponse> refundPayment(
        @PathVariable Long freightId,
        @RequestBody RefundPaymentRequest request,
        Authentication authentication
    );
}
```

### 6. Validation

- Amount must be >= 1 ETB
- Provider must be one of: chapa, cbe_birr, telebirr, cash
- Freight must be in MATCHED status to initialize payment
- Cannot verify payment if not PENDING_PAYMENT
- Cannot release payment if not DELIVERED
- Cannot refund if already RELEASED
- Only shipper can initialize/verify/release
- Only admin can refund
- Platform fee calculation (configurable percentage)

### 7. Security Requirements

- **POST /payments/initialize:** Authenticated (shipper or ADMIN)
- **POST /payments/{freightId}/verify:** Authenticated (shipper or ADMIN)
- **GET /payments/{freightId}:** Authenticated (shipper, driver, or ADMIN)
- **POST /payments/{freightId}/release:** Authenticated (shipper or ADMIN)
- **POST /payments/{freightId}/refund:** Authenticated (ADMIN)

### 8. Flyway Migrations

#### V6__create_payments_table.sql
```sql
CREATE TABLE IF NOT EXISTS payments (
    id BIGSERIAL PRIMARY KEY,
    freight_id BIGINT NOT NULL REFERENCES freight(id) ON DELETE CASCADE,
    shipper_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    amount DECIMAL(15, 2) NOT NULL CHECK (amount >= 1),
    provider VARCHAR(20) CHECK (provider IN ('chapa', 'cbe_birr', 'telebirr', 'cash')),
    provider_transaction_id VARCHAR(255),
    escrow_status VARCHAR(30) NOT NULL DEFAULT 'pending_payment' CHECK (escrow_status IN ('pending_payment', 'payment_held', 'in_transit', 'delivered', 'released', 'refunded')),
    platform_fee DECIMAL(15, 2),
    driver_amount DECIMAL(15, 2),
    refund_amount DECIMAL(15, 2),
    refund_reason TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(freight_id)
);

CREATE INDEX idx_payments_freight ON payments(freight_id);
CREATE INDEX idx_payments_shipper ON payments(shipper_id);
CREATE INDEX idx_payments_status ON payments(escrow_status);
```

### 9. API Endpoints

| Method | Endpoint | Purpose | Auth |
|--------|----------|---------|------|
| POST | `/api/payments/initialize` | Initialize payment | Shipper/Admin |
| POST | `/api/payments/{freightId}/verify` | Verify payment | Shipper/Admin |
| GET | `/api/payments/{freightId}` | Get payment details | Shipper/Driver/Admin |
| POST | `/api/payments/{freightId}/release` | Release payment | Shipper/Admin |
| POST | `/api/payments/{freightId}/refund` | Refund payment | Admin |

### 10. Testing Checklist

- [ ] Shipper initializes payment (success)
- [ ] Initialize payment on unmatched freight (should fail)
- [ ] Initialize payment with invalid provider (should fail)
- [ ] Verify payment (success, status changes to PAYMENT_HELD)
- [ ] Verify payment with invalid transaction (should fail)
- [ ] Get payment details (success)
- [ ] Release payment after delivery (success, status changes to RELEASED)
- [ ] Release payment before delivery (should fail)
- [ ] Admin refunds payment (success, status changes to REFUNDED)
- [ ] Non-shipper cannot initialize payment (should fail)
- [ ] Non-admin cannot refund payment (should fail)
- [ ] Platform fee calculated correctly
- [ ] Driver amount calculated correctly
- [ ] Idempotency - duplicate initialize returns same payment

---

## Phase 6: Messaging System

**Goal:** Enable in-app messaging between shippers and drivers  
**Duration:** 3-4 days  
**Dependencies:** Phase 2 (Freight)  
**Frontend Impact:** Messages page becomes functional

### Dependency Analysis

**Domain Entities:**
- User (already exists from Auth phase)
- Freight (from Phase 2)
- Message (new)

**Database Dependencies:**
- users table (already exists)
- freight table (from Phase 2)
- messages table (new)

**Authentication Dependencies:**
- DRIVER role (already exists in User entity)
- SHIPPER role (already exists in User entity)
- ADMIN role (already exists in User entity)

**Frontend Dependencies:**
- Messages page (frontend exists, needs backend)

**APIs Required by Later Phases:**
- None - messaging is a standalone feature

**Architectural Notes:**
- Message is a junction entity connecting Freight, sender (User), and receiver (User)
- Messaging can happen at any freight status, so it doesn't depend on Phase 3 (Bidding)
- No refactoring expected - Message is designed as a dependent entity

### 1. Required Entities

#### Message
```java
@Entity
@Table(name = "messages")
public class Message {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "freight_id")
    private Freight freight;
    
    @ManyToOne
    @JoinColumn(name = "sender_id")
    private User sender;
    
    @ManyToOne
    @JoinColumn(name = "receiver_id")
    private User receiver;
    
    @Column(name = "content", nullable = false, length = 2000)
    private String content;
    
    @Column(name = "masked_content")
    private String maskedContent;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private MessageType type; // text, system, alert
    
    @Column(name = "has_phone_number")
    private Boolean hasPhoneNumber = false;
    
    @Column(name = "has_payment_request")
    private Boolean hasPaymentRequest = false;
    
    @Column(name = "is_read")
    private Boolean isRead = false;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    public enum MessageType {
        TEXT, SYSTEM, ALERT
    }
}
```

### 2. DTOs

#### CreateMessageRequest
```java
public record CreateMessageRequest(
    @NotNull Long freightId,
    @NotNull Long receiverId,
    @NotBlank @Size(max = 2000) String content,
    @NotBlank String type
) {}
```

#### MessageResponse
```java
public record MessageResponse(
    Long id,
    Long freightId,
    Long senderId,
    String senderName,
    String senderRole,
    Long receiverId,
    String content,
    String maskedContent,
    String type,
    Boolean hasPhoneNumber,
    Boolean hasPaymentRequest,
    Boolean isRead,
    LocalDateTime createdAt
) {}
```

### 3. Repositories

#### MessageRepository
```java
@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findByFreightIdOrderByCreatedAtAsc(Long freightId);
    List<Message> findBySenderIdAndReceiverId(Long senderId, Long receiverId);
    List<Message> findByReceiverIdAndIsReadFalse(Long receiverId);
    long countByFreightIdAndReceiverIdAndIsReadFalse(Long freightId, Long receiverId);
}
```

### 4. Services

#### MessageService
```java
public interface MessageService {
    MessageResponse createMessage(Long senderId, CreateMessageRequest request);
    List<MessageResponse> getFreightMessages(Long freightId, Long userId);
    List<MessageResponse> getUnreadMessages(Long userId);
    void markAsRead(Long messageId, Long userId);
    void markConversationAsRead(Long freightId, Long userId);
    long getUnreadCount(Long userId);
}
```

#### MessageServiceImpl
- Validate sender and receiver are parties to the freight
- Implement content filtering (phone number masking, payment request detection)
- Create system messages for status changes
- Implement read status tracking
- Handle message moderation (optional)

### 5. Controllers

#### MessageController
```java
@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
@Tag(name = "Messages", description = "Messaging endpoints")
public class MessageController {
    
    @PostMapping
    @Operation(summary = "Send message")
    public ResponseEntity<MessageResponse> sendMessage(
        @Valid @RequestBody CreateMessageRequest request,
        Authentication authentication
    );
    
    @GetMapping("/{freightId}")
    @Operation(summary = "Get messages for freight")
    public ResponseEntity<List<MessageResponse>> getFreightMessages(
        @PathVariable Long freightId,
        Authentication authentication
    );
    
    @PatchMapping("/{messageId}/read")
    @Operation(summary = "Mark message as read")
    public ResponseEntity<Void> markAsRead(
        @PathVariable Long messageId,
        Authentication authentication
    );
    
    @GetMapping("/unread")
    @Operation(summary = "Get unread messages")
    public ResponseEntity<List<MessageResponse>> getUnreadMessages(Authentication authentication);
    
    @GetMapping("/unread/count")
    @Operation(summary = "Get unread message count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(Authentication authentication);
}
```

### 6. Validation

- Content must not exceed 2000 characters
- Sender and receiver must be parties to the freight
- Type must be one of: text, system, alert
- Only freight parties can send messages
- System messages can only be created by system
- Phone number masking for active transactions
- Payment request detection and masking

### 7. Security Requirements

- **POST /messages:** Authenticated (freight party)
- **GET /messages/{freightId}:** Authenticated (freight party)
- **PATCH /messages/{messageId}/read:** Authenticated (message receiver)
- **GET /messages/unread:** Authenticated
- **GET /messages/unread/count:** Authenticated

### 8. Flyway Migrations

#### V7__create_messages_table.sql
```sql
CREATE TABLE IF NOT EXISTS messages (
    id BIGSERIAL PRIMARY KEY,
    freight_id BIGINT NOT NULL REFERENCES freight(id) ON DELETE CASCADE,
    sender_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    receiver_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content VARCHAR(2000) NOT NULL,
    masked_content VARCHAR(2000),
    type VARCHAR(20) NOT NULL DEFAULT 'text' CHECK (type IN ('text', 'system', 'alert')),
    has_phone_number BOOLEAN DEFAULT FALSE,
    has_payment_request BOOLEAN DEFAULT FALSE,
    is_read BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_messages_freight ON messages(freight_id);
CREATE INDEX idx_messages_sender ON messages(sender_id);
CREATE INDEX idx_messages_receiver ON messages(receiver_id);
CREATE INDEX idx_messages_read ON messages(receiver_id, is_read) WHERE is_read = FALSE;
```

### 9. API Endpoints

| Method | Endpoint | Purpose | Auth |
|--------|----------|---------|------|
| POST | `/api/messages` | Send message | Freight party |
| GET | `/api/messages/{freightId}` | Get freight messages | Freight party |
| PATCH | `/api/messages/{messageId}/read` | Mark as read | Receiver |
| GET | `/api/messages/unread` | Get unread messages | Authenticated |
| GET | `/api/messages/unread/count` | Get unread count | Authenticated |

### 10. Testing Checklist

- [ ] Send valid message (success)
- [ ] Send message with content > 2000 chars (should fail)
- [ ] Non-party sends message (should fail)
- [ ] Get freight messages (success, returns only for parties)
- [ ] Messages ordered by createdAt ASC
- [ ] Mark message as read (success)
- [ ] Mark other user's message as read (should fail)
- [ ] Get unread messages (success)
- [ ] Get unread count (success)
- [ ] Phone number masking works for active transactions
- [ ] Payment request detection works
- [ ] System message creation (admin only)

---

## Phase 7: AI Engine Integration

**Goal:** Proxy AI Engine endpoints for price prediction and vehicle recommendation  
**Duration:** 2-3 days  
**Dependencies:** Phase 2 (Freight), Phase 1 (Vehicles)  
**Frontend Impact:** AI-powered features in freight-new and freight-detail pages

### Dependency Analysis

**Domain Entities:**
- None (proxy endpoints only, no new entities)

**Database Dependencies:**
- None (no database changes)

**Authentication Dependencies:**
- SHIPPER role (already exists in User entity)
- ADMIN role (already exists in User entity)

**Frontend Dependencies:**
- Freight-new page AI predictions (frontend exists, needs backend)
- Freight-detail page AI truck recommendations (frontend exists, needs backend)

**APIs Required by Later Phases:**
- None - AI integration is a standalone proxy layer

**Architectural Notes:**
- This phase is a proxy layer to the external AI Engine service (port 8000)
- Does not introduce new entities, only DTOs for request/response transformation
- Depends on Freight and Vehicle conceptually (for the data sent to AI), but not at the entity level
- Could be implemented in parallel with Phase 4, 5, or 6 since it has no entity dependencies
- No refactoring expected - proxy layer is isolated

### 1. Required Entities

None (proxy endpoints only, no new entities)

### 2. DTOs

#### PricePredictionRequest
```java
public record PricePredictionRequest(
    @NotNull @DecimalMin("0.1") BigDecimal weight,
    @NotNull @DecimalMin("1") BigDecimal distanceKm,
    @NotBlank String cargoType
) {}
```

#### PricePredictionResponse
```java
public record PricePredictionResponse(
    BigDecimal recommendedPrice,
    BigDecimal minPrice,
    BigDecimal maxPrice,
    Double confidence,
    String model,
    BigDecimal pricePerKm,
    BigDecimal pricePerTon,
    Map<String, BigDecimal> breakdown
) {}
```

#### VehicleRecommendationRequest
```java
public record VehicleRecommendationRequest(
    @NotNull @DecimalMin("0.1") BigDecimal weight,
    @NotBlank String cargoType,
    @NotNull @DecimalMin("1") BigDecimal distanceKm
) {}
```

#### VehicleRecommendationResponse
```java
public record VehicleRecommendationResponse(
    String truckType,
    String capacityRange,
    String reason,
    List<String> features,
    String riskLevel
) {}
```

#### TruckRecommendationRequest
```java
public record TruckRecommendationRequest(
    @NotNull Long freightId,
    @NotNull @DecimalMin("0.1") BigDecimal weight,
    @NotBlank String cargoType,
    BigDecimal budget
) {}
```

#### TruckRecommendationResponse
```java
public record TruckMatch(
    Long driverId,
    String driverName,
    String vehicleTruckType,
    BigDecimal vehicleCapacity,
    Double avgRating,
    Integer totalDeliveries,
    Double matchScore
) {}

public record TruckRecommendationResponse(
    List<TruckMatch> matches
) {}
```

### 3. Repositories

None (proxy to external AI Engine service)

### 4. Services

#### AIEngineService
```java
public interface AIEngineService {
    PricePredictionResponse getPricePrediction(PricePredictionRequest request);
    VehicleRecommendationResponse getVehicleRecommendation(VehicleRecommendationRequest request);
    TruckRecommendationResponse recommendTruck(TruckRecommendationRequest request);
}
```

#### AIEngineServiceImpl
- Call AI Engine service (configured URL)
- Handle service failures gracefully
- Implement caching (optional)
- Transform AI Engine responses to internal DTOs
- Add timeout configuration
- Implement retry logic for transient failures

### 5. Controllers

#### AIController
```java
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Tag(name = "AI Engine", description = "AI Engine integration endpoints")
public class AIController {
    
    @GetMapping("/price-prediction")
    @Operation(summary = "Get AI price prediction")
    public ResponseEntity<PricePredictionResponse> getPricePrediction(
        @RequestParam BigDecimal weight,
        @RequestParam BigDecimal distanceKm,
        @RequestParam String cargoType
    );
    
    @GetMapping("/vehicle-recommendation")
    @Operation(summary = "Get AI vehicle recommendation")
    public ResponseEntity<VehicleRecommendationResponse> getVehicleRecommendation(
        @RequestParam BigDecimal weight,
        @RequestParam String cargoType,
        @RequestParam BigDecimal distanceKm
    );
    
    @PostMapping("/recommend-truck")
    @Operation(summary = "Get AI truck recommendations for freight")
    public ResponseEntity<TruckRecommendationResponse> recommendTruck(
        @Valid @RequestBody TruckRecommendationRequest request,
        Authentication authentication
    );
}
```

### 6. Validation

- Weight must be >= 0.1 tons
- Distance must be >= 1 km
- Cargo type must be valid
- Budget must be >= 1 ETB (if provided)
- Freight must exist for truck recommendation
- AI Engine service availability check

### 7. Security Requirements

- **GET /ai/price-prediction:** Public (or authenticated for rate limiting)
- **GET /ai/vehicle-recommendation:** Public (or authenticated for rate limiting)
- **POST /ai/recommend-truck:** Authenticated (shipper or ADMIN)

### 8. Flyway Migrations

None (no database changes)

### 9. API Endpoints

| Method | Endpoint | Purpose | Auth |
|--------|----------|---------|------|
| GET | `/api/ai/price-prediction` | Get price prediction | Public |
| GET | `/api/ai/vehicle-recommendation` | Get vehicle recommendation | Public |
| POST | `/api/ai/recommend-truck` | Get truck recommendations | Shipper/Admin |

**Configuration:** Add AI Engine URL to application.yml (default: http://localhost:8000)

### 10. Testing Checklist

- [ ] Get price prediction with valid parameters (success)
- [ ] Get price prediction with invalid weight (should fail)
- [ ] Get vehicle recommendation with valid parameters (success)
- [ ] Get truck recommendations for freight (success)
- [ ] AI Engine service unavailable (graceful degradation)
- [ ] Response transformation correct
- [ ] Timeout handling works
- [ ] Retry logic works for transient failures
- [ ] Caching reduces AI Engine calls (if implemented)

---

## Phase 8: Admin Dashboard

**Goal:** Enable comprehensive admin management  
**Duration:** 7-10 days  
**Dependencies:** All previous phases  
**Frontend Impact:** Admin page becomes fully functional

### Dependency Analysis

**Domain Entities:**
- User (already exists from Auth phase)
- Vehicle (from Phase 1)
- Freight (from Phase 2)
- Bid (from Phase 3)
- LocationUpdate (from Phase 4)
- Payment (from Phase 5)
- Message (from Phase 6)
- Dispute (new)
- Document (new)
- PricingSettings (new)

**Database Dependencies:**
- users table (already exists)
- vehicles table (from Phase 1)
- freight table (from Phase 2)
- bids table (from Phase 3)
- location_updates table (from Phase 4)
- payments table (from Phase 5)
- messages table (from Phase 6)
- disputes table (new)
- documents table (new)
- pricing_settings table (new)

**Authentication Dependencies:**
- ADMIN role (already exists in User entity)
- DRIVER role (for document uploads)
- FLEET_OWNER role (for document uploads)

**Frontend Dependencies:**
- Admin page (frontend exists, needs backend)

**APIs Required by Later Phases:**
- None - this is the final phase

**Architectural Notes:**
- Admin Dashboard aggregates data from all previous phases
- Introduces new entities (Dispute, Document, PricingSettings) that are admin-centric
- Document management requires file upload/download infrastructure
- Analytics queries may require database views or materialized views for performance
- No refactoring expected - Admin is designed as a read/write aggregation layer

### 1. Required Entities

#### AdminStats (View/DTO, not entity)
```java
public record AdminStats(
    Integer totalUsers,
    Integer activeDrivers,
    Integer postedFreight,
    Integer completedFreight,
    Integer totalPayments,
    BigDecimal escrowHeld,
    BigDecimal platformRevenue,
    Integer openDisputes
) {}
```

#### Dispute
```java
@Entity
@Table(name = "disputes")
public class Dispute {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "freight_id")
    private Freight freight;
    
    @ManyToOne
    @JoinColumn(name = "reported_by_id")
    private User reportedBy;
    
    @Column(name = "reason", nullable = false)
    private String reason;
    
    @Column(name = "description")
    private String description;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private DisputeStatus status; // open, investigating, resolved, closed
    
    @Column(name = "resolution")
    private String resolution;
    
    @Column(name = "admin_notes")
    private String adminNotes;
    
    @Column(name = "refund_amount")
    private BigDecimal refundAmount;
    
    @ManyToOne
    @JoinColumn(name = "resolved_by_id")
    private User resolvedBy;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;
    
    public enum DisputeStatus {
        OPEN, INVESTIGATING, RESOLVED, CLOSED
    }
}
```

#### Document
```java
@Entity
@Table(name = "documents")
public class Document {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;
    
    @Column(name = "document_type", nullable = false)
    private String documentType; // license, national_id, vehicle_registration, insurance, tin
    
    @Column(name = "file_path", nullable = false)
    private String filePath;
    
    @Column(name = "file_name", nullable = false)
    private String fileName;
    
    @Column(name = "file_size")
    private Long fileSize;
    
    @Column(name = "mime_type")
    private String mimeType;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private DocumentStatus status; // pending, approved, rejected
    
    @Column(name = "rejection_reason")
    private String rejectionReason;
    
    @ManyToOne
    @JoinColumn(name = "reviewed_by_id")
    private User reviewedBy;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;
    
    public enum DocumentStatus {
        PENDING, APPROVED, REJECTED
    }
}
```

#### PricingSettings
```java
@Entity
@Table(name = "pricing_settings")
public class PricingSettings {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "rate_min", nullable = false)
    private BigDecimal rateMin;
    
    @Column(name = "rate_max", nullable = false)
    private BigDecimal rateMax;
    
    @Column(name = "platform_fee_percent", nullable = false)
    private BigDecimal platformFeePercent;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
```

### 2. DTOs

#### ResolveDisputeRequest
```java
public record ResolveDisputeRequest(
    @NotBlank String resolution,
    String adminNotes,
    BigDecimal refundAmount
) {}
```

#### ReviewDocumentRequest
```java
public record ReviewDocumentRequest(
    @NotBlank String action, // approve, reject
    String rejectionReason
) {}
```

#### UpdatePricingRequest
```java
public record UpdatePricingRequest(
    @NotNull @DecimalMin("1") BigDecimal rateMin,
    @NotNull @DecimalMin("1") BigDecimal rateMax
) {}
```

#### CreateUserRequest
```java
public record CreateUserRequest(
    @NotBlank String name,
    @NotBlank @Email String email,
    @NotBlank String phone,
    @NotBlank String password,
    @NotBlank String role
) {}
```

#### UpdateUserRequest
```java
public record UpdateUserRequest(
    String name,
    String email,
    String phone,
    String password,
    String role
) {}
```

### 3. Repositories

#### DisputeRepository
```java
@Repository
public interface DisputeRepository extends JpaRepository<Dispute, Long> {
    List<Dispute> findByStatus(DisputeStatus status);
    List<Dispute> findByReportedById(Long userId);
}
```

#### DocumentRepository
```java
@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {
    List<Document> findByUserId(Long userId);
    List<Document> findByStatus(DocumentStatus status);
    List<Document> findByDocumentType(String documentType);
}
```

#### PricingSettingsRepository
```java
@Repository
public interface PricingSettingsRepository extends JpaRepository<PricingSettings, Long> {
    Optional<PricingSettings> findFirstByOrderByIdAsc();
}
```

### 4. Services

#### AdminService
```java
public interface AdminService {
    AdminStats getStats();
    List<UserResponse> getAllUsers();
    UserResponse createUser(CreateUserRequest request);
    UserResponse updateUser(Long id, UpdateUserRequest request);
    void deleteUser(Long id);
    List<DriverResponse> getAllDrivers();
    DriverResponse createDriver(CreateDriverRequest request);
    void updateDriverStatus(Long id, String status);
    List<PaymentResponse> getAllPayments();
    List<DisputeResponse> getAllDisputes();
    DisputeResponse resolveDispute(Long id, ResolveDisputeRequest request);
    List<DocumentResponse> getAllDocuments();
    DocumentResponse reviewDocument(Long id, ReviewDocumentRequest request);
    PricingSettingsResponse getPricingSettings();
    PricingSettingsResponse updatePricingSettings(UpdatePricingRequest request);
    List<FreightResponse> getAllFreight();
    List<PaymentResponse> getEscrowOverview();
    List<UserResponse> getFleetOwners();
    RevenueAnalyticsResponse getRevenueAnalytics();
    RouteAnalyticsResponse getRouteAnalytics();
    CargoAnalyticsResponse getCargoAnalytics();
}
```

#### AdminServiceImpl
- Implement statistics aggregation
- Implement user CRUD operations
- Implement driver management
- Implement document review workflow
- Implement dispute resolution
- Implement pricing settings management
- Implement analytics queries
- File upload/download handling

### 5. Controllers

#### AdminController
```java
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Admin dashboard endpoints")
public class AdminController {
    
    @GetMapping("/stats")
    @Operation(summary = "Get dashboard statistics")
    public ResponseEntity<AdminStats> getStats();
    
    @GetMapping("/users")
    @Operation(summary = "Get all users")
    public ResponseEntity<List<UserResponse>> getAllUsers();
    
    @PostMapping("/users")
    @Operation(summary = "Create user")
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request);
    
    @PutMapping("/users/{id}")
    @Operation(summary = "Update user")
    public ResponseEntity<UserResponse> updateUser(
        @PathVariable Long id,
        @Valid @RequestBody UpdateUserRequest request
    );
    
    @DeleteMapping("/users/{id}")
    @Operation(summary = "Delete user")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id);
    
    @GetMapping("/drivers")
    @Operation(summary = "Get all drivers")
    public ResponseEntity<List<DriverResponse>> getAllDrivers();
    
    @PostMapping("/drivers")
    @Operation(summary = "Create driver")
    public ResponseEntity<DriverResponse> createDriver(@Valid @RequestBody CreateDriverRequest request);
    
    @PatchMapping("/drivers/{id}/status")
    @Operation(summary = "Update driver status")
    public ResponseEntity<DriverResponse> updateDriverStatus(
        @PathVariable Long id,
        @RequestParam String status
    );
    
    @GetMapping("/payments")
    @Operation(summary = "Get all payments")
    public ResponseEntity<List<PaymentResponse>> getAllPayments();
    
    @GetMapping("/disputes")
    @Operation(summary = "Get all disputes")
    public ResponseEntity<List<DisputeResponse>> getAllDisputes();
    
    @PatchMapping("/disputes/{id}/resolve")
    @Operation(summary = "Resolve dispute")
    public ResponseEntity<DisputeResponse> resolveDispute(
        @PathVariable Long id,
        @Valid @RequestBody ResolveDisputeRequest request
    );
    
    @GetMapping("/escrow")
    @Operation(summary = "Get escrow overview")
    public ResponseEntity<List<PaymentResponse>> getEscrowOverview();
    
    @GetMapping("/driver-documents")
    @Operation(summary = "Get all driver documents")
    public ResponseEntity<List<DocumentResponse>> getAllDocuments();
    
    @PatchMapping("/driver-documents/{id}/review")
    @Operation(summary = "Review document")
    public ResponseEntity<DocumentResponse> reviewDocument(
        @PathVariable Long id,
        @Valid @RequestBody ReviewDocumentRequest request
    );
    
    @GetMapping("/settings/pricing")
    @Operation(summary = "Get pricing settings")
    public ResponseEntity<PricingSettingsResponse> getPricingSettings();
    
    @PatchMapping("/settings/pricing")
    @Operation(summary = "Update pricing settings")
    public ResponseEntity<PricingSettingsResponse> updatePricingSettings(
        @Valid @RequestBody UpdatePricingRequest request
    );
    
    @GetMapping("/fleet-owners")
    @Operation(summary = "Get fleet owners")
    public ResponseEntity<List<UserResponse>> getFleetOwners();
    
    @GetMapping("/analytics/revenue")
    @Operation(summary = "Get revenue analytics")
    public ResponseEntity<RevenueAnalyticsResponse> getRevenueAnalytics();
    
    @GetMapping("/analytics/routes")
    @Operation(summary = "Get route analytics")
    public ResponseEntity<RouteAnalyticsResponse> getRouteAnalytics();
    
    @GetMapping("/analytics/cargo")
    @Operation(summary = "Get cargo analytics")
    public ResponseEntity<CargoAnalyticsResponse> getCargoAnalytics();
}
```

#### DocumentController
```java
@RestController
@RequestMapping("/api/driver/documents")
@RequiredArgsConstructor
@Tag(name = "Documents", description = "Document management endpoints")
public class DocumentController {
    
    @PostMapping("/upload")
    @Operation(summary = "Upload document")
    public ResponseEntity<DocumentResponse> uploadDocument(
        @RequestParam("file") MultipartFile file,
        @RequestParam("documentType") String documentType,
        Authentication authentication
    );
    
    @GetMapping("/{id}/file")
    @Operation(summary = "Download document file")
    public ResponseEntity<Resource> downloadDocument(@PathVariable Long id);
}
```

### 6. Validation

- All admin operations require ADMIN role
- User role validation (shipper, driver, admin, fleet_owner)
- Document type validation
- File size limits (e.g., 10MB)
- File type validation (PDF, JPG, PNG)
- Dispute resolution validation
- Pricing settings validation (min < max)
- Analytics date range validation

### 7. Security Requirements

- **All /api/admin/* endpoints:** Authenticated (ADMIN role only)
- **POST /api/driver/documents/upload:** Authenticated (DRIVER or FLEET_OWNER)
- **GET /api/driver/documents/{id}/file:** Authenticated (ADMIN or document owner)

### 8. Flyway Migrations

#### V8__create_admin_tables.sql
```sql
CREATE TABLE IF NOT EXISTS disputes (
    id BIGSERIAL PRIMARY KEY,
    freight_id BIGINT NOT NULL REFERENCES freight(id) ON DELETE CASCADE,
    reported_by_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reason VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'open' CHECK (status IN ('open', 'investigating', 'resolved', 'closed')),
    resolution TEXT,
    admin_notes TEXT,
    refund_amount DECIMAL(15, 2),
    resolved_by_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS documents (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    document_type VARCHAR(50) NOT NULL CHECK (document_type IN ('license', 'national_id', 'vehicle_registration', 'insurance', 'tin')),
    file_path VARCHAR(500) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_size BIGINT,
    mime_type VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'approved', 'rejected')),
    rejection_reason TEXT,
    reviewed_by_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS pricing_settings (
    id BIGSERIAL PRIMARY KEY,
    rate_min DECIMAL(10, 2) NOT NULL DEFAULT 18,
    rate_max DECIMAL(10, 2) NOT NULL DEFAULT 28,
    platform_fee_percent DECIMAL(5, 2) NOT NULL DEFAULT 5.00,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_disputes_freight ON disputes(freight_id);
CREATE INDEX idx_disputes_status ON disputes(status);
CREATE INDEX idx_documents_user ON documents(user_id);
CREATE INDEX idx_documents_status ON documents(status);
CREATE INDEX idx_documents_type ON documents(document_type);

INSERT INTO pricing_settings (rate_min, rate_max, platform_fee_percent) 
VALUES (18, 28, 5.00);
```

### 9. API Endpoints

| Method | Endpoint | Purpose | Auth |
|--------|----------|---------|------|
| GET | `/api/admin/stats` | Dashboard statistics | Admin |
| GET | `/api/admin/users` | List all users | Admin |
| POST | `/api/admin/users` | Create user | Admin |
| PUT | `/api/admin/users/{id}` | Update user | Admin |
| DELETE | `/api/admin/users/{id}` | Delete user | Admin |
| GET | `/api/admin/drivers` | List drivers | Admin |
| POST | `/api/admin/drivers` | Create driver | Admin |
| PATCH | `/api/admin/drivers/{id}/status` | Update driver status | Admin |
| GET | `/api/admin/payments` | List payments | Admin |
| GET | `/api/admin/disputes` | List disputes | Admin |
| PATCH | `/api/admin/disputes/{id}/resolve` | Resolve dispute | Admin |
| GET | `/api/admin/escrow` | Escrow overview | Admin |
| GET | `/api/admin/driver-documents` | List documents | Admin |
| PATCH | `/api/admin/driver-documents/{id}/review` | Review document | Admin |
| GET | `/api/admin/settings/pricing` | Get pricing settings | Admin |
| PATCH | `/api/admin/settings/pricing` | Update pricing settings | Admin |
| GET | `/api/admin/fleet-owners` | List fleet owners | Admin |
| GET | `/api/admin/analytics/revenue` | Revenue analytics | Admin |
| GET | `/api/admin/analytics/routes` | Route analytics | Admin |
| GET | `/api/admin/analytics/cargo` | Cargo analytics | Admin |
| POST | `/api/driver/documents/upload` | Upload document | Driver/FleetOwner |
| GET | `/api/driver/documents/{id}/file` | Download document | Admin/Owner |

**Path Aliases:** Add `/drivers` → `/api/admin/drivers`, `/users` → `/api/admin/users`, `/disputes` → `/api/admin/disputes`, `/trips` → `/api/admin/trips` for frontend compatibility

### 10. Testing Checklist

- [ ] Get dashboard statistics (success)
- [ ] Create user (success)
- [ ] Create user with invalid role (should fail)
- [ ] Update user (success)
- [ ] Delete user (success)
- [ ] Create driver (success)
- [ ] Update driver status (success)
- [ ] Get all payments (success)
- [ ] Get all disputes (success)
- [ ] Resolve dispute (success)
- [ ] Resolve dispute with invalid status (should fail)
- [ ] Get escrow overview (success)
- [ ] Upload document (success)
- [ ] Upload document with invalid type (should fail)
- [ ] Upload document exceeding size limit (should fail)
- [ ] Review document - approve (success)
- [ ] Review document - reject (success)
- [ ] Get pricing settings (success)
- [ ] Update pricing settings (success)
- [ ] Update pricing with min > max (should fail)
- [ ] Get revenue analytics (success)
- [ ] Get route analytics (success)
- [ ] Get cargo analytics (success)
- [ ] Non-admin cannot access admin endpoints (should fail)
- [ ] Document download works for authorized users
- [ ] Document download fails for unauthorized users

---

## Summary

### Phase Overview

| Phase | Feature | Duration | Entity Dependencies | Endpoints |
|-------|---------|----------|-------------------|-----------|
| 1 | Vehicle Management | 3-4 days | None (User exists) | 6 |
| 2 | Freight/Cargo Management | 5-7 days | Phase 1 (Vehicle) | 6 |
| 3 | Bidding System | 4-5 days | Phase 1 (Vehicle), Phase 2 (Freight) | 6 |
| 4 | Tracking & Location | 3-4 days | Phase 2 (Freight) | 5 |
| 5 | Payment & Escrow | 5-6 days | Phase 2 (Freight), Phase 3 (business flow) | 5 |
| 6 | Messaging System | 3-4 days | Phase 2 (Freight) | 5 |
| 7 | AI Engine Integration | 2-3 days | None (proxy layer) | 3 |
| 8 | Admin Dashboard | 7-10 days | All previous phases | 25+ |

### Total Estimated Effort

- **Total Duration:** 32-43 days (6-8 weeks)
- **Total Endpoints:** 61+
- **Total Entities:** 8 new entities (Vehicle, Freight, Bid, LocationUpdate, Payment, Message, Dispute, Document, PricingSettings)
- **Total Flyway Migrations:** 7 migration files

### Critical Path

**Sequential Critical Path:** Phase 1 → Phase 2 → Phase 3 → Phase 5

This path represents the core freight flow: register vehicles → post freight → accept bids → process payments.

**Parallel Development Opportunities:**
- **Phase 4 (Tracking)** can be developed in parallel with Phase 3 after Phase 2 (only depends on Freight entity)
- **Phase 6 (Messaging)** can be developed in parallel with Phase 3 after Phase 2 (only depends on Freight entity)
- **Phase 7 (AI)** can be developed in parallel with Phase 3 after Phase 2 (no entity dependencies, proxy layer)

**Optimized Timeline with Parallelization:**
- **Week 1:** Phase 1 (Vehicles)
- **Week 2-3:** Phase 2 (Freight)
- **Week 4:** Phase 3 (Bidding) + Phase 4 (Tracking) + Phase 6 (Messaging) + Phase 7 (AI) in parallel
- **Week 5:** Phase 5 (Payment)
- **Week 6-8:** Phase 8 (Admin Dashboard)

**Potential Reduction:** 32-43 days → 28-35 days with parallel development (1-2 weeks saved)

### Dependency Graph

```
Phase 1 (Vehicle)
    ↓
Phase 2 (Freight) ←──────────────┐
    ↓                              │
Phase 3 (Bidding)                 │
    ↓                              │
Phase 5 (Payment)                  │
                                   │
Phase 4 (Tracking) ───────────────┤ (can start after Phase 2)
                                   │
Phase 6 (Messaging) ───────────────┤ (can start after Phase 2)
                                   │
Phase 7 (AI) ──────────────────────┤ (can start after Phase 2)
                                   │
Phase 8 (Admin) ←──────────────────┘ (requires all)
```

### Architectural Validation

**No Reordering Required:** The current phase order is optimal from a domain dependency perspective:

1. **Phase 1 first:** Vehicle has no dependencies and is referenced by Freight and Bid
2. **Phase 2 second:** Freight is the central aggregate root, depends on Vehicle for full functionality
3. **Phase 3 third:** Bid depends on both Vehicle and Freight as a junction entity
4. **Phase 4-6:** These depend only on Freight and can be parallelized
5. **Phase 5 fifth:** Payment depends on Freight and the business flow from Phase 3
6. **Phase 7:** AI integration has no entity dependencies, can be parallelized
7. **Phase 8 last:** Admin aggregates all previous data

**No Refactoring Risk:** Each phase introduces entities that are designed as dependent entities with proper foreign key constraints. No circular dependencies exist.

### Risk Mitigation

1. **AI Engine Dependency:** Phase 7 includes graceful degradation if AI Engine is unavailable
2. **Payment Provider Integration:** Phase 5 includes mock mode for testing without real payment providers
3. **File Storage:** Document upload uses local storage by default, can be switched to S3
4. **Path Compatibility:** Each phase includes path aliases for frontend compatibility

### Success Criteria

After each phase, the corresponding frontend pages should be fully functional:
- **Phase 1:** Vehicles page
- **Phase 2:** Freight list, freight detail, freight-new pages
- **Phase 3:** Bidding functionality in freight detail
- **Phase 4:** Tracking page
- **Phase 5:** Payment page
- **Phase 6:** Messages page
- **Phase 7:** AI predictions in freight-new and freight-detail
- **Phase 8:** Complete admin dashboard
