package com.ethioloadai.freight;

import com.ethioloadai.freight.service.FreightVisibilityProperties;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FreightVisibilityPropertiesTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void defaultsAreFiniteDriverAndCarrierRetentionPeriods() {
        FreightVisibilityProperties properties = new FreightVisibilityProperties();

        assertThat(properties.getDriverHistoryDays()).isEqualTo(30);
        assertThat(properties.getCarrierHistoryDays()).isEqualTo(90);
        assertThat(validator.validate(properties)).isEmpty();
    }

    @Test
    void rejectsNegativeOrEffectivelyUnboundedRetentionValues() {
        FreightVisibilityProperties properties = new FreightVisibilityProperties();
        properties.setDriverHistoryDays(-1);
        properties.setCarrierHistoryDays(Long.MAX_VALUE);

        assertThat(validator.validate(properties)).hasSize(2);
    }
}
