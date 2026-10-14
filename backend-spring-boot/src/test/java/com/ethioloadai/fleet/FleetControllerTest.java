package com.ethioloadai.fleet;

import com.ethioloadai.fleet.controller.FleetController;
import com.ethioloadai.fleet.dto.FleetDashboardResponse;
import com.ethioloadai.fleet.service.FleetService;
import com.ethioloadai.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FleetControllerTest {

    @Mock private FleetService fleetService;
    @InjectMocks private FleetController controller;

    @Test
    void dashboardUsesDefaultPageAndSize() {
        User caller = User.builder().id(15L).role(User.Role.FLEET_OWNER).build();
        FleetDashboardResponse result = FleetDashboardResponse.builder()
                .activeBookingCount(26)
                .page(0)
                .pageSize(20)
                .hasNext(true)
                .build();
        when(fleetService.getDashboard(15L, 0, 20)).thenReturn(result);

        ResponseEntity<FleetDashboardResponse> response = controller.getDashboard(caller, 0, 20);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getActiveBookingCount()).isEqualTo(26);
        assertThat(response.getBody().getPageSize()).isEqualTo(20);
        assertThat(response.getBody().isHasNext()).isTrue();
        verify(fleetService).getDashboard(15L, 0, 20);
    }

    @Test
    void dashboardPassesExplicitPageAndCapsSizeAtMaximum() {
        User caller = User.builder().id(15L).role(User.Role.FLEET_OWNER).build();
        when(fleetService.getDashboard(15L, 3, 100))
                .thenReturn(FleetDashboardResponse.builder().page(3).pageSize(100).build());

        ResponseEntity<FleetDashboardResponse> response = controller.getDashboard(caller, 3, 500);

        assertThat(response.getBody().getPage()).isEqualTo(3);
        assertThat(response.getBody().getPageSize()).isEqualTo(100);
        verify(fleetService).getDashboard(15L, 3, 100);
    }

    @Test
    void dashboardRejectsNegativePageAndNonPositiveSize() {
        User caller = User.builder().id(15L).role(User.Role.FLEET_OWNER).build();

        assertThatThrownBy(() -> controller.getDashboard(caller, -1, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Page must be zero or greater");
        assertThatThrownBy(() -> controller.getDashboard(caller, 0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Size must be at least 1");
    }

    @Test
    void dashboardRouteAndAuthorizationRemainUnchangedAndExposePaginationDefaults() throws Exception {
        Method method = FleetController.class.getMethod(
                "getDashboard", User.class, int.class, int.class);

        assertThat(method.getAnnotation(GetMapping.class).value()).containsExactly("/dashboard");
        assertThat(method.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasAnyRole('FLEET_OWNER','ADMIN')");
        Parameter[] parameters = method.getParameters();
        assertThat(parameters[1].getAnnotation(RequestParam.class).defaultValue()).isEqualTo("0");
        assertThat(parameters[2].getAnnotation(RequestParam.class).defaultValue()).isEqualTo("20");
    }
}
