package com.ethioloadai.routing.controller;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RoutingControllerTest {

    @Test
    void disabledExternalServicesUseFallbacksWithoutHttpRequests() {
        RestClient.Builder builder = mock(RestClient.Builder.class);
        RestClient httpClient = mock(RestClient.class);
        when(builder.build()).thenReturn(httpClient);
        RoutingController controller = new RoutingController(
                builder,
                "http://router.project-osrm.org",
                "https://nominatim.openstreetmap.org",
                "EthioLoadAI/1.0",
                false);

        Map<String, Object> route = controller.route(
                9.0, null, 38.0, null, 8.0, null, 37.0, null).getBody();
        Map<String, Object> search = controller.search("Adama").getBody();
        Map<String, Object> reverse = controller.reverse(9.0, 38.0).getBody();
        Map<String, Object> nearestCity = controller.nearestCity(
                Map.of("lat", 9.0, "lng", 38.0)).getBody();

        assertThat(route).containsEntry("source", "haversine");
        assertThat(search).containsEntry("places", java.util.List.of());
        assertThat(reverse).containsEntry("type", "unknown");
        assertThat(nearestCity)
                .containsEntry("city", "")
                .containsEntry("distance_km", 0.0);
        verifyNoInteractions(httpClient);
    }
}
