package com.ethioloadai.freight.service;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "app.freight.visibility")
public class FreightVisibilityProperties {
    private static final long MAX_HISTORY_DAYS = 3650;

    @Min(0)
    @Max(MAX_HISTORY_DAYS)
    private long driverHistoryDays = 30;

    @Min(0)
    @Max(MAX_HISTORY_DAYS)
    private long carrierHistoryDays = 90;

    public long getDriverHistoryDays() {
        return driverHistoryDays;
    }

    public void setDriverHistoryDays(long driverHistoryDays) {
        this.driverHistoryDays = driverHistoryDays;
    }

    public long getCarrierHistoryDays() {
        return carrierHistoryDays;
    }

    public void setCarrierHistoryDays(long carrierHistoryDays) {
        this.carrierHistoryDays = carrierHistoryDays;
    }
}
