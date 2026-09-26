package com.ethioloadai.trip.mapper;

import com.ethioloadai.booking.entity.Booking;
import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.trip.dto.TripResponse;
import com.ethioloadai.trip.dto.TripStopResponse;
import com.ethioloadai.trip.entity.Trip;
import com.ethioloadai.trip.entity.TripStop;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.vehicle.entity.Vehicle;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Component
public class TripMapper {

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    public TripResponse toResponse(Trip trip) {
        if (trip == null) {
            return null;
        }

        TripResponse.TripResponseBuilder builder = TripResponse.builder()
                .id(trip.getId())
                .startLocation(trip.getStartLocation())
                .destination(trip.getDestination())
                .tripStatus(trip.getTripStatus().name())
                .tripType(trip.getTripType().name())
                .totalStops(trip.getTotalStops())
                .completedStops(trip.getCompletedStops())
                .startTime(trip.getStartTime())
                .endTime(trip.getEndTime())
                .createdAt(trip.getCreatedAt())
                .updatedAt(trip.getUpdatedAt());

        // Add booking information
        if (trip.getBooking() != null) {
            builder.booking(mapBookingInfo(trip.getBooking()));
            
            // Add driver information
            if (trip.getBooking().getDriver() != null) {
                builder.driver(mapDriverInfo(trip.getBooking().getDriver()));
            }
            
            // Add vehicle information
            if (trip.getBooking().getVehicle() != null) {
                builder.vehicle(mapVehicleInfo(trip.getBooking().getVehicle()));
            }
            
            // Add shipper and cargo information
            if (trip.getBooking().getCargoRequest() != null) {
                Freight cargo = trip.getBooking().getCargoRequest();
                builder.cargo(mapCargoInfo(cargo));
                
                if (cargo.getShipper() != null) {
                    builder.shipper(mapShipperInfo(cargo.getShipper()));
                }
            }
        }

        // Add route information
        builder.routeInfo(mapRouteInfo(trip));

        // Add trip stops
        if (trip.getTripStops() != null && !trip.getTripStops().isEmpty()) {
            List<TripStopResponse> stopResponses = trip.getTripStops().stream()
                    .map(this::toTripStopResponse)
                    .toList();
            builder.stops(stopResponses);
        }

        return builder.build();
    }

    public TripStopResponse toTripStopResponse(TripStop tripStop) {
        if (tripStop == null) {
            return null;
        }

        TripStopResponse.TripStopResponseBuilder builder = TripStopResponse.builder()
                .id(tripStop.getId())
                .tripId(tripStop.getTrip().getId())
                .locationName(tripStop.getLocationName())
                .pickupLatitude(tripStop.getPickupLat())
                .pickupLongitude(tripStop.getPickupLng())
                .stopOrder(tripStop.getStopOrder())
                .stopStatus(tripStop.getStopStatus().name())
                .agreedPrice(tripStop.getAgreedPrice())
                .arrivalTime(tripStop.getArrivedAt())
                .loadingTime(null) // Field doesn't exist in entity yet
                .completionTime(tripStop.getCompletedAt())
                .createdAt(tripStop.getCreatedAt())
                .updatedAt(tripStop.getUpdatedAt());

        // Add cargo information if available
        if (tripStop.getCargoRequest() != null) {
            builder.cargoInfo(mapCargoStopInfo(tripStop.getCargoRequest()));
        }

        return builder.build();
    }

    private TripResponse.BookingInfo mapBookingInfo(Booking booking) {
        return TripResponse.BookingInfo.builder()
                .id(booking.getId())
                .status(booking.getBookingStatus().name())
                .estimatedPrice(booking.getEstimatedPrice())
                .commissionFee(booking.getCommissionFee())
                .build();
    }

    private TripResponse.DriverInfo mapDriverInfo(User driver) {
        return TripResponse.DriverInfo.builder()
                .id(driver.getId())
                .fullName(driver.getFullName())
                .phone(driver.getPhone())
                .build();
    }

    private TripResponse.ShipperInfo mapShipperInfo(User shipper) {
        return TripResponse.ShipperInfo.builder()
                .id(shipper.getId())
                .fullName(shipper.getFullName())
                .phone(shipper.getPhone())
                .build();
    }

    private TripResponse.VehicleInfo mapVehicleInfo(Vehicle vehicle) {
        return TripResponse.VehicleInfo.builder()
                .id(vehicle.getId())
                .plateNumber(vehicle.getPlateNumber())
                .truckType(vehicle.getTruckType())
                .loadCapacity(vehicle.getCapacity() != null ? BigDecimal.valueOf(vehicle.getCapacity()) : null)
                .build();
    }

    private TripResponse.CargoInfo mapCargoInfo(Freight cargo) {
        return TripResponse.CargoInfo.builder()
                .id(cargo.getId())
                .materialType(cargo.getMaterialType())
                .weightTons(cargo.getWeightTons())
                .volumeM3(cargo.getVolumeM3())
                .urgencyLevel(cargo.getUrgencyLevel())
                .build();
    }

    private TripStopResponse.CargoStopInfo mapCargoStopInfo(Freight cargo) {
        return TripStopResponse.CargoStopInfo.builder()
                .cargoRequestId(cargo.getId())
                .materialType(cargo.getMaterialType())
                .weightTons(cargo.getWeightTons())
                .shipperName(cargo.getShipper() != null ? cargo.getShipper().getFullName() : null)
                .build();
    }

    private TripResponse.RouteInfo mapRouteInfo(Trip trip) {
        TripResponse.RouteInfo.RouteInfoBuilder builder = TripResponse.RouteInfo.builder();

        // Calculate basic route statistics
        List<Map<String, Object>> routeData = trip.getRouteData();
        if (routeData != null && !routeData.isEmpty()) {
            builder.gpsPointCount(routeData.size());
            
            // Calculate total distance if we have GPS points
            BigDecimal totalDistance = calculateTotalDistance(routeData);
            if (totalDistance != null) {
                builder.totalDistanceKm(totalDistance);
            }
            
            // Get last GPS update timestamp
            Map<String, Object> lastPoint = routeData.get(routeData.size() - 1);
            if (lastPoint.get("ts") != null) {
                builder.lastGpsUpdate(lastPoint.get("ts").toString());
            }
            
            // Calculate average speed
            BigDecimal avgSpeed = calculateAverageSpeed(routeData);
            if (avgSpeed != null) {
                builder.averageSpeed(avgSpeed);
            }
        } else {
            builder.gpsPointCount(0);
        }

        // Calculate completion percentage
        if (trip.getTotalStops() != null && trip.getTotalStops() > 0) {
            int completedStops = trip.getCompletedStops() != null ? trip.getCompletedStops() : 0;
            BigDecimal completionPercentage = BigDecimal.valueOf(completedStops)
                    .divide(BigDecimal.valueOf(trip.getTotalStops()), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            builder.completionPercentage(completionPercentage);
        }

        return builder.build();
    }

    private BigDecimal calculateTotalDistance(List<Map<String, Object>> routeData) {
        if (routeData == null || routeData.size() < 2) {
            return BigDecimal.ZERO;
        }

        double totalDistance = 0.0;
        
        for (int i = 1; i < routeData.size(); i++) {
            Map<String, Object> prev = routeData.get(i - 1);
            Map<String, Object> curr = routeData.get(i);
            
            Object prevLat = prev.get("lat");
            Object prevLng = prev.get("lng");
            Object currLat = curr.get("lat");
            Object currLng = curr.get("lng");
            
            if (prevLat != null && prevLng != null && currLat != null && currLng != null) {
                double distance = calculateHaversineDistance(
                    ((Number) prevLat).doubleValue(),
                    ((Number) prevLng).doubleValue(),
                    ((Number) currLat).doubleValue(),
                    ((Number) currLng).doubleValue()
                );
                totalDistance += distance;
            }
        }

        return BigDecimal.valueOf(totalDistance).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateAverageSpeed(List<Map<String, Object>> routeData) {
        if (routeData == null || routeData.isEmpty()) {
            return null;
        }

        double totalSpeed = 0.0;
        int speedCount = 0;

        for (Map<String, Object> point : routeData) {
            Object speed = point.get("speed");
            if (speed != null) {
                totalSpeed += ((Number) speed).doubleValue();
                speedCount++;
            }
        }

        if (speedCount == 0) {
            return null;
        }

        return BigDecimal.valueOf(totalSpeed / speedCount).setScale(1, RoundingMode.HALF_UP);
    }

    private double calculateHaversineDistance(double lat1, double lng1, double lat2, double lng2) {
        final double R = 6371; // Earth's radius in kilometers

        double latDistance = Math.toRadians(lat2 - lat1);
        double lngDistance = Math.toRadians(lng2 - lng1);
        
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lngDistance / 2) * Math.sin(lngDistance / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return R * c;
    }
}