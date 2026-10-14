package com.ethioloadai.setting.service;

import com.ethioloadai.exception.ValidationException;
import com.ethioloadai.setting.dto.PricingSettingsResponse;
import com.ethioloadai.setting.dto.UpdatePricingSettingsRequest;
import com.ethioloadai.setting.entity.PlatformSetting;
import com.ethioloadai.setting.repository.PlatformSettingRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service implementation for managing admin platform settings.
 */
@Service
@RequiredArgsConstructor
public class PlatformSettingServiceImpl implements PlatformSettingService {

    private static final Logger log = LoggerFactory.getLogger(PlatformSettingServiceImpl.class);

    public static final String KEY_RATE_MIN = "pricing.rate_min";
    public static final String KEY_RATE_MAX = "pricing.rate_max";
    public static final String KEY_INTRACITY_RATE_MIN = "pricing.intracity_rate_min";
    public static final String KEY_INTRACITY_RATE_MAX = "pricing.intracity_rate_max";

    public static final double DEFAULT_RATE_MIN = 18.0;
    public static final double DEFAULT_RATE_MAX = 28.0;
    public static final double DEFAULT_INTRACITY_RATE_MIN = 50.0;
    public static final double DEFAULT_INTRACITY_RATE_MAX = 100.0;

    private final PlatformSettingRepository platformSettingRepository;

    @Override
    @Transactional(readOnly = true)
    public PricingSettingsResponse getPricingSettings() {
        double rateMin = getDoubleSetting(KEY_RATE_MIN, DEFAULT_RATE_MIN);
        double rateMax = getDoubleSetting(KEY_RATE_MAX, DEFAULT_RATE_MAX);
        double intracityMin = getDoubleSetting(KEY_INTRACITY_RATE_MIN, DEFAULT_INTRACITY_RATE_MIN);
        double intracityMax = getDoubleSetting(KEY_INTRACITY_RATE_MAX, DEFAULT_INTRACITY_RATE_MAX);

        log.debug("Fetched pricing settings: min={}, max={}, intracityMin={}, intracityMax={}",
                rateMin, rateMax, intracityMin, intracityMax);

        return PricingSettingsResponse.builder()
                .rateMin(rateMin)
                .rateMax(rateMax)
                .intracityRateMin(intracityMin)
                .intracityRateMax(intracityMax)
                .success(true)
                .build();
    }

    @Override
    @Transactional
    public PricingSettingsResponse updatePricingSettings(UpdatePricingSettingsRequest request) {
        if (request.getRateMin() == null || request.getRateMax() == null) {
            throw new ValidationException("rate_min and rate_max are required", "rate_min", "rate_min and rate_max are required");
        }
        if (request.getRateMin() < 1.0) {
            throw new ValidationException("rate_min must be at least 1", "rate_min", "rate_min must be at least 1");
        }
        if (request.getRateMax() < 1.0) {
            throw new ValidationException("rate_max must be at least 1", "rate_max", "rate_max must be at least 1");
        }
        if (request.getRateMin() >= request.getRateMax()) {
            throw new ValidationException("rate_min must be less than rate_max", "rate_min", "rate_min must be less than rate_max");
        }

        log.info("Updating platform pricing rates: min={}, max={}", request.getRateMin(), request.getRateMax());

        saveSetting(KEY_RATE_MIN, String.valueOf(request.getRateMin()));
        saveSetting(KEY_RATE_MAX, String.valueOf(request.getRateMax()));

        double intracityMin = getDoubleSetting(KEY_INTRACITY_RATE_MIN, DEFAULT_INTRACITY_RATE_MIN);
        double intracityMax = getDoubleSetting(KEY_INTRACITY_RATE_MAX, DEFAULT_INTRACITY_RATE_MAX);

        return PricingSettingsResponse.builder()
                .rateMin(request.getRateMin())
                .rateMax(request.getRateMax())
                .intracityRateMin(intracityMin)
                .intracityRateMax(intracityMax)
                .success(true)
                .build();
    }

    private double getDoubleSetting(String key, double defaultValue) {
        return platformSettingRepository.findByKey(key)
                .map(PlatformSetting::getValue)
                .map(val -> {
                    try {
                        return Double.parseDouble(val.trim());
                    } catch (NumberFormatException e) {
                        log.warn("Invalid numeric setting for key {}: '{}', using default {}", key, val, defaultValue);
                        return defaultValue;
                    }
                })
                .orElse(defaultValue);
    }

    private void saveSetting(String key, String value) {
        PlatformSetting setting = platformSettingRepository.findByKey(key)
                .orElseGet(() -> PlatformSetting.builder()
                        .key(key)
                        .build());
        setting.setValue(value);
        platformSettingRepository.save(setting);
    }
}
