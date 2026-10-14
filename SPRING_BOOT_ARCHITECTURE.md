# Spring Boot Architecture Design
## EthioloadAI Migration

**Project:** EthioloadAI  
**Source:** Laravel 11.x (PHP)  
**Target:** Spring Boot 3 (Java 21)  
**Build Tool:** Maven  
**Database:** PostgreSQL  
**Migration Approach:** Incremental Migration  
**Document Version:** 2.0  
**Created:** July 8, 2026  
**Updated:** July 8, 2026

---

## TABLE OF CONTENTS

1. [Package Structure](#1-package-structure)
2. [Module Structure](#2-module-structure)
3. [Configuration Classes](#3-configuration-classes)
4. [Dependency List](#4-dependency-list)
5. [Maven pom.xml](#5-maven-pomxml)
6. [Application Profiles](#6-application-profiles)
7. [Logging Configuration](#7-logging-configuration)
8. [Exception Handling Structure](#8-exception-handling-structure)
9. [DTO Conventions](#9-dto-conventions)
10. [Repository Conventions](#10-repository-conventions)
11. [Service Conventions](#11-service-conventions)
12. [Controller Conventions](#12-controller-conventions)

---

## 1. PACKAGE STRUCTURE

### 1.1 Migration Approach

This is an **incremental migration**, not a rewrite. The Laravel backend remains the reference implementation until migration is complete. The following components are preserved:
- PostgreSQL database
- React frontend
- Flutter application
- FastAPI AI service
- API contracts (unless intentionally changed)

### 1.2 Root Package

```
com.ethioloadai
├── EthioloadAiApplication.java
└── config/
```

### 1.3 Complete Package Structure

```
com.ethioloadai
├── EthioloadAiApplication.java
│
├── config/
│   ├── SecurityConfig.java
│   ├── CacheConfig.java
│   ├── FlywayConfig.java
│   ├── OpenApiConfig.java
│   ├── AsyncConfig.java
│   ├── WebSocketConfig.java
│   └── AiEngineConfig.java
│
├── common/
│   ├── annotation/
│   │   ├── CurrentUser.java
│   │   ├── RequireRole.java
│   │   └── RateLimit.java
│   ├── constant/
│   │   ├── Role.java
│   │   ├── UserRole.java
│   │   ├── BookingStatus.java
│   │   ├── BidStatus.java
│   │   ├── TripStatus.java
│   │   ├── DocumentType.java
│   │   ├── DocumentStatus.java
│   │   ├── ServiceType.java
│   │   ├── PriceType.java
│   │   └── VehicleCategory.java
│   ├── exception/
│   │   ├── ApplicationException.java
│   │   ├── ResourceNotFoundException.java
│   │   ├── ValidationException.java
│   │   ├── AuthenticationException.java
│   │   ├── AuthorizationException.java
│   │   └── ExternalServiceException.java
│   ├── dto/
│   │   ├── ApiResponse.java
│   │   ├── PageResponse.java
│   │   ├── ErrorResponse.java
│   │   └── ValidationError.java
│   ├── enums/
│   │   └── (enum classes for constants)
│   ├── util/
│   │   ├── HaversineCalculator.java
│   │   └── DateUtil.java
│   └── mapper/
│       └── (MapStruct mappers)
│
├── security/
│   ├── jwt/
│   │   ├── JwtAuthenticationFilter.java
│   │   ├── JwtAuthenticationEntryPoint.java
│   │   ├── JwtTokenProvider.java
│   │   └── JwtUserDetailsService.java
│   ├── service/
│   │   └── CustomUserDetailsService.java
│   └── model/
│       └── UserDetailsImpl.java
│
├── auth/
│   ├── controller/
│   │   └── AuthController.java
│   ├── service/
│   │   └── AuthService.java
│   ├── dto/
│   │   ├── RegisterRequest.java
│   │   ├── LoginRequest.java
│   │   ├── LoginResponse.java
│   │   ├── ChangePasswordRequest.java
│   │   └── UpdateProfileRequest.java
│   └── mapper/
│       └── AuthMapper.java
│
├── user/
│   ├── controller/
│   │   └── UserController.java
│   ├── service/
│   │   └── UserService.java
│   ├── repository/
│   │   └── UserRepository.java
│   ├── entity/
│   │   └── User.java
│   ├── dto/
│   │   ├── UserResponse.java
│   │   ├── UserCreateRequest.java
│   │   ├── UserUpdateRequest.java
│   │   └── DriverStatsResponse.java
│   └── mapper/
│       └── UserMapper.java
│
├── driver/
│   ├── controller/
│   │   └── DriverController.java
│   ├── service/
│   │   └── DriverService.java
│   ├── repository/
│   │   └── DriverRepository.java
│   ├── entity/
│   │   └── Driver.java
│   ├── dto/
│   │   ├── DriverResponse.java
│   │   ├── DriverCreateRequest.java
│   │   └── DriverUpdateRequest.java
│   └── mapper/
│       └── DriverMapper.java
│
├── vehicle/
│   ├── controller/
│   │   └── VehicleController.java
│   ├── service/
│   │   └── VehicleService.java
│   ├── repository/
│   │   └── VehicleRepository.java
│   ├── entity/
│   │   └── Vehicle.java
│   ├── dto/
│   │   ├── VehicleResponse.java
│   │   ├── VehicleCreateRequest.java
│   │   ├── VehicleUpdateRequest.java
│   │   ├── VehicleLocationRequest.java
│   │   └── NearbyVehicleResponse.java
│   └── mapper/
│       └── VehicleMapper.java
│
├── cargo/
│   ├── controller/
│   │   └── CargoRequestController.java
│   ├── service/
│   │   └── CargoRequestService.java
│   ├── repository/
│   │   └── CargoRequestRepository.java
│   ├── entity/
│   │   └── CargoRequest.java
│   ├── dto/
│   │   ├── CargoRequestResponse.java
│   │   ├── CargoCreateRequest.java
│   │   ├── CargoUpdateRequest.java
│   │   ├── IntercityCargoRequest.java
│   │   ├── IntracityCargoRequest.java
│   │   └── NearbyDriverResponse.java
│   └── mapper/
│       └── CargoRequestMapper.java
│
├── bid/
│   ├── controller/
│   │   └── BidController.java
│   ├── service/
│   │   └── BidService.java
│   ├── repository/
│   │   └── BidRepository.java
│   ├── entity/
│   │   └── Bid.java
│   ├── dto/
│   │   ├── BidResponse.java
│   │   ├── BidCreateRequest.java
│   │   ├── BidUpdateRequest.java
│   │   ├── CounterOfferRequest.java
│   │   └── BidRankingResponse.java
│   └── mapper/
│       └── BidMapper.java
│
├── booking/
│   ├── controller/
│   │   └── BookingController.java
│   ├── service/
│   │   └── BookingService.java
│   ├── repository/
│   │   └── BookingRepository.java
│   ├── entity/
│   │   └── Booking.java
│   ├── dto/
│   │   ├── BookingResponse.java
│   │   ├── BookingCreateRequest.java
│   │   ├── BookingUpdateRequest.java
│   │   └── BookingCancelResponse.java
│   └── mapper/
│       └── BookingMapper.java
│
├── trip/
│   ├── controller/
│   │   └── TripController.java
│   ├── service/
│   │   └── TripService.java
│   ├── repository/
│   │   └── TripRepository.java
│   ├── entity/
│   │   └── Trip.java
│   ├── dto/
│   │   ├── TripResponse.java
│   │   ├── TripCreateRequest.java
│   │   ├── TripStatusUpdateRequest.java
│   │   ├── TripLocationRequest.java
│   │   └── TripLocationResponse.java
│   └── mapper/
│       └── TripMapper.java
│
├── payment/
│   ├── controller/
│   │   └── PaymentController.java
│   ├── service/
│   │   └── PaymentService.java
│   ├── repository/
│   │   └── PaymentRepository.java
│   ├── entity/
│   │   └── Payment.java
│   ├── dto/
│   │   ├── PaymentResponse.java
│   │   ├── PaymentCreateRequest.java
│   │   └── PaymentProcessRequest.java
│   └── mapper/
│       └── PaymentMapper.java
│
├── admin/
│   ├── controller/
│   │   ├── AdminController.java
│   │   ├── AdminUserController.java
│   │   ├── AdminStatsController.java
│   │   ├── AdminAnalyticsController.java
│   │   └── AdminSettingsController.java
│   ├── service/
│   │   ├── AdminUserService.java
│   │   ├── AdminStatsService.java
│   │   ├── AdminAnalyticsService.java
│   │   └── AdminSettingsService.java
│   ├── dto/
│   │   ├── AdminStatsResponse.java
│   │   ├── AdminUserResponse.java
│   │   ├── AdminAnalyticsResponse.java
│   │   └── AdminPricingUpdateRequest.java
│   └── mapper/
│       └── AdminMapper.java
│
├── ai/
│   ├── service/
│   │   ├── AiEngineService.java
│   │   └── PricePredictionService.java
│   ├── dto/
│   │   ├── TruckRecommendationRequest.java
│   │   ├── TruckRecommendationResponse.java
│   │   ├── PricePredictionRequest.java
│   │   ├── PricePredictionResponse.java
│   │   ├── BackhaulOpportunityRequest.java
│   │   └── BackhaulOpportunityResponse.java
│   └── client/
│       └── AiEngineClient.java
│
├── notification/
│   ├── controller/
│   │   └── NotificationController.java
│   ├── service/
│   │   └── NotificationService.java
│   ├── repository/
│   │   └── NotificationRepository.java
│   ├── entity/
│   │   └── Notification.java
│   ├── dto/
│   │   ├── NotificationResponse.java
│   │   └── NotificationMarkReadRequest.java
│   └── mapper/
│       └── NotificationMapper.java
│
├── audit/
│   ├── service/
│   │   └── AuditService.java
│   ├── repository/
│   │   └── AuditLogRepository.java
│   ├── entity/
│   │   └── AuditLog.java
│   └── dto/
│       └── AuditLogResponse.java
│
├── integration/
│   ├── service/
│   │   ├── RoutingService.java
│   │   └── GeocodingService.java
│   ├── dto/
│   │   ├── RouteRequest.java
│   │   ├── RouteResponse.java
│   │   ├── PlaceSearchRequest.java
│   │   ├── PlaceSearchResponse.java
│   │   ├── ReverseGeocodeRequest.java
│   │   └── ReverseGeocodeResponse.java
│   └── client/
│       ├── OsrmClient.java
│       └── NominatimClient.java
│
└── websocket/
    ├── config/
    │   └── WebSocketConfig.java
    └── handler/
        └── TripLocationWebSocketHandler.java
```

### 1.4 Module Organization Notes

- **Domain-first structure**: Each module (auth, user, driver, vehicle, cargo, bid, booking, trip, payment, admin, ai, notification, audit, integration) contains its own controllers, services, repositories, entities, DTOs, and mappers
- **Cross-cutting concerns**: common, security, config provide shared functionality
- **Deferred modules**: routing, geocoding, backhaul, platform, job, event are intentionally omitted as top-level modules. These can become subpackages within integration or other modules as the project grows
- **No premature abstraction**: Start with the approved modules only. Add new top-level modules only with strong architectural justification

---

## 2. MODULE STRUCTURE

### 2.1 Monolithic Module Structure

The application follows a monolithic architecture with clear domain separation:

```
ethioloadai-backend/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/ethioloadai/
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-dev.yml
│   │       ├── application-prod.yml
│   │       ├── application-test.yml
│   │       ├── logback-spring.xml
│   │       └── db/migration/
│   │           └── (Flyway migrations)
│   └── test/
│       ├── java/
│       │   └── com/ethioloadai/
│       │       ├── (unit tests)
│       │       └── (integration tests)
│       └── resources/
│           ├── application-test.yml
│           └── (test data)
└── pom.xml
```

### 2.2 Domain Modules

Each domain module follows the same structure:

```
domain/
├── controller/    # REST endpoints
├── service/       # Business logic
├── repository/    # Data access
├── entity/        # JPA entities
├── dto/           # Data transfer objects
└── mapper/        # MapStruct mappers
```

### 2.3 Cross-Cutting Modules

```
common/            # Shared utilities and constants
security/          # Authentication and authorization
config/            # Spring configuration
```

---

## 3. CONFIGURATION CLASSES

### 3.1 Configuration Philosophy

Only include configuration classes that are genuinely required. Avoid unnecessary configuration classes that Spring Boot can auto-configure. Configuration classes should be added only when:
- Default Spring Boot behavior needs customization
- External service integration requires specific beans
- Cross-cutting concerns need centralized configuration

### 3.2 SecurityConfig

```java
package com.ethioloadai.config;

import com.ethioloadai.security.jwt.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    
    private final JwtAuthenticationFilter jwtAuthFilter;
    private final AuthenticationProvider authenticationProvider;
    
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**", "/api-docs/**", "/swagger-ui/**").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .authenticationProvider(authenticationProvider)
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }
    
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        // CORS configuration
    }
}
```

**Note:** JWT configuration properties are managed in application.yml. No separate JwtConfig class is needed unless custom property binding is required.

### 3.3 CacheConfig

```java
package com.ethioloadai.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfig {
    
    @Bean
    public RedisCacheConfiguration cacheConfiguration() {
        return RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofHours(1))
            .disableCachingNullValues()
            .serializeValuesWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new GenericJackson2JsonRedisSerializer()));
    }
}
```

**Redis Planned Uses:**
- **Caching**: API responses, route calculations, geocoding results
- **Rate Limiting**: API endpoint throttling
- **Queues**: Asynchronous job processing (backhaul recommendations)
- **Pub/Sub**: WebSocket message distribution for horizontal scaling
- **Distributed Locking**: Prevent duplicate operations across instances

### 3.4 FlywayConfig

```java
package com.ethioloadai.config;

import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class FlywayConfig {
    
    @Bean
    public Flyway flyway(DataSource dataSource) {
        return Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .baselineOnMigrate(true)
            .load();
    }
}
```

### 3.5 OpenApiConfig

```java
package com.ethioloadai.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    
    @Bean
    public OpenAPI ethioloadAiOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("EthioloadAI API")
                .description("Logistics Platform API")
                .version("1.0"))
            .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
            .components(new io.swagger.v3.oas.models.Components()
                .addSecuritySchemes("bearerAuth",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")));
    }
}
```

### 3.6 AsyncConfig

```java
package com.ethioloadai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {
    
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("async-");
        executor.initialize();
        return executor;
    }
}
```

### 3.7 WebSocketConfig

```java
package com.ethioloadai.websocket.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }
    
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
            .setAllowedOriginPatterns("*")
            .withSockJS();
    }
}
```

**WebSocket Use Cases:**
- Live driver tracking
- Shipment tracking
- Booking updates
- Bid notifications
- Chat/messaging
- Admin live dashboard

**Scaling Strategy:**
- Initial deployment uses in-memory message broker (simple broker)
- For horizontal scaling, migrate to Redis Pub/Sub by replacing `enableSimpleBroker()` with `enableStompBrokerRelay()`
- Redis Pub/Sub enables WebSocket message distribution across multiple instances

### 3.8 AiEngineConfig

```java
package com.ethioloadai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "ai-engine")
public class AiEngineConfig {
    
    private String url;
    private Integer timeout;
    private Boolean enabled;
}
```

**Note:** OsrmConfig and NominatimConfig are intentionally omitted. External service configuration is managed in application.yml and accessed via @Value or @ConfigurationProperties in the integration service layer. No separate config classes are needed unless complex property binding is required.

---

## 4. DEPENDENCY LIST

### 4.1 Core Spring Boot Dependencies

```xml
<!-- Spring Boot Starter -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>

<!-- Spring Boot Data JPA -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>

<!-- Spring Boot Security -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- Spring Boot Validation -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>

<!-- Spring Boot Cache -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-cache</artifactId>
</dependency>

<!-- Spring Boot WebSocket -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-websocket</artifactId>
</dependency>

<!-- Spring Boot Actuator -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

**Note:** Spring Boot starter dependencies are included. Additional dependencies for JWT, Redis, etc. are listed in subsequent sections.

### 4.2 Database Dependencies

```xml
<!-- PostgreSQL Driver -->
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>

<!-- Flyway -->
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>

<!-- Flyway PostgreSQL -->
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-database-postgresql</artifactId>
</dependency>

<!-- HikariCP (included in Spring Boot) -->
<dependency>
    <groupId>com.zaxxer</groupId>
    <artifactId>HikariCP</artifactId>
</dependency>
```

### 4.3 JWT & Security Dependencies

```xml
<!-- JWT (jjwt) - Deferred until authentication strategy is finalized -->
<!-- 
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.3</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.3</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.3</version>
    <scope>runtime</scope>
</dependency>
-->
```

**Authentication Strategy Note:**
JWT dependencies are commented out pending authentication strategy decision. See MIGRATION_DECISIONS.md for authentication migration options:
- **Option A**: Maintain compatibility with existing Laravel authentication during migration
- **Option B**: Adopt JWT after evaluating impact on React frontend and Flutter application

Do not hardcode one approach. Evaluate both options before implementation.

### 4.4 Mapping & Utilities

```xml
<!-- MapStruct -->
<dependency>
    <groupId>org.mapstruct</groupId>
    <artifactId>mapstruct</artifactId>
    <version>1.5.5.Final</version>
</dependency>
<dependency>
    <groupId>org.mapstruct</groupId>
    <artifactId>mapstruct-processor</artifactId>
    <version>1.5.5.Final</version>
    <scope>provided</scope>
</dependency>

<!-- Lombok -->
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <optional>true</optional>
</dependency>
```

### 4.5 API Documentation

```xml
<!-- SpringDoc OpenAPI -->
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.3.0</version>
</dependency>
```

### 4.6 Caching

```xml
<!-- Spring Data Redis -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

### 4.7 Monitoring & Metrics

```xml
<!-- Micrometer Tracing -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-brave</artifactId>
</dependency>

<!-- Micrometer Observation -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-observation</artifactId>
</dependency>

<!-- Zipkin Reporter -->
<dependency>
    <groupId>io.zipkin.reporter2</groupId>
    <artifactId>zipkin-reporter-brave</artifactId>
</dependency>
```

### 4.8 HTTP Client

```xml
<!-- Spring Boot WebClient (included in web) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
```

### 4.9 Testing

```xml
<!-- Spring Boot Test -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>

<!-- Spring Security Test -->
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-test</artifactId>
    <scope>test</scope>
</dependency>

<!-- H2 Database for Testing -->
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>test</scope>
</dependency>
```

---

## 5. MAVEN POM.XML

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.0</version>
        <relativePath/>
    </parent>
    
    <groupId>com.ethioloadai</groupId>
    <artifactId>ethioloadai-backend</artifactId>
    <version>1.0.0</version>
    <name>EthioloadAI Backend</name>
    <description>Logistics Platform Backend - Spring Boot Migration</description>
    
    <properties>
        <java.version>21</java.version>
        <mapstruct.version>1.5.5.Final</mapstruct.version>
        <jjwt.version>0.12.3</jjwt.version>
        <springdoc.version>2.3.0</springdoc.version>
        <flyway.version>10.0.0</flyway.version>
    </properties>
    
    <dependencies>
        <!-- Spring Boot Starters -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
        
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-cache</artifactId>
        </dependency>
        
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-websocket</artifactId>
        </dependency>
        
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>
        
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webflux</artifactId>
        </dependency>
        
        <!-- Database -->
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>
        
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-core</artifactId>
            <version>${flyway.version}</version>
        </dependency>
        
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-database-postgresql</artifactId>
            <version>${flyway.version}</version>
        </dependency>
        
        <!-- JWT -->
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-api</artifactId>
            <version>${jjwt.version}</version>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-impl</artifactId>
            <version>${jjwt.version}</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-jackson</artifactId>
            <version>${jjwt.version}</version>
            <scope>runtime</scope>
        </dependency>
        
        <!-- MapStruct -->
        <dependency>
            <groupId>org.mapstruct</groupId>
            <artifactId>mapstruct</artifactId>
            <version>${mapstruct.version}</version>
        </dependency>
        <dependency>
            <groupId>org.mapstruct</groupId>
            <artifactId>mapstruct-processor</artifactId>
            <version>${mapstruct.version}</version>
            <scope>provided</scope>
        </dependency>
        
        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        
        <!-- OpenAPI -->
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
            <version>${springdoc.version}</version>
        </dependency>
        
        <!-- Monitoring -->
        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-tracing-bridge-brave</artifactId>
        </dependency>
        
        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-observation</artifactId>
        </dependency>
        
        <dependency>
            <groupId>io.zipkin.reporter2</groupId>
            <artifactId>zipkin-reporter-brave</artifactId>
        </dependency>
        
        <!-- Testing -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-test</artifactId>
            <scope>test</scope>
        </dependency>
        
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
    
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
            
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.11.0</version>
                <configuration>
                    <source>21</source>
                    <target>21</target>
                    <annotationProcessorPaths>
                        <path>
                            <groupId>org.mapstruct</groupId>
                            <artifactId>mapstruct-processor</artifactId>
                            <version>${mapstruct.version}</version>
                        </path>
                        <path>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                            <version>${lombok.version}</version>
                        </path>
                        <path>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok-mapstruct-binding</artifactId>
                            <version>0.2.0</version>
                        </path>
                    </annotationProcessorPaths>
                </configuration>
            </plugin>
            
            <plugin>
                <groupId>org.flywaydb</groupId>
                <artifactId>flyway-maven-plugin</artifactId>
                <version>${flyway.version}</version>
                <configuration>
                    <url>${DATABASE_URL}</url>
                    <user>${DATABASE_USERNAME}</user>
                    <password>${DATABASE_PASSWORD}</password>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

---

## 6. APPLICATION PROFILES

### 6.1 application.yml (Base)

```yaml
spring:
  application:
    name: ethioloadai-backend
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}
  
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true
    open-in-view: false
  
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 10MB
  
  cache:
    type: redis
    redis:
      time-to-live: 3600000
  
  data:
    redis:
      repositories:
        enabled: false
  
  task:
    execution:
      pool:
        core-size: 5
        max-size: 10
        queue-capacity: 100

server:
  port: ${SERVER_PORT:8080}
  servlet:
    context-path: /api

# Authentication configuration - deferred until strategy is finalized
# See MIGRATION_DECISIONS.md for authentication options
# jwt:
#   secret: ${JWT_SECRET:default-secret-key-change-in-production}
#   expiration: ${JWT_EXPIRATION:86400000}
#   refresh-expiration: ${JWT_REFRESH_EXPIRATION:604800000}
#   header: Authorization
#   prefix: Bearer

ai-engine:
  url: ${AI_ENGINE_URL:http://localhost:8000}
  timeout: 5000
  enabled: ${AI_ENGINE_ENABLED:true}

# External service configuration - managed in integration service layer
osrm:
  base-url: http://router.project-osrm.org
  timeout: 5000
  route-cache-ttl: 3600
  nearest-cache-ttl: 1800

nominatim:
  base-url: https://nominatim.openstreetmap.org
  user-agent: EthioLoadAI/1.0
  timeout: 5000
  country-code: et
  search-cache-ttl: 86400
  reverse-cache-ttl: 21600

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: when-authorized
  metrics:
    tags:
      application: ethioloadai-backend

springdoc:
  api-docs:
    path: /api-docs
  swagger-ui:
    path: /swagger-ui.html
    enabled: true
```

### 6.2 application-dev.yml

```yaml
spring:
  datasource:
    url: ${DATABASE_URL:jdbc:postgresql://localhost:5432/ethioloadai_dev}
    username: ${DATABASE_USERNAME:ethioloadai}
    password: ${DATABASE_PASSWORD:password}
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 10
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
  
  redis:
    host: ${REDIS_HOST:localhost}
    port: ${REDIS_PORT:6379}
    password: ${REDIS_PASSWORD:}
  
  jpa:
    show-sql: true
    properties:
      hibernate:
        format_sql: true

logging:
  level:
    com.ethioloadai: DEBUG
    org.springframework.security: DEBUG
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE
```

### 6.3 application-prod.yml

```yaml
spring:
  datasource:
    url: ${DATABASE_URL}
    username: ${DATABASE_USERNAME}
    password: ${DATABASE_PASSWORD}
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 20
      minimum-idle: 10
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
  
  redis:
    host: ${REDIS_HOST}
    port: ${REDIS_PORT}
    password: ${REDIS_PASSWORD}
    ssl: true
  
  jpa:
    show-sql: false

logging:
  level:
    com.ethioloadai: INFO
    org.springframework.security: WARN
    org.hibernate.SQL: WARN
  file:
    name: /var/log/ethioloadai/application.log
  logback:
    rollingpolicy:
      max-file-size: 10MB
      max-history: 30

management:
  endpoint:
    health:
      show-details: never
```

### 6.4 application-test.yml

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb
    driver-class-name: org.h2.Driver
    username: sa
    password:
  
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: true
  
  redis:
    host: localhost
    port: 6379
    database: 1

# Authentication configuration - deferred
# jwt:
#   secret: test-secret-key
#   expiration: 3600000

ai-engine:
  enabled: false

logging:
  level:
    com.ethioloadai: DEBUG
```

---

## 7. LOGGING CONFIGURATION

### 7.1 logback-spring.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <springProfile name="dev">
        <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
            <encoder>
                <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
            </encoder>
        </appender>
        
        <root level="INFO">
            <appender-ref ref="CONSOLE"/>
        </root>
        
        <logger name="com.ethioloadai" level="DEBUG"/>
    </springProfile>
    
    <springProfile name="prod">
        <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
            <file>/var/log/ethioloadai/application.log</file>
            <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
                <fileNamePattern>/var/log/ethioloadai/application-%d{yyyy-MM-dd}.%i.log</fileNamePattern>
                <timeBasedFileNamingAndTriggeringPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedFNATP">
                    <maxFileSize>10MB</maxFileSize>
                </timeBasedFileNamingAndTriggeringPolicy>
                <maxHistory>30</maxHistory>
            </rollingPolicy>
            <encoder>
                <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
            </encoder>
        </appender>
        
        <appender name="ASYNC_FILE" class="ch.qos.logback.classic.AsyncAppender">
            <appender-ref ref="FILE"/>
            <queueSize>512</queueSize>
        </appender>
        
        <root level="INFO">
            <appender-ref ref="ASYNC_FILE"/>
        </root>
        
        <logger name="com.ethioloadai" level="INFO"/>
        <logger name="org.springframework.security" level="WARN"/>
        <logger name="org.hibernate.SQL" level="WARN"/>
    </springProfile>
    
    <springProfile name="test">
        <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
            <encoder>
                <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
            </encoder>
        </appender>
        
        <root level="INFO">
            <appender-ref ref="CONSOLE"/>
        </root>
        
        <logger name="com.ethioloadai" level="DEBUG"/>
    </springProfile>
</configuration>
```

---

## 8. EXCEPTION HANDLING STRUCTURE

### 8.1 Exception Hierarchy

```
ApplicationException (RuntimeException)
├── ResourceNotFoundException
├── ValidationException
├── AuthenticationException
├── AuthorizationException
├── ExternalServiceException
└── BusinessException
    ├── BidExpiredException
    ├── CargoNotAvailableException
    ├── VehicleNotAvailableException
    └── DocumentVerificationException
```

### 8.2 Global Exception Handler

```java
package com.ethioloadai.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<ErrorResponse> handleApplicationException(ApplicationException ex) {
        log.error("Application exception: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.BAD_REQUEST.value())
            .error("Bad Request")
            .message(ex.getMessage())
            .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
    
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(ResourceNotFoundException ex) {
        log.error("Resource not found: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.NOT_FOUND.value())
            .error("Not Found")
            .message(ex.getMessage())
            .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }
    
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(ValidationException ex) {
        log.error("Validation exception: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.UNPROCESSABLE_ENTITY.value())
            .error("Validation Error")
            .message(ex.getMessage())
            .build();
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(error);
    }
    
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(AuthenticationException ex) {
        log.error("Authentication exception: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.UNAUTHORIZED.value())
            .error("Unauthorized")
            .message(ex.getMessage())
            .build();
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }
    
    @ExceptionHandler(AuthorizationException.class)
    public ResponseEntity<ErrorResponse> handleAuthorizationException(AuthorizationException ex) {
        log.error("Authorization exception: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.FORBIDDEN.value())
            .error("Forbidden")
            .message(ex.getMessage())
            .build();
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        
        ErrorResponse error = ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.BAD_REQUEST.value())
            .error("Validation Error")
            .message("Validation failed")
            .errors(errors)
            .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
    
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentialsException(BadCredentialsException ex) {
        log.error("Bad credentials: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.UNAUTHORIZED.value())
            .error("Unauthorized")
            .message("Invalid credentials")
            .build();
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }
    
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException ex) {
        log.error("Access denied: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.FORBIDDEN.value())
            .error("Forbidden")
            .message("Access denied")
            .build();
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        ErrorResponse error = ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .error("Internal Server Error")
            .message("An unexpected error occurred")
            .build();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
```

### 8.3 Exception Classes

```java
package com.ethioloadai.common.exception;

public class ApplicationException extends RuntimeException {
    public ApplicationException(String message) {
        super(message);
    }
    
    public ApplicationException(String message, Throwable cause) {
        super(message, cause);
    }
}

public class ResourceNotFoundException extends ApplicationException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}

public class ValidationException extends ApplicationException {
    public ValidationException(String message) {
        super(message);
    }
}

public class AuthenticationException extends ApplicationException {
    public AuthenticationException(String message) {
        super(message);
    }
}

public class AuthorizationException extends ApplicationException {
    public AuthorizationException(String message) {
        super(message);
    }
}

public class ExternalServiceException extends ApplicationException {
    public ExternalServiceException(String message) {
        super(message);
    }
    
    public ExternalServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

---

## 9. DTO CONVENTIONS

### 9.1 DTO Naming Conventions

- **Request DTOs:** `{Entity}CreateRequest`, `{Entity}UpdateRequest`, `{Entity}ActionRequest`
- **Response DTOs:** `{Entity}Response`
- **Query DTOs:** `{Entity}QueryRequest`, `{Entity}FilterRequest`
- **Nested DTOs:** Use descriptive names, e.g., `DriverStatsResponse`, `NearbyVehicleResponse`

### 9.2 DTO Structure

```java
package com.ethioloadai.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserCreateRequest {
    
    @NotBlank(message = "Full name is required")
    @Size(min = 2, max = 100, message = "Full name must be between 2 and 100 characters")
    private String fullName;
    
    @NotBlank(message = "Phone is required")
    @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "Invalid phone number")
    private String phone;
    
    @Email(message = "Invalid email format")
    private String email;
    
    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
    private String password;
    
    @NotBlank(message = "Role is required")
    private String role;
}
```

### 9.3 Response DTO Structure

```java
package com.ethioloadai.user.dto;

import com.ethioloadai.common.constant.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    
    private Long id;
    private String fullName;
    private String phone;
    private String email;
    private UserRole role;
    private Boolean verificationStatus;
    private Boolean isActive;
    private String location;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

### 9.4 Common Response Wrapper

```java
package com.ethioloadai.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {
    
    private LocalDateTime timestamp;
    private Integer status;
    private String message;
    private T data;
    
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
            .timestamp(LocalDateTime.now())
            .status(200)
            .message("Success")
            .data(data)
            .build();
    }
    
    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
            .timestamp(LocalDateTime.now())
            .status(200)
            .message(message)
            .data(data)
            .build();
    }
    
    public static <T> ApiResponse<T> created(T data) {
        return ApiResponse.<T>builder()
            .timestamp(LocalDateTime.now())
            .status(201)
            .message("Created successfully")
            .data(data)
            .build();
    }
}
```

### 9.5 Error Response Structure

```java
package com.ethioloadai.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {
    
    private LocalDateTime timestamp;
    private Integer status;
    private String error;
    private String message;
    private Map<String, String> errors;
    private String path;
}
```

### 9.6 Page Response Structure

```java
package com.ethioloadai.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResponse<T> {
    
    private List<T> data;
    private int currentPage;
    private int totalPages;
    private long totalItems;
    private int itemsPerPage;
    
    public static <T> PageResponse<T> of(List<T> data, int currentPage, int totalPages, 
                                           long totalItems, int itemsPerPage) {
        return PageResponse.<T>builder()
            .data(data)
            .currentPage(currentPage)
            .totalPages(totalPages)
            .totalItems(totalItems)
            .itemsPerPage(itemsPerPage)
            .build();
    }
}
```

---

## 10. REPOSITORY CONVENTIONS

### 10.1 Repository Interface

```java
package com.ethioloadai.user.repository;

import com.ethioloadai.user.entity.User;
import com.ethioloadai.common.constant.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    Optional<User> findByPhone(String phone);
    
    Optional<User> findByEmail(String email);
    
    boolean existsByPhone(String phone);
    
    boolean existsByEmail(String email);
    
    List<User> findByRole(UserRole role);
    
    @Query("SELECT u FROM User u WHERE u.fleetOwnerId = :fleetOwnerId")
    List<User> findDriversByFleetOwner(@Param("fleetOwnerId") Long fleetOwnerId);
    
    @Query("SELECT u FROM User u WHERE u.verificationStatus = true AND u.isActive = true")
    List<User> findVerifiedActiveUsers();
}
```

### 10.2 Custom Repository Methods

- **Naming Convention:** `findBy{Field}`, `existsBy{Field}`, `countBy{Field}`
- **Complex Queries:** Use `@Query` annotation with JPQL or native SQL
- **Pagination:** Use `Pageable` parameter
- **Projections:** Use interface-based or class-based projections for partial data

### 10.3 Repository Best Practices

1. **Keep repositories simple** - Business logic in services
2. **Use Optional** for single result queries that may return null
3. **Add @Index** annotations on entity fields for query optimization
4. **Use @Transactional** in service layer, not repository layer
5. **Custom queries** should be in repository interface with @Query

---

## 11. SERVICE CONVENTIONS

### 11.1 Service Interface

```java
package com.ethioloadai.user.service;

import com.ethioloadai.user.dto.UserCreateRequest;
import com.ethioloadai.user.dto.UserResponse;
import com.ethioloadai.user.dto.UserUpdateRequest;
import com.ethioloadai.common.constant.UserRole;

import java.util.List;

public interface UserService {
    
    UserResponse createUser(UserCreateRequest request);
    
    UserResponse getUserById(Long id);
    
    UserResponse getCurrentUser();
    
    UserResponse updateUser(Long id, UserUpdateRequest request);
    
    void deleteUser(Long id);
    
    List<UserResponse> getAllUsers();
    
    List<UserResponse> getUsersByRole(UserRole role);
    
    UserResponse updateProfile(UserUpdateRequest request);
    
    void changePassword(String oldPassword, String newPassword);
}
```

### 11.2 Service Implementation

```java
package com.ethioloadai.user.service;

import com.ethioloadai.common.exception.ResourceNotFoundException;
import com.ethioloadai.common.exception.ValidationException;
import com.ethioloadai.user.dto.UserCreateRequest;
import com.ethioloadai.user.dto.UserResponse;
import com.ethioloadai.user.dto.UserUpdateRequest;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.user.mapper.UserMapper;
import com.ethioloadai.user.repository.UserRepository;
import com.ethioloadai.common.constant.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UserServiceImpl implements UserService {
    
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    
    @Override
    public UserResponse createUser(UserCreateRequest request) {
        log.info("Creating user with phone: {}", request.getPhone());
        
        if (userRepository.existsByPhone(request.getPhone())) {
            throw new ValidationException("Phone number already registered");
        }
        
        if (request.getEmail() != null && userRepository.existsByEmail(request.getEmail())) {
            throw new ValidationException("Email already registered");
        }
        
        User user = userMapper.toEntity(request);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        
        // Set default values based on role
        if (request.getRole().equals(UserRole.DRIVER.name())) {
            user.setVerificationStatus(false);
            user.setIsActive(false);
        } else {
            user.setVerificationStatus(true);
            user.setIsActive(true);
        }
        
        User savedUser = userRepository.save(user);
        log.info("User created successfully with id: {}", savedUser.getId());
        
        return userMapper.toResponse(savedUser);
    }
    
    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        return userMapper.toResponse(user);
    }
    
    // ... other methods
}
```

### 11.3 Service Conventions

1. **Interface-Implementation Pattern** - Define interface, implement in *Impl class
2. **@Transactional** - Use on service methods, read-only for queries
3. **Logging** - Use SLF4J with @Slf4j annotation
4. **Exception Handling** - Throw custom exceptions, let global handler handle
5. **Validation** - Validate business logic, throw ValidationException
6. **Mapper Usage** - Use MapStruct for entity-DTO conversion
7. **Constructor Injection** - Use @RequiredArgsConstructor (Lombok)
8. **Method Naming** - Use descriptive names matching business intent

---

## 12. CONTROLLER CONVENTIONS

### 12.1 Controller Structure

```java
package com.ethioloadai.user.controller;

import com.ethioloadai.common.annotation.CurrentUser;
import com.ethioloadai.common.dto.ApiResponse;
import com.ethioloadai.common.dto.PageResponse;
import com.ethioloadai.user.dto.UserCreateRequest;
import com.ethioloadai.user.dto.UserResponse;
import com.ethioloadai.user.dto.UserUpdateRequest;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.user.service.UserService;
import com.ethioloadai.common.constant.UserRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class UserController {
    
    private final UserService userService;
    
    @GetMapping
    @Operation(summary = "Get all users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<UserResponse>>> getAllUsers() {
        log.info("Fetching all users");
        List<UserResponse> users = userService.getAllUsers();
        return ResponseEntity.ok(ApiResponse.success(users));
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "Get user by ID")
    @PreAuthorize("hasRole('ADMIN') or #id == @currentUser.id")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(@PathVariable Long id) {
        log.info("Fetching user with id: {}", id);
        UserResponse user = userService.getUserById(id);
        return ResponseEntity.ok(ApiResponse.success(user));
    }
    
    @PostMapping
    @Operation(summary = "Create new user")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> createUser(
            @Valid @RequestBody UserCreateRequest request) {
        log.info("Creating new user");
        UserResponse user = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(user));
    }
    
    @PutMapping("/{id}")
    @Operation(summary = "Update user")
    @PreAuthorize("hasRole('ADMIN') or #id == @currentUser.id")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UserUpdateRequest request) {
        log.info("Updating user with id: {}", id);
        UserResponse user = userService.updateUser(id, request);
        return ResponseEntity.ok(ApiResponse.success(user));
    }
    
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete user")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable Long id) {
        log.info("Deleting user with id: {}", id);
        userService.deleteUser(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .message("User deleted successfully")
            .build());
    }
}
```

### 12.2 Controller Conventions

1. **RESTful Endpoints** - Use proper HTTP methods (GET, POST, PUT, DELETE, PATCH)
2. **Request Mapping** - Use plural resource names (e.g., `/users`, not `/user`)
3. **Validation** - Use `@Valid` annotation with @RequestBody
4. **Authorization** - Use `@PreAuthorize` for role-based access
5. **OpenAPI Documentation** - Add @Operation annotations
6. **Security** - Add @SecurityRequirement for authenticated endpoints
7. **Response Wrapper** - Use ApiResponse for consistent responses
8. **Logging** - Log entry/exit for each method
9. **Exception Handling** - Let global handler manage exceptions
10. **Dependency Injection** - Use @RequiredArgsConstructor

### 12.3 URL Conventions

| Pattern | Example | Description |
|---------|---------|-------------|
| `/api/{resource}` | `/api/users` | List all resources |
| `/api/{resource}/{id}` | `/api/users/1` | Get single resource |
| `/api/{resource}` (POST) | `/api/users` | Create resource |
| `/api/{resource}/{id}` (PUT) | `/api/users/1` | Update resource |
| `/api/{resource}/{id}` (DELETE) | `/api/users/1` | Delete resource |
| `/api/{resource}/{id}/{action}` | `/api/users/1/activate` | Resource action |
| `/api/{parent}/{parentId}/{child}` | `/api/users/1/vehicles` | Nested resources |

### 12.4 HTTP Status Codes

| Status | Usage |
|--------|-------|
| 200 OK | Successful GET, PUT, DELETE |
| 201 Created | Successful POST |
| 204 No Content | Successful DELETE with no body |
| 400 Bad Request | Validation error |
| 401 Unauthorized | Authentication required |
| 403 Forbidden | Authorization failed |
| 404 Not Found | Resource not found |
| 422 Unprocessable Entity | Business logic validation |
| 500 Internal Server Error | Unexpected error |

---

**End of Spring Boot Architecture Design**
