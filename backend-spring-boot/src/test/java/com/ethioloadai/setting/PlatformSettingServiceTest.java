package com.ethioloadai.setting;

import com.ethioloadai.exception.ValidationException;
import com.ethioloadai.setting.dto.PricingSettingsResponse;
import com.ethioloadai.setting.dto.UpdatePricingSettingsRequest;
import com.ethioloadai.setting.entity.PlatformSetting;
import com.ethioloadai.setting.repository.PlatformSettingRepository;
import com.ethioloadai.setting.service.PlatformSettingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PlatformSettingService")
class PlatformSettingServiceTest {

    @Mock
    private PlatformSettingRepository platformSettingRepository;

    @InjectMocks
    private PlatformSettingServiceImpl platformSettingService;

    @Nested
    @DisplayName("getPricingSettings")
    class GetPricingSettingsTests {

        @Test
        @DisplayName("Returns stored pricing rates when settings exist in repository")
        void returnsStoredSettingsWhenPresent() {
            when(platformSettingRepository.findByKey(PlatformSettingServiceImpl.KEY_RATE_MIN))
                    .thenReturn(Optional.of(PlatformSetting.builder().key("pricing.rate_min").value("20.5").build()));
            when(platformSettingRepository.findByKey(PlatformSettingServiceImpl.KEY_RATE_MAX))
                    .thenReturn(Optional.of(PlatformSetting.builder().key("pricing.rate_max").value("35.0").build()));
            when(platformSettingRepository.findByKey(PlatformSettingServiceImpl.KEY_INTRACITY_RATE_MIN))
                    .thenReturn(Optional.of(PlatformSetting.builder().key("pricing.intracity_rate_min").value("60.0").build()));
            when(platformSettingRepository.findByKey(PlatformSettingServiceImpl.KEY_INTRACITY_RATE_MAX))
                    .thenReturn(Optional.of(PlatformSetting.builder().key("pricing.intracity_rate_max").value("120.0").build()));

            PricingSettingsResponse response = platformSettingService.getPricingSettings();

            assertThat(response).isNotNull();
            assertThat(response.getRateMin()).isEqualTo(20.5);
            assertThat(response.getRateMax()).isEqualTo(35.0);
            assertThat(response.getIntracityRateMin()).isEqualTo(60.0);
            assertThat(response.getIntracityRateMax()).isEqualTo(120.0);
            assertThat(response.getSuccess()).isTrue();
        }

        @Test
        @DisplayName("Returns fallback default values when repository has no settings")
        void returnsDefaultSettingsWhenMissing() {
            when(platformSettingRepository.findByKey(any())).thenReturn(Optional.empty());

            PricingSettingsResponse response = platformSettingService.getPricingSettings();

            assertThat(response).isNotNull();
            assertThat(response.getRateMin()).isEqualTo(PlatformSettingServiceImpl.DEFAULT_RATE_MIN);
            assertThat(response.getRateMax()).isEqualTo(PlatformSettingServiceImpl.DEFAULT_RATE_MAX);
            assertThat(response.getIntracityRateMin()).isEqualTo(PlatformSettingServiceImpl.DEFAULT_INTRACITY_RATE_MIN);
            assertThat(response.getIntracityRateMax()).isEqualTo(PlatformSettingServiceImpl.DEFAULT_INTRACITY_RATE_MAX);
            assertThat(response.getSuccess()).isTrue();
        }
    }

    @Nested
    @DisplayName("updatePricingSettings")
    class UpdatePricingSettingsTests {

        @Test
        @DisplayName("Successfully saves new rates and returns updated values")
        void updatesRatesSuccessfully() {
            UpdatePricingSettingsRequest request = UpdatePricingSettingsRequest.builder()
                    .rateMin(22.0)
                    .rateMax(32.0)
                    .build();

            when(platformSettingRepository.findByKey(PlatformSettingServiceImpl.KEY_RATE_MIN))
                    .thenReturn(Optional.of(PlatformSetting.builder().key("pricing.rate_min").value("18").build()));
            when(platformSettingRepository.findByKey(PlatformSettingServiceImpl.KEY_RATE_MAX))
                    .thenReturn(Optional.of(PlatformSetting.builder().key("pricing.rate_max").value("28").build()));
            when(platformSettingRepository.findByKey(PlatformSettingServiceImpl.KEY_INTRACITY_RATE_MIN))
                    .thenReturn(Optional.of(PlatformSetting.builder().key("pricing.intracity_rate_min").value("50").build()));
            when(platformSettingRepository.findByKey(PlatformSettingServiceImpl.KEY_INTRACITY_RATE_MAX))
                    .thenReturn(Optional.of(PlatformSetting.builder().key("pricing.intracity_rate_max").value("100").build()));

            PricingSettingsResponse response = platformSettingService.updatePricingSettings(request);

            assertThat(response).isNotNull();
            assertThat(response.getRateMin()).isEqualTo(22.0);
            assertThat(response.getRateMax()).isEqualTo(32.0);
            assertThat(response.getSuccess()).isTrue();

            verify(platformSettingRepository, times(2)).save(any(PlatformSetting.class));
        }

        @Test
        @DisplayName("Throws ValidationException when rate_min is greater than or equal to rate_max")
        void throwsValidationExceptionWhenRateMinGteRateMax() {
            UpdatePricingSettingsRequest request = UpdatePricingSettingsRequest.builder()
                    .rateMin(30.0)
                    .rateMax(25.0)
                    .build();

            assertThatThrownBy(() -> platformSettingService.updatePricingSettings(request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("rate_min must be less than rate_max");

            verify(platformSettingRepository, never()).save(any());
        }

        @Test
        @DisplayName("Throws ValidationException when rate_min is less than 1.0")
        void throwsValidationExceptionWhenRateMinLessThanOne() {
            UpdatePricingSettingsRequest request = UpdatePricingSettingsRequest.builder()
                    .rateMin(0.5)
                    .rateMax(25.0)
                    .build();

            assertThatThrownBy(() -> platformSettingService.updatePricingSettings(request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("rate_min must be at least 1");

            verify(platformSettingRepository, never()).save(any());
        }

        @Test
        @DisplayName("Throws ValidationException when rate_min or rate_max is null")
        void throwsValidationExceptionWhenValuesNull() {
            UpdatePricingSettingsRequest request = UpdatePricingSettingsRequest.builder()
                    .rateMin(null)
                    .rateMax(25.0)
                    .build();

            assertThatThrownBy(() -> platformSettingService.updatePricingSettings(request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("rate_min and rate_max are required");
        }
    }
}
