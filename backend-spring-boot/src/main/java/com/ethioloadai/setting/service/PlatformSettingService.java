package com.ethioloadai.setting.service;

import com.ethioloadai.setting.dto.PricingSettingsResponse;
import com.ethioloadai.setting.dto.UpdatePricingSettingsRequest;

/**
 * Service interface for platform settings management.
 */
public interface PlatformSettingService {

    /**
     * Retrieve the current platform pricing configuration.
     *
     * @return current pricing settings with defaults if not configured
     */
    PricingSettingsResponse getPricingSettings();

    /**
     * Update the platform pricing rate configuration.
     *
     * @param request the new rate boundaries (rate_min, rate_max)
     * @return the updated pricing settings
     */
    PricingSettingsResponse updatePricingSettings(UpdatePricingSettingsRequest request);
}
