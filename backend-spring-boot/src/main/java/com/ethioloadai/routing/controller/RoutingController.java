package com.ethioloadai.routing.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping
public class RoutingController {
    private static final Logger log = LoggerFactory.getLogger(RoutingController.class);
    private final RestClient restClient;
    private final String osrmBaseUrl;
    private final String nominatimBaseUrl;
    private final String nominatimUserAgent;
    private final boolean externalServicesEnabled;

    public RoutingController(
            RestClient.Builder restClientBuilder,
            @Value("${osrm.base-url:http://router.project-osrm.org}") String osrmBaseUrl,
            @Value("${nominatim.base-url:https://nominatim.openstreetmap.org}") String nominatimBaseUrl,
            @Value("${nominatim.user-agent:EthioLoadAI/1.0}") String nominatimUserAgent,
            @Value("${routing.external-services.enabled:true}") boolean externalServicesEnabled) {
        this.restClient = restClientBuilder.build();
        this.osrmBaseUrl = osrmBaseUrl;
        this.nominatimBaseUrl = nominatimBaseUrl;
        this.nominatimUserAgent = nominatimUserAgent;
        this.externalServicesEnabled = externalServicesEnabled;
    }

    @GetMapping("/routing/route")
    public ResponseEntity<Map<String, Object>> route(
            @RequestParam(name = "fromLat", required = false) Double fromLat,
            @RequestParam(name = "from_lat", required = false) Double fromLatSnake,
            @RequestParam(name = "fromLng", required = false) Double fromLng,
            @RequestParam(name = "from_lng", required = false) Double fromLngSnake,
            @RequestParam(name = "toLat", required = false) Double toLat,
            @RequestParam(name = "to_lat", required = false) Double toLatSnake,
            @RequestParam(name = "toLng", required = false) Double toLng,
            @RequestParam(name = "to_lng", required = false) Double toLngSnake) {
        double fLat = fromLat != null ? fromLat : (fromLatSnake != null ? fromLatSnake : 0.0);
        double fLng = fromLng != null ? fromLng : (fromLngSnake != null ? fromLngSnake : 0.0);
        double tLat = toLat != null ? toLat : (toLatSnake != null ? toLatSnake : 0.0);
        double tLng = toLng != null ? toLng : (toLngSnake != null ? toLngSnake : 0.0);
        if (!externalServicesEnabled) {
            return ResponseEntity.ok(fallbackRoute(fLat, fLng, tLat, tLng));
        }
        try {
            Map<?, ?> response = restClient.get()
                    .uri(uriBuilder -> routeUri(uriBuilder, fLng, fLat, tLng, tLat))
                    .retrieve()
                    .body(Map.class);
            Map<?, ?> route = firstMap(response == null ? null : response.get("routes"));
            if (route != null) {
                return ResponseEntity.ok(toRouteResponse(route));
            }
        } catch (RuntimeException ignored) {
            // Return a usable straight-line route when the external router is unavailable.
        }
        return ResponseEntity.ok(fallbackRoute(fLat, fLng, tLat, tLng));
    }

    @GetMapping("/routing/search")
    public ResponseEntity<Map<String, Object>> search(@RequestParam String q) {
        if (!externalServicesEnabled) {
            return ResponseEntity.ok(Map.of("places", List.of()));
        }
        try {
            List<?> results = restClient.get()
                    .uri(UriComponentsBuilder.fromUriString(nominatimBaseUrl + "/search")
                            .queryParam("q", q)
                            .queryParam("format", "jsonv2")
                            .queryParam("limit", 8)
                            .queryParam("countrycodes", "et")
                        .build()
                        .toUri())
                    .header(HttpHeaders.USER_AGENT, nominatimUserAgent)
                    .retrieve()
                    .body(List.class);
                    log.debug("Nominatim returned {} results for query '{}'",
                        results == null ? 0 : results.size(), q);
            List<Map<String, Object>> places = new ArrayList<>();
            if (results != null) {
                for (Object item : results) {
                    if (item instanceof Map<?, ?> place) {
                        places.add(placeResponse(place));
                    }
                }
            }
            return ResponseEntity.ok(Map.of("places", places));
        } catch (RuntimeException exception) {
            log.warn("Nominatim search failed for query '{}'", q, exception);
            return ResponseEntity.ok(Map.of("places", List.of()));
        }
    }

    @GetMapping("/routing/reverse")
    public ResponseEntity<Map<String, Object>> reverse(
            @RequestParam double lat,
            @RequestParam double lng) {
        if (!externalServicesEnabled) {
            return ResponseEntity.ok(Map.of(
                    "name", "", "short_name", "", "lat", lat, "lng", lng, "type", "unknown"));
        }
        try {
            Map<?, ?> place = restClient.get()
                    .uri(UriComponentsBuilder.fromUriString(nominatimBaseUrl + "/reverse")
                            .queryParam("lat", lat)
                            .queryParam("lon", lng)
                            .queryParam("format", "jsonv2")
                        .build()
                        .toUri())
                    .retrieve()
                    .body(Map.class);
            if (place != null) {
                return ResponseEntity.ok(placeResponse(place));
            }
        } catch (RuntimeException ignored) {
            // Keep reverse geocoding optional for GPS updates.
        }
        return ResponseEntity.ok(Map.of("name", "", "short_name", "", "lat", lat, "lng", lng, "type", "unknown"));
    }

    @PostMapping("/geocode/nearest-city")
    public ResponseEntity<Map<String, Object>> nearestCity(@RequestBody Map<String, Object> request) {
        double lat = number(request.get("lat"));
        double lng = number(request.get("lng"));
        Map<String, Object> reverse = reverse(lat, lng).getBody();
        String city = reverse == null ? "" : String.valueOf(reverse.getOrDefault("short_name", ""));
        return ResponseEntity.ok(Map.of(
                "city", city,
                "distance_km", 0.0,
                "lat", lat,
                "lng", lng));
    }

    private java.net.URI routeUri(UriBuilder builder, double fromLng, double fromLat, double toLng, double toLat) {
        return builder.path(osrmBaseUrl + "/route/v1/driving/{coordinates}")
                .queryParam("overview", "full")
                .queryParam("geometries", "geojson")
                .queryParam("steps", "true")
                .build(fromLng + "," + fromLat + ";" + toLng + "," + toLat);
    }

    private Map<String, Object> toRouteResponse(Map<?, ?> route) {
        Map<String, Object> result = new HashMap<>();
        double distance = number(route.get("distance"));
        result.put("distance_km", distance / 1000.0);
        result.put("duration_min", (int) Math.round(number(route.get("duration")) / 60.0));
        result.put("source", "osrm");
        Object geometry = route.get("geometry");
        result.put("polyline", geometry instanceof Map<?, ?> map ? valueOr(map, "coordinates", List.of()) : List.of());
        result.put("steps", List.of());
        return result;
    }

    private Map<String, Object> fallbackRoute(double fromLat, double fromLng, double toLat, double toLng) {
        double distanceKm = haversine(fromLat, fromLng, toLat, toLng);
        return Map.of(
                "distance_km", distanceKm,
                "duration_min", (int) Math.ceil(distanceKm / 45.0 * 60.0),
                "polyline", List.of(List.of(fromLng, fromLat), List.of(toLng, toLat)),
                "steps", List.of(),
                "source", "haversine");
    }

    private Map<String, Object> placeResponse(Map<?, ?> place) {
        String displayName = String.valueOf(valueOr(place, "display_name", ""));
        String name = String.valueOf(valueOr(place, "name", displayName));
        return Map.of(
                "name", name,
                "short_name", name,
                "lat", number(place.get("lat")),
                "lng", number(valueOr(place, "lon", 0)),
                "type", String.valueOf(valueOr(place, "type", "place")));
    }

    private Object valueOr(Map<?, ?> map, String key, Object fallback) {
        return map.containsKey(key) && map.get(key) != null ? map.get(key) : fallback;
    }

    private Map<?, ?> firstMap(Object value) {
        if (value instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> map) {
            return map;
        }
        return null;
    }

    private double number(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (RuntimeException ignored) {
            return 0.0;
        }
    }

    private double haversine(double fromLat, double fromLng, double toLat, double toLng) {
        double lat = Math.toRadians(toLat - fromLat);
        double lng = Math.toRadians(toLng - fromLng);
        double a = Math.sin(lat / 2) * Math.sin(lat / 2)
                + Math.cos(Math.toRadians(fromLat)) * Math.cos(Math.toRadians(toLat))
                * Math.sin(lng / 2) * Math.sin(lng / 2);
        return 6371.0 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
