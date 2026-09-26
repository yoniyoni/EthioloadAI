package com.ethioloadai.websocket.security;

import com.ethioloadai.booking.repository.BookingRepository;
import com.ethioloadai.trip.repository.TripRepository;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.vehicle.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Implementation of WebSocket authorization service.
 * 
 * Provides comprehensive authorization logic for WebSocket operations
 * based on user roles, trip relationships, and business rules.
 */
@Service
@RequiredArgsConstructor
public class WebSocketAuthorizationServiceImpl implements WebSocketAuthorizationService {
    private static final Logger log = LoggerFactory.getLogger(WebSocketAuthorizationServiceImpl.class);

    private final WebSocketSecurityConfig securityConfig;
    private final TripRepository tripRepository;
    private final BookingRepository bookingRepository;
    private final VehicleRepository vehicleRepository;

    // Topic pattern matching
    private static final Pattern TRIP_TOPIC_PATTERN = Pattern.compile("trip\\.([0-9]+)\\..*");
    private static final Pattern USER_TOPIC_PATTERN = Pattern.compile("user\\.([0-9]+)\\..*");
    private static final Pattern VEHICLE_TOPIC_PATTERN = Pattern.compile("vehicle\\.([0-9]+)\\..*");
    private static final Pattern FLEET_TOPIC_PATTERN = Pattern.compile("fleet\\.(\\w+).*");

    @Override
    public AuthorizationResult canSubscribeToTopic(User user, String topic, Object filter) {
        log.debug("Checking subscription authorization for user {} to topic {}", user.getId(), topic);

        // Check if user meets minimum role requirement
        if (!hasMinimumRole(user)) {
            return AuthorizationResult.denyRequireRole(
                "User does not meet minimum role requirement", 
                securityConfig.getSecurityPolicy().getMinimumRole()
            );
        }

        // Check role-based topic access
        if (!isTopicAllowedForRole(user.getRole().name(), topic)) {
            return AuthorizationResult.deny(
                String.format("Topic %s not allowed for role %s", topic, user.getRole().name()),
                "TOPIC_NOT_ALLOWED_FOR_ROLE"
            );
        }

        // Specific topic authorization checks
        if (TRIP_TOPIC_PATTERN.matcher(topic).matches()) {
            return canSubscribeToTripTopic(user, topic);
        } else if (USER_TOPIC_PATTERN.matcher(topic).matches()) {
            return canSubscribeToUserTopic(user, topic);
        } else if (VEHICLE_TOPIC_PATTERN.matcher(topic).matches()) {
            return canSubscribeToVehicleTopic(user, topic);
        } else if (FLEET_TOPIC_PATTERN.matcher(topic).matches()) {
            return canSubscribeToFleetTopic(user, topic);
        }

        // Default authorization for general topics
        return AuthorizationResult.allow("Topic access granted");
    }

    @Override
    public AuthorizationResult canSendLocationUpdate(User user, Long tripId, Long vehicleId) {
        log.debug("Checking location update authorization for user {} trip {} vehicle {}", 
                 user.getId(), tripId, vehicleId);

        // Only drivers and fleet owners can send location updates
        if (!hasLocationUpdateRole(user)) {
            return AuthorizationResult.deny(
                "Only drivers and fleet owners can send location updates",
                "INSUFFICIENT_ROLE_FOR_LOCATION_UPDATE"
            );
        }

        // Check trip access
        AuthorizationResult tripAccess = canAccessTrip(user, tripId);
        if (!tripAccess.allowed()) {
            return AuthorizationResult.deny(
                "Cannot send location update: " + tripAccess.reason(),
                "TRIP_ACCESS_DENIED"
            );
        }

        // Check vehicle access
        AuthorizationResult vehicleAccess = canAccessVehicle(user, vehicleId);
        if (!vehicleAccess.allowed()) {
            return AuthorizationResult.deny(
                "Cannot send location update: " + vehicleAccess.reason(),
                "VEHICLE_ACCESS_DENIED"
            );
        }

        return AuthorizationResult.allow("Location update authorized");
    }

    @Override
    public AuthorizationResult canAccessTrip(User user, Long tripId) {
        log.debug("Checking trip access for user {} to trip {}", user.getId(), tripId);

        // Admin can access all trips
        if (user.getRole() == User.Role.ADMIN) {
            return AuthorizationResult.allow("Admin access to trip");
        }

        // Check if user is directly involved in the trip
        try {
            if (user.getRole() == User.Role.FLEET_OWNER) {
                // Fleet owners can access trips that use their vehicles
                boolean hasAccess = tripRepository.findByIdWithBookingAndVehicle(tripId)
                    .map(t -> t.getBooking() != null
                              && t.getBooking().getVehicle() != null
                              && user.getId().equals(t.getBooking().getVehicle().getFleetOwnerId()))
                    .orElse(false);
                if (hasAccess) return AuthorizationResult.allow("Fleet owner has vehicles in trip");
            }

            if (user.getRole() == User.Role.DRIVER) {
                // Drivers can access trips they are assigned to
                boolean hasAccess = tripRepository.findByIdAndDriverId(tripId, user.getId()).isPresent();
                if (hasAccess) return AuthorizationResult.allow("User is assigned driver for trip");
            }

            if (user.getRole() == User.Role.SHIPPER) {
                // Shippers can access trips for their cargo requests
                boolean hasAccess = tripRepository.findByIdAndShipperId(tripId, user.getId()).isPresent();
                if (hasAccess) return AuthorizationResult.allow("User is shipper for trip");
            }

            return AuthorizationResult.deny("User has no relationship to this trip", "NO_TRIP_RELATIONSHIP");

        } catch (Exception e) {
            log.error("Error checking trip access for user {} trip {}: {}", user.getId(), tripId, e.getMessage(), e);
            return AuthorizationResult.deny("Error checking trip access", "AUTHORIZATION_ERROR");
        }
    }

    @Override
    public AuthorizationResult canAccessVehicle(User user, Long vehicleId) {
        log.debug("Checking vehicle access for user {} to vehicle {}", user.getId(), vehicleId);

        // Admin can access all vehicles
        if (user.getRole() == User.Role.ADMIN) {
            return AuthorizationResult.allow("Admin access to vehicle");
        }

        try {
            if (user.getRole() == User.Role.FLEET_OWNER) {
                boolean ownsVehicle = vehicleRepository.findByFleetOwnerId(user.getId())
                    .stream().anyMatch(v -> v.getId() != null && v.getId().equals(vehicleId));
                if (ownsVehicle) return AuthorizationResult.allow("Fleet owner access to own vehicle");
            }

            if (user.getRole() == User.Role.DRIVER) {
                boolean isAssigned = vehicleRepository.findByUserId(user.getId())
                    .stream().anyMatch(v -> v.getId() != null && v.getId().equals(vehicleId));
                if (isAssigned) return AuthorizationResult.allow("Driver access to assigned vehicle");
            }

            return AuthorizationResult.deny("User has no access to this vehicle", "NO_VEHICLE_ACCESS");

        } catch (Exception e) {
            log.error("Error checking vehicle access for user {} vehicle {}: {}", user.getId(), vehicleId, e.getMessage(), e);
            return AuthorizationResult.deny("Error checking vehicle access", "AUTHORIZATION_ERROR");
        }
    }

    @Override
    public AuthorizationResult canAccessFleet(User user, Long fleetOwnerId) {
        log.debug("Checking fleet access for user {} to fleet {}", user.getId(), fleetOwnerId);

        // Admin can access all fleets
        if (user.getRole() == User.Role.ADMIN) {
            return AuthorizationResult.allow("Admin access to fleet");
        }

        // Fleet owners can access their own fleet
        if (user.getRole() == User.Role.FLEET_OWNER) {
            if (fleetOwnerId == null || fleetOwnerId.equals(user.getId())) {
                return AuthorizationResult.allow("Fleet owner access to own fleet");
            }
        }

        return AuthorizationResult.deny("User cannot access this fleet", "NO_FLEET_ACCESS");
    }

    @Override
    public AuthorizationResult canAccessUserNotifications(User user, Long targetUserId) {
        log.debug("Checking notification access for user {} to user {}", user.getId(), targetUserId);

        // Admin can access all notifications
        if (user.getRole() == User.Role.ADMIN) {
            return AuthorizationResult.allow("Admin access to notifications");
        }

        // Users can access their own notifications
        if (user.getId().equals(targetUserId)) {
            return AuthorizationResult.allow("User access to own notifications");
        }

        // Fleet owners can access notifications for their drivers (if implemented)
        if (user.getRole() == User.Role.FLEET_OWNER) {
            // This would require a driver-fleet owner relationship check
            // For now, deny by default
        }

        return AuthorizationResult.deny("User cannot access these notifications", "NO_NOTIFICATION_ACCESS");
    }

    @Override
    public List<String> filterAuthorizedTopics(User user, List<String> topics) {
        List<String> authorizedTopics = new ArrayList<>();
        
        for (String topic : topics) {
            AuthorizationResult result = canSubscribeToTopic(user, topic, null);
            if (result.allowed()) {
                authorizedTopics.add(topic);
            }
        }
        
        return authorizedTopics;
    }

    @Override
    public List<String> getAuthorizedTopicPatterns(User user) {
        List<String> patterns = new ArrayList<>();
        
        Map<String, List<String>> roleTopicAccess = securityConfig.getSecurityPolicy().getRoleTopicAccess();
        List<String> allowedPatterns = roleTopicAccess.get(user.getRole().name());
        
        if (allowedPatterns != null) {
            patterns.addAll(allowedPatterns);
        }
        
        // Add user-specific patterns
        switch (user.getRole()) {
            case DRIVER:
                patterns.add("user." + user.getId() + ".*");
                break;
            case SHIPPER:
                patterns.add("user." + user.getId() + ".*");
                break;
            case FLEET_OWNER:
                patterns.add("user." + user.getId() + ".*");
                patterns.add("fleet.owner." + user.getId() + ".*");
                break;
            case ADMIN:
                patterns.add("*");
                break;
        }
        
        return patterns;
    }

    @Override
    public AuthorizationResult validateSubscriptionFilter(User user, String topic, Object filter) {
        // Basic filter validation - extend based on requirements
        if (filter == null) {
            return AuthorizationResult.allow("No filter validation needed");
        }

        // Example: Validate trip-specific filters
        if (TRIP_TOPIC_PATTERN.matcher(topic).matches() && filter instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> filterMap = (Map<String, Object>) filter;
            
            // Ensure user can only filter for trips they have access to
            Object tripIdFilter = filterMap.get("tripId");
            if (tripIdFilter instanceof Long) {
                AuthorizationResult tripAccess = canAccessTrip(user, (Long) tripIdFilter);
                if (!tripAccess.allowed()) {
                    return AuthorizationResult.deny(
                        "Cannot filter for unauthorized trip", "UNAUTHORIZED_TRIP_FILTER"
                    );
                }
            }
        }

        return AuthorizationResult.allow("Filter validation passed");
    }

    @Override
    public AuthorizationResult canPerformAdminAction(User user, String action, Object target) {
        log.debug("Checking admin action authorization for user {} action {}", user.getId(), action);

        // Only admins can perform administrative actions
        if (user.getRole() != User.Role.ADMIN) {
            return AuthorizationResult.denyRequireRole(
                "Administrative actions require admin role", "ADMIN"
            );
        }

        // Additional action-specific checks could be added here
        return AuthorizationResult.allow("Admin action authorized");
    }

    // ── Private Helper Methods ───────────────────────────────────────────────

    private boolean hasMinimumRole(User user) {
        String minimumRole = securityConfig.getSecurityPolicy().getMinimumRole();
        
        // Simple role hierarchy check
        return switch (minimumRole) {
            case "ADMIN" -> user.getRole() == User.Role.ADMIN;
            case "FLEET_OWNER" -> user.getRole() == User.Role.ADMIN || user.getRole() == User.Role.FLEET_OWNER;
            case "DRIVER" -> user.getRole() != null; // All roles except null
            case "SHIPPER" -> user.getRole() != null;
            default -> true;
        };
    }

    private boolean isTopicAllowedForRole(String role, String topic) {
        Map<String, List<String>> roleTopicAccess = securityConfig.getSecurityPolicy().getRoleTopicAccess();
        List<String> allowedPatterns = roleTopicAccess.get(role);
        
        if (allowedPatterns == null) {
            return false;
        }
        
        for (String pattern : allowedPatterns) {
            if ("*".equals(pattern) || topic.matches(pattern.replace("*", ".*"))) {
                return true;
            }
        }
        
        return false;
    }

    private boolean hasLocationUpdateRole(User user) {
        return user.getRole() == User.Role.DRIVER || 
               user.getRole() == User.Role.FLEET_OWNER || 
               user.getRole() == User.Role.ADMIN;
    }

    private AuthorizationResult canSubscribeToTripTopic(User user, String topic) {
        // Extract trip ID from topic
        var matcher = TRIP_TOPIC_PATTERN.matcher(topic);
        if (matcher.matches()) {
            Long tripId = Long.parseLong(matcher.group(1));
            return canAccessTrip(user, tripId);
        }
        return AuthorizationResult.deny("Invalid trip topic format", "INVALID_TOPIC_FORMAT");
    }

    private AuthorizationResult canSubscribeToUserTopic(User user, String topic) {
        // Extract user ID from topic
        var matcher = USER_TOPIC_PATTERN.matcher(topic);
        if (matcher.matches()) {
            Long targetUserId = Long.parseLong(matcher.group(1));
            return canAccessUserNotifications(user, targetUserId);
        }
        return AuthorizationResult.deny("Invalid user topic format", "INVALID_TOPIC_FORMAT");
    }

    private AuthorizationResult canSubscribeToVehicleTopic(User user, String topic) {
        // Extract vehicle ID from topic
        var matcher = VEHICLE_TOPIC_PATTERN.matcher(topic);
        if (matcher.matches()) {
            Long vehicleId = Long.parseLong(matcher.group(1));
            return canAccessVehicle(user, vehicleId);
        }
        return AuthorizationResult.deny("Invalid vehicle topic format", "INVALID_TOPIC_FORMAT");
    }

    private AuthorizationResult canSubscribeToFleetTopic(User user, String topic) {
        // Fleet topics can be general or specific to a fleet owner
        if (topic.equals("fleet.vehicles")) {
            // General fleet topic - check if user can access any fleet data
            if (user.getRole() == User.Role.ADMIN || user.getRole() == User.Role.FLEET_OWNER) {
                return AuthorizationResult.allow("User can access fleet data");
            }
        }
        
        return AuthorizationResult.deny("User cannot access fleet topics", "NO_FLEET_ACCESS");
    }
}