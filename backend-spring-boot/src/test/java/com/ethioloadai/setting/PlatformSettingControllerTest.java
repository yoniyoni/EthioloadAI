package com.ethioloadai.setting;

import com.ethioloadai.setting.controller.PlatformSettingController;
import com.ethioloadai.setting.dto.PricingSettingsResponse;
import com.ethioloadai.setting.dto.UpdatePricingSettingsRequest;
import com.ethioloadai.setting.service.PlatformSettingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PlatformSettingController")
class PlatformSettingControllerTest {

    @Mock
    private PlatformSettingService platformSettingService;

    @InjectMocks
    private PlatformSettingController platformSettingController;

    @Test
    @DisplayName("GET /api/admin/settings/pricing returns 200 OK with PricingSettingsResponse")
    void getPricingSettings_returns200Ok() {
        PricingSettingsResponse mockResponse = PricingSettingsResponse.builder()
                .rateMin(18.0)
                .rateMax(28.0)
                .intracityRateMin(50.0)
                .intracityRateMax(100.0)
                .success(true)
                .build();

        when(platformSettingService.getPricingSettings()).thenReturn(mockResponse);

        ResponseEntity<PricingSettingsResponse> response = platformSettingController.getPricingSettings();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getRateMin()).isEqualTo(18.0);
        assertThat(response.getBody().getRateMax()).isEqualTo(28.0);
        verify(platformSettingService).getPricingSettings();
    }

    @Test
    @DisplayName("PATCH /api/admin/settings/pricing returns 200 OK with updated rates")
    void updatePricingSettings_returns200Ok() {
        UpdatePricingSettingsRequest request = UpdatePricingSettingsRequest.builder()
                .rateMin(20.0)
                .rateMax(30.0)
                .build();

        PricingSettingsResponse mockResponse = PricingSettingsResponse.builder()
                .rateMin(20.0)
                .rateMax(30.0)
                .intracityRateMin(50.0)
                .intracityRateMax(100.0)
                .success(true)
                .build();

        when(platformSettingService.updatePricingSettings(request)).thenReturn(mockResponse);

        ResponseEntity<PricingSettingsResponse> response = platformSettingController.updatePricingSettings(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getRateMin()).isEqualTo(20.0);
        assertThat(response.getBody().getRateMax()).isEqualTo(30.0);
        verify(platformSettingService).updatePricingSettings(request);
    }

    @Test
    @DisplayName("Controller class has @PreAuthorize(\"hasRole('ADMIN')\") annotation")
    void controllerHasAdminPreAuthorize() {
        PreAuthorize annotation = PlatformSettingController.class.getAnnotation(PreAuthorize.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("hasRole('ADMIN')");
    }
}
