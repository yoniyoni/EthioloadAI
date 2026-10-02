# Migration Decisions
## EthioloadAI Laravel to Spring Boot Migration

**Project:** EthioloadAI  
**Source:** Laravel 11.x (PHP)  
**Target:** Spring Boot 3 (Java 21)  
**Document Version:** 1.0  
**Created:** July 8, 2026

---

## TABLE OF CONTENTS

1. [Migration Approach](#1-migration-approach)
2. [Package Structure Decisions](#2-package-structure-decisions)
3. [Redis Architecture](#3-redis-architecture)
4. [WebSocket Architecture](#4-websocket-architecture)
5. [Authentication Strategy](#5-authentication-strategy)
6. [Configuration Philosophy](#6-configuration-philosophy)
7. [Accepted Trade-offs](#7-accepted-trade-offs)
8. [Deferred Enhancements](#8-deferred-enhancements)
9. [Technologies Approved for Future Phases](#9-technologies-approved-for-future-phases)
10. [Technologies Intentionally Postponed](#10-technologies-intentionally-postponed)

---

## 1. MIGRATION APPROACH

### 1.1 Decision: Incremental Migration, Not Rewrite

**Decision:** This is a migration, not a rewrite. The Laravel backend remains the reference implementation until migration is complete.

**Preserved Components:**
- PostgreSQL database (existing schema and data)
- React frontend (existing API contracts maintained)
- Flutter application (existing API contracts maintained)
- FastAPI AI service (existing integration maintained)
- API contracts (unless intentionally changed)

**Rationale:**
- Minimizes risk by maintaining a working production system
- Allows gradual migration of modules with continuous validation
- Preserves existing business logic and data integrity
- Reduces downtime and deployment complexity
- Enables A/B testing between Laravel and Spring Boot implementations

**Migration Strategy:**
1. Implement Spring Boot alongside Laravel (dual deployment)
2. Migrate modules incrementally by priority
3. Route traffic to Spring Boot for migrated modules
4. Validate each module before proceeding
5. Decommission Laravel after full migration

---

## 2. PACKAGE STRUCTURE DECISIONS

### 2.1 Decision: Domain-First Package Structure

**Decision:** Keep a domain-first package structure with approved modules only.

**Approved Top-Level Modules:**
- auth
- user
- driver
- vehicle
- cargo
- bid
- booking
- trip
- payment
- admin
- ai
- notification
- audit
- integration
- common
- security
- config

**Intentionally Omitted as Top-Level Modules:**
- routing (deferred to integration subpackage)
- geocoding (deferred to integration subpackage)
- backhaul (deferred to ai subpackage)
- websocket (deferred to config subpackage)
- platform (deferred to admin subpackage)
- job (deferred to future phase)
- event (deferred to future phase)

**Rationale:**
- **Domain-first structure** aligns with business domains, not technical layers
- **Minimal module set** prevents premature abstraction and over-engineering
- **Deferred modules** can become subpackages as the project grows
- **No additional top-level modules** without strong architectural justification
- **Clear boundaries** between domains reduce coupling

**Module Organization:**
- Each domain module contains: controller, service, repository, entity, dto, mapper
- Cross-cutting concerns (common, security, config) provide shared functionality
- Integration module handles external service integrations (routing, geocoding)
- AI module handles AI engine integration and backhaul logic

---

## 3. REDIS ARCHITECTURE

### 3.1 Decision: Redis is Part of Long-Term Architecture

**Decision:** Redis IS part of the long-term architecture. Include Redis configuration and document planned uses.

**Planned Redis Uses:**
- **Caching**: API responses, route calculations, geocoding results
- **Rate Limiting**: API endpoint throttling
- **Queues**: Asynchronous job processing (backhaul recommendations)
- **Pub/Sub**: WebSocket message distribution for horizontal scaling
- **Distributed Locking**: Prevent duplicate operations across instances

**Rationale:**
- Redis provides high-performance caching for frequently accessed data
- Rate limiting protects API endpoints from abuse
- Queues enable asynchronous processing of long-running tasks
- Pub/Sub enables WebSocket scaling across multiple instances
- Distributed locking prevents race conditions in distributed deployments

**Implementation Notes:**
- Redis is configured in CacheConfig with 1-hour default TTL
- Spring Data Redis starter is included in dependencies
- Redis configuration is present in all application profiles (dev, prod, test)
- Do not redesign the application around Redis from day one
- Add Redis usage incrementally as needed during migration

---

## 4. WEBSOCKET ARCHITECTURE

### 4.1 Decision: WebSockets Replace Polling for Real-Time Features

**Decision:** The application will use WebSockets to replace polling for real-time features.

**WebSocket Use Cases:**
- Live driver tracking
- Shipment tracking
- Booking updates
- Bid notifications
- Chat/messaging
- Admin live dashboard

**Technology Stack:**
- Spring WebSocket with STOMP
- In-memory message broker (initial deployment)
- SockJS fallback for browser compatibility

**Scaling Strategy:**
- **Initial deployment**: Use in-memory message broker (`enableSimpleBroker()`)
- **Horizontal scaling**: Migrate to Redis Pub/Sub by replacing `enableSimpleBroker()` with `enableStompBrokerRelay()`
- **Redis Pub/Sub**: Enables WebSocket message distribution across multiple instances

**Rationale:**
- WebSockets provide real-time bidirectional communication
- Eliminates polling overhead and reduces server load
- Improves user experience with instant updates
- STOMP provides a simple messaging protocol
- Redis Pub/Sub enables horizontal scaling when needed

**Implementation Notes:**
- WebSocket configuration is in WebSocketConfig
- STOMP endpoint at `/ws` with SockJS fallback
- Simple broker at `/topic` for broadcast messages
- Application prefix at `/app` for client messages
- Redis Pub/Sub migration path is documented for future scaling

---

## 5. AUTHENTICATION STRATEGY

### 5.1 Decision: Do Not Assume JWT Immediately

**Decision:** Document multiple migration strategies. Do not hardcode one approach.

**Option A: Maintain Laravel Authentication Compatibility**

**Description:**
Maintain compatibility with the existing Laravel authentication behavior during migration.

**Approach:**
- Use Laravel Sanctum tokens during migration
- Spring Boot validates Laravel tokens via shared database
- Gradually migrate to Spring Boot authentication
- Preserve existing React frontend and Flutter authentication

**Pros:**
- Minimal frontend changes
- Faster migration timeline
- Lower risk to existing users
- Maintains existing session behavior

**Cons:**
- Tightly coupled to Laravel during migration
- Additional complexity in Spring Boot
- Technical debt from dual authentication systems

**Option B: Adopt JWT After Evaluation**

**Description:**
Adopt JWT only after evaluating the impact on the React frontend and Flutter application.

**Approach:**
- Evaluate JWT impact on frontend authentication
- Implement JWT in Spring Boot
- Update React frontend to use JWT
- Update Flutter application to use JWT
- Decommission Laravel authentication

**Pros:**
- Stateless authentication
- Better for microservices architecture
- Standard industry practice
- Decoupled from Laravel

**Cons:**
- Requires frontend authentication changes
- Longer migration timeline
- Higher risk to existing users
- Token revocation complexity

**Rationale:**
- Authentication strategy has significant impact on frontend applications
- Both options have valid trade-offs depending on migration timeline
- Do not hardcode one approach without evaluation
- Decision should be based on:
  - Migration timeline constraints
  - Frontend team capacity
  - Security requirements
  - Long-term architecture goals

**Implementation Notes:**
- JWT dependencies are commented out in pom.xml
- JWT configuration is commented out in application.yml
- SecurityConfig includes JWT filter structure but is not activated
- Authentication strategy decision must be made before implementing security
- See MIGRATION_DECISIONS.md for detailed evaluation criteria

---

## 6. CONFIGURATION PHILOSOPHY

### 6.1 Decision: Only Include Genuinely Required Configuration Classes

**Decision:** Only include configuration classes that are genuinely required. Avoid unnecessary configuration classes that Spring Boot can auto-configure.

**Configuration Classes Included:**
- SecurityConfig (required for custom security rules)
- CacheConfig (required for Redis cache configuration)
- FlywayConfig (required for database migration)
- OpenApiConfig (required for API documentation)
- AsyncConfig (required for async task execution)
- WebSocketConfig (required for WebSocket configuration)
- AiEngineConfig (required for AI engine integration)

**Configuration Classes Intentionally Omitted:**
- JwtConfig (deferred until authentication strategy is finalized)
- OsrmConfig (managed in application.yml, accessed via @Value)
- NominatimConfig (managed in application.yml, accessed via @Value)

**Rationale:**
- Spring Boot auto-configuration handles most common cases
- Unnecessary configuration classes add complexity
- External service configuration can be managed in application.yml
- Configuration classes should only be added when:
  - Default Spring Boot behavior needs customization
  - External service integration requires specific beans
  - Cross-cutting concerns need centralized configuration

**Implementation Notes:**
- External service configuration (OSRM, Nominatim) is in application.yml
- Integration service layer accesses configuration via @Value or @ConfigurationProperties
- No separate config classes unless complex property binding is required
- Follow Spring Boot convention over configuration principle

---

## 7. ACCEPTED TRADE-OFFS

### 7.1 Dual Deployment Complexity

**Trade-off:** Running Laravel and Spring Boot in parallel during migration increases deployment complexity.

**Acceptance:** Accepted as necessary for incremental migration. The risk of a big-bang rewrite outweighs the complexity of dual deployment.

### 7.2 Deferred Authentication Decision

**Trade-off:** Authentication strategy is deferred, creating uncertainty in security implementation.

**Acceptance:** Accepted as necessary to evaluate impact on frontend applications. Authentication decision must be made before implementing security.

### 7.3 Limited Module Set

**Trade-off:** Starting with a limited module set may require refactoring if new domains emerge.

**Acceptance:** Accepted as necessary to prevent premature abstraction. New top-level modules can be added with strong architectural justification.

### 7.4 In-Memory WebSocket Broker

**Trade-off:** In-memory WebSocket broker does not support horizontal scaling.

**Acceptance:** Accepted as necessary for initial deployment. Redis Pub/Sub migration path is documented for future scaling.

### 7.5 Redis Not Day One

**Trade-off:** Redis is configured but not fully utilized from day one.

**Acceptance:** Accepted as necessary to avoid over-engineering. Redis usage will be added incrementally as needed.

---

## 8. DEFERRED ENHANCEMENTS

### 8.1 Redis Pub/Sub for WebSocket Scaling

**Deferred:** Redis Pub/Sub for WebSocket message distribution.

**Reason:** In-memory broker is sufficient for initial deployment. Redis Pub/Sub will be implemented when horizontal scaling is needed.

**Migration Path:** Replace `enableSimpleBroker()` with `enableStompBrokerRelay()` in WebSocketConfig.

### 8.2 Distributed Locking

**Deferred:** Distributed locking for cross-instance coordination.

**Reason:** Single-instance deployment does not require distributed locking. Will be implemented when horizontal scaling is needed.

### 8.3 Advanced Caching Strategies

**Deferred:** Advanced caching strategies (cache warming, cache invalidation, multi-level caching).

**Reason:** Basic caching is sufficient for initial deployment. Advanced strategies will be added based on performance requirements.

### 8.4 Job Scheduling Framework

**Deferred:** Job scheduling framework (Spring Batch, Quartz).

**Reason:** Simple async processing is sufficient for initial deployment. Job scheduling framework will be added when complex job requirements emerge.

### 8.5 Event-Driven Architecture

**Deferred:** Event-driven architecture with event sourcing.

**Reason:** Direct service calls are sufficient for initial deployment. Event-driven architecture will be considered if domain complexity increases.

---

## 9. TECHNOLOGIES APPROVED FOR FUTURE PHASES

### 9.1 Redis Pub/Sub

**Approved for:** WebSocket horizontal scaling.

**Trigger:** When horizontal scaling is needed for WebSocket support.

**Implementation:** Replace in-memory broker with Redis STOMP relay.

### 9.2 Distributed Locking

**Approved for:** Cross-instance coordination.

**Trigger:** When horizontal scaling is needed and race conditions emerge.

**Implementation:** Use Redis distributed locks or Spring Integration.

### 9.3 Job Scheduling Framework

**Approved for:** Complex job processing requirements.

**Trigger:** When simple async processing is insufficient.

**Options:** Spring Batch, Quartz, or Spring Cloud Task.

### 9.4 Event-Driven Architecture

**Approved for:** Complex domain interactions and decoupling.

**Trigger:** When domain complexity increases and direct service calls become problematic.

**Options:** Spring Cloud Stream, Kafka, or RabbitMQ.

### 9.5 API Gateway

**Approved for:** Microservices architecture.

**Trigger:** When monolithic architecture is split into microservices.

**Options:** Spring Cloud Gateway, Zuul, or Kong.

---

## 10. TECHNOLOGIES INTENTIONALLY POSTPONED

### 10.1 Additional Top-Level Modules

**Postponed:** routing, geocoding, backhaul, websocket, platform, job, event as top-level modules.

**Reason:** No strong architectural justification for additional modules. These can become subpackages within existing modules as the project grows.

**Future Consideration:** Add new top-level modules only with strong architectural justification and team consensus.

### 10.2 Complex Configuration Classes

**Postponed:** OsrmConfig, NominatimConfig, JwtConfig.

**Reason:** Spring Boot auto-configuration and application.yml are sufficient. Configuration classes add unnecessary complexity.

**Future Consideration:** Add configuration classes only when complex property binding is required.

### 10.3 JWT Authentication

**Postponed:** JWT implementation until authentication strategy is finalized.

**Reason:** Authentication strategy has significant impact on frontend applications. Must evaluate both options before implementation.

**Future Consideration:** Implement JWT only after evaluating impact on React frontend and Flutter application.

### 10.4 Advanced Monitoring

**Postponed:** Advanced monitoring (APM, distributed tracing).

**Reason:** Spring Boot Actuator provides sufficient monitoring for initial deployment. Advanced monitoring will be added based on operational requirements.

**Future Consideration:** Add APM (New Relic, Datadog) and distributed tracing (Zipkin, Jaeger) when operational complexity increases.

### 10.5 Service Mesh

**Postponed:** Service mesh (Istio, Linkerd).

**Reason:** Monolithic architecture does not require service mesh. Service mesh will be considered when migrating to microservices.

**Future Consideration:** Evaluate service mesh options when microservices architecture is adopted.

---

## APPENDICES

### Appendix A: Decision-Making Criteria

**Architectural decisions are based on:**
1. Migration timeline constraints
2. Risk tolerance and rollback strategy
3. Frontend team capacity
4. Long-term architecture goals
5. Operational complexity
6. Team expertise and learning curve

### Appendix B: Review Process

**Architectural decisions are reviewed:**
1. Before implementation begins
2. When new requirements emerge
3. When trade-offs need re-evaluation
4. Before major milestones
5. During retrospective meetings

### Appendix C: Documentation Updates

**This document is updated:**
1. When new architectural decisions are made
2. When existing decisions are modified
3. When trade-offs are re-evaluated
4. When technologies are approved or postponed
5. At major migration milestones

---

**Document Status:** Active  
**Last Reviewed:** July 8, 2026  
**Next Review:** Before authentication strategy implementation
