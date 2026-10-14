package com.ethioloadai.freight;

import com.ethioloadai.freight.converter.FreightPriceTypeConverter;
import com.ethioloadai.freight.converter.FreightServiceTypeConverter;
import com.ethioloadai.freight.converter.FreightStatusConverter;
import com.ethioloadai.freight.entity.Freight;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for V6 JPA enum converters to ensure proper database persistence
 * and retrieval of enum values in lowercase format.
 */
@DisplayName("Freight V6 Converter Tests")
class FreightV6ConverterTest {

    @Nested
    @DisplayName("FreightStatusConverter")
    class FreightStatusConverterTest {

        private final FreightStatusConverter converter = new FreightStatusConverter();

        @Test
        @DisplayName("converts POSTED to 'posted'")
        void convertPostedToDatabaseColumn() {
            String result = converter.convertToDatabaseColumn(Freight.FreightStatus.POSTED);
            assertThat(result).isEqualTo("posted");
        }

        @Test
        @DisplayName("converts IN_TRANSIT to 'in_transit'")
        void convertInTransitToDatabaseColumn() {
            String result = converter.convertToDatabaseColumn(Freight.FreightStatus.IN_TRANSIT);
            assertThat(result).isEqualTo("in_transit");
        }

        @Test
        @DisplayName("converts null to null")
        void convertNullToDatabaseColumn() {
            String result = converter.convertToDatabaseColumn(null);
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("converts 'posted' to POSTED")
        void convertPostedToEntityAttribute() {
            Freight.FreightStatus result = converter.convertToEntityAttribute("posted");
            assertThat(result).isEqualTo(Freight.FreightStatus.POSTED);
        }

        @Test
        @DisplayName("converts 'in_transit' to IN_TRANSIT")
        void convertInTransitToEntityAttribute() {
            Freight.FreightStatus result = converter.convertToEntityAttribute("in_transit");
            assertThat(result).isEqualTo(Freight.FreightStatus.IN_TRANSIT);
        }

        @Test
        @DisplayName("converts null/blank to null")
        void convertNullToEntityAttribute() {
            assertThat(converter.convertToEntityAttribute(null)).isNull();
            assertThat(converter.convertToEntityAttribute("")).isNull();
            assertThat(converter.convertToEntityAttribute("   ")).isNull();
        }

        @Test
        @DisplayName("throws exception for invalid database value")
        void throwsExceptionForInvalidValue() {
            assertThatThrownBy(() -> converter.convertToEntityAttribute("invalid_status"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid freight status value in database: 'invalid_status'");
        }
    }

    @Nested
    @DisplayName("FreightServiceTypeConverter")
    class FreightServiceTypeConverterTest {

        private final FreightServiceTypeConverter converter = new FreightServiceTypeConverter();

        @Test
        @DisplayName("converts INTERCITY to 'intercity'")
        void convertIntercityToDatabaseColumn() {
            String result = converter.convertToDatabaseColumn(Freight.ServiceType.INTERCITY);
            assertThat(result).isEqualTo("intercity");
        }

        @Test
        @DisplayName("converts INTRACITY to 'intracity'")
        void convertIntracityToDatabaseColumn() {
            String result = converter.convertToDatabaseColumn(Freight.ServiceType.INTRACITY);
            assertThat(result).isEqualTo("intracity");
        }

        @Test
        @DisplayName("converts null to null")
        void convertNullToDatabaseColumn() {
            String result = converter.convertToDatabaseColumn(null);
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("converts 'intercity' to INTERCITY")
        void convertIntercityToEntityAttribute() {
            Freight.ServiceType result = converter.convertToEntityAttribute("intercity");
            assertThat(result).isEqualTo(Freight.ServiceType.INTERCITY);
        }

        @Test
        @DisplayName("converts 'intracity' to INTRACITY")
        void convertIntracityToEntityAttribute() {
            Freight.ServiceType result = converter.convertToEntityAttribute("intracity");
            assertThat(result).isEqualTo(Freight.ServiceType.INTRACITY);
        }

        @Test
        @DisplayName("converts null/blank to null")
        void convertNullToEntityAttribute() {
            assertThat(converter.convertToEntityAttribute(null)).isNull();
            assertThat(converter.convertToEntityAttribute("")).isNull();
            assertThat(converter.convertToEntityAttribute("   ")).isNull();
        }

        @Test
        @DisplayName("throws exception for invalid database value")
        void throwsExceptionForInvalidValue() {
            assertThatThrownBy(() -> converter.convertToEntityAttribute("invalid_service_type"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid service_type in database: 'invalid_service_type'");
        }
    }

    @Nested
    @DisplayName("FreightPriceTypeConverter")
    class FreightPriceTypeConverterTest {

        private final FreightPriceTypeConverter converter = new FreightPriceTypeConverter();

        @Test
        @DisplayName("converts NEGOTIABLE to 'negotiable'")
        void convertNegotiableToDatabaseColumn() {
            String result = converter.convertToDatabaseColumn(Freight.PriceType.NEGOTIABLE);
            assertThat(result).isEqualTo("negotiable");
        }

        @Test
        @DisplayName("converts FIXED to 'fixed'")
        void convertFixedToDatabaseColumn() {
            String result = converter.convertToDatabaseColumn(Freight.PriceType.FIXED);
            assertThat(result).isEqualTo("fixed");
        }

        @Test
        @DisplayName("converts null to null")
        void convertNullToDatabaseColumn() {
            String result = converter.convertToDatabaseColumn(null);
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("converts 'negotiable' to NEGOTIABLE")
        void convertNegotiableToEntityAttribute() {
            Freight.PriceType result = converter.convertToEntityAttribute("negotiable");
            assertThat(result).isEqualTo(Freight.PriceType.NEGOTIABLE);
        }

        @Test
        @DisplayName("converts 'fixed' to FIXED")
        void convertFixedToEntityAttribute() {
            Freight.PriceType result = converter.convertToEntityAttribute("fixed");
            assertThat(result).isEqualTo(Freight.PriceType.FIXED);
        }

        @Test
        @DisplayName("converts null/blank to null")
        void convertNullToEntityAttribute() {
            assertThat(converter.convertToEntityAttribute(null)).isNull();
            assertThat(converter.convertToEntityAttribute("")).isNull();
            assertThat(converter.convertToEntityAttribute("   ")).isNull();
        }

        @Test
        @DisplayName("throws exception for invalid database value")
        void throwsExceptionForInvalidValue() {
            assertThatThrownBy(() -> converter.convertToEntityAttribute("invalid_price_type"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid price_type in database: 'invalid_price_type'");
        }
    }
}