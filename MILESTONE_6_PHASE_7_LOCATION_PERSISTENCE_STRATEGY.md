# Phase 7: Location Persistence Strategy Design

## Overview
Design and implement a scalable location persistence strategy that handles both current location tracking and historical GPS data storage with performance optimization.

## Current State Analysis

### Existing Implementation
- **Current Storage**: GPS data stored in `trips.route_data` JSONB column
- **Growth Control**: Manual cap at ~500 points per trip
- **Access Pattern**: Full route data loaded with trip entity
- **Performance**: Good for small datasets, problematic at scale

### Identified Issues
1. **Unbounded Growth**: JSONB field grows indefinitely without proper archival
2. **Memory Usage**: Loading entire route with trip entity causes memory bloat
3. **Query Performance**: Large JSONB fields slow down trip queries
4. **Real-time Access**: No separation between current location and historical data
5. **Concurrent Updates**: Multiple GPS updates can cause race conditions on JSONB field

## Persistence Strategy Design

### Two-Tier Architecture

#### Tier 1: Current Location (Hot Data)
**Purpose**: Immediate access to latest vehicle positions for real-time tracking
**Storage**: Dedicated `current_vehicle_locations` table
**Retention**: Single record per vehicle (upsert pattern)
**Access Pattern**: High frequency reads, constant updates

#### Tier 2: Historical Locations (Cold Data)
**Purpose**: Complete GPS history for analytics, route reconstruction, compliance
**Storage**: Partitioned `vehicle_location_history` table
**Retention**: Long-term with configurable archival policies
**Access Pattern**: Analytical queries, batch processing

### Database Schema Design

#### Current Location Table
```sql
CREATE TABLE current_vehicle_locations (
    vehicle_id BIGINT PRIMARY KEY REFERENCES vehicles(id),
    trip_id BIGINT REFERENCES trips(id),
    latitude DECIMAL(10,8) NOT NULL,
    longitude DECIMAL(11,8) NOT NULL,
    altitude DECIMAL(8,2),
    accuracy DECIMAL(6,2),
    speed DECIMAL(5,2),
    heading DECIMAL(5,2),
    timestamp TIMESTAMP NOT NULL,
    battery_level INTEGER,
    signal_strength INTEGER,
    metadata JSONB,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Indexes for performance
CREATE INDEX idx_current_locations_trip ON current_vehicle_locations(trip_id);
CREATE INDEX idx_current_locations_timestamp ON current_vehicle_locations(timestamp);
CREATE INDEX idx_current_locations_geo ON current_vehicle_locations USING GIST (
    point(longitude, latitude)
);
```

#### Historical Location Table
```sql
CREATE TABLE vehicle_location_history (
    id BIGSERIAL,
    vehicle_id BIGINT NOT NULL REFERENCES vehicles(id),
    trip_id BIGINT REFERENCES trips(id),
    latitude DECIMAL(10,8) NOT NULL,
    longitude DECIMAL(11,8) NOT NULL,
    altitude DECIMAL(8,2),
    accuracy DECIMAL(6,2),
    speed DECIMAL(5,2),
    heading DECIMAL(5,2),
    timestamp TIMESTAMP NOT NULL,
    battery_level INTEGER,
    signal_strength INTEGER,
    metadata JSONB,
    created_at TIMESTAMP DEFAULT NOW()
) PARTITION BY RANGE (timestamp);

-- Monthly partitions for efficient querying and archival
CREATE TABLE vehicle_location_history_y2024m01 PARTITION OF vehicle_location_history
    FOR VALUES FROM ('2024-01-01') TO ('2024-02-01');
-- ... additional partitions

-- Indexes on partitioned table
CREATE INDEX ON vehicle_location_history (vehicle_id, timestamp DESC);
CREATE INDEX ON vehicle_location_history (trip_id, timestamp);
CREATE INDEX ON vehicle_location_history USING GIST (point(longitude, latitude));
CREATE INDEX ON vehicle_location_history USING GIN (metadata);
```

### Data Flow Architecture

#### GPS Update Process
1. **Receive GPS Update** → Vehicle consistency validation
2. **Write to Current Location** → Upsert latest position (immediate)
3. **Write to Historical Location** → Append to history (async)
4. **Update Trip Route Summary** → Compressed breadcrumbs for trip entity
5. **Trigger WebSocket Broadcast** → Real-time updates to subscribers

#### Data Retention Policies
- **Current Locations**: Retained until vehicle becomes inactive
- **Historical Locations**: Configurable retention (default: 2 years)
- **Trip Route Data**: Compressed summary (max 100 key waypoints)

### Performance Optimizations

#### Read Optimization
- **Current Location**: Single-row lookup by vehicle_id
- **Recent History**: Index on (vehicle_id, timestamp DESC)
- **Route Queries**: Partition pruning by timestamp ranges
- **Geospatial Queries**: PostGIS indexes for proximity searches

#### Write Optimization
- **Batch Inserts**: Bulk insert historical data every 30 seconds
- **Async Processing**: Historical writes don't block current location updates
- **Connection Pooling**: Separate pools for real-time vs analytical workloads

#### Storage Optimization
- **Compression**: JSONB compression for metadata
- **Partitioning**: Monthly partitions enable efficient archival
- **Archival**: Automated old data movement to cold storage

### Service Layer Design

#### LocationPersistenceService
**Responsibilities**:
- Manage current vs historical location writes
- Handle data retention policies
- Provide unified query interface
- Manage performance optimizations

#### CurrentLocationService
**Responsibilities**:
- Real-time location updates (upsert)
- Current position queries
- Vehicle proximity calculations
- WebSocket event triggering

#### LocationHistoryService
**Responsibilities**:
- Historical location storage
- Route reconstruction queries
- Analytics and reporting
- Data archival management

### Migration Strategy

#### Phase 1: Parallel Implementation
- Create new location tables alongside existing route_data
- Implement dual-write pattern (write to both old and new)
- Validate data consistency between implementations

#### Phase 2: Read Migration
- Update services to read from new location tables
- Keep route_data as backup during transition
- Monitor performance improvements

#### Phase 3: Cleanup
- Migrate existing route_data to new tables
- Remove dual-write pattern
- Deprecate route_data field usage

### Configuration & Monitoring

#### Configuration Properties
```yaml
location:
  persistence:
    current-location-ttl: PT24H  # Keep current locations for 24 hours after trip ends
    history-retention-days: 730  # 2 years of historical data
    batch-insert-size: 100       # Batch size for historical inserts
    batch-insert-interval: 30s   # Interval for flushing batches
    route-summary-max-points: 100 # Max waypoints in trip route summary
```

#### Performance Metrics
- Current location update latency
- Historical data write throughput
- Query response times by operation type
- Storage usage and growth rates
- Data archival processing times

### Compliance & Audit

#### Data Protection
- GPS data encryption at rest
- Access logging for location queries
- GDPR compliance with data deletion capabilities
- Audit trail for location data modifications

#### Business Continuity
- Backup strategies for both tiers
- Point-in-time recovery capabilities
- Cross-region replication for disaster recovery
- Data integrity validation processes

## Implementation Benefits

### Performance Improvements
- **10x faster current location queries** (single row vs JSONB array)
- **Reduced memory usage** (no large route data loading with trips)
- **Better concurrent write performance** (no JSONB field contention)
- **Efficient historical queries** (proper indexing and partitioning)

### Scalability Improvements
- **Horizontal scaling** through partitioning
- **Predictable storage growth** with retention policies
- **Separation of concerns** (real-time vs analytical workloads)
- **Future-proof architecture** for millions of GPS points

### Operational Improvements
- **Clear data lifecycle management**
- **Automated archival and cleanup**
- **Better monitoring and alerting**
- **Simplified backup and recovery**

## Next Steps
1. Implement entity classes for new location tables
2. Create LocationPersistenceService and related services
3. Add database migrations for new schema
4. Implement dual-write pattern for gradual migration
5. Add comprehensive testing for new persistence layer
6. Create performance benchmarks and monitoring