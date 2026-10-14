package com.ethioloadai.converter;

import com.ethioloadai.bid.converter.BidCounterByConverter;
import com.ethioloadai.bid.converter.BidStatusConverter;
import com.ethioloadai.bid.entity.Bid;
import com.ethioloadai.booking.converter.BookingStatusConverter;
import com.ethioloadai.booking.entity.Booking;
import com.ethioloadai.document.converter.DocumentStatusConverter;
import com.ethioloadai.document.converter.DocumentTypeConverter;
import com.ethioloadai.document.entity.DriverDocument;
import com.ethioloadai.freight.converter.FreightPriceTypeConverter;
import com.ethioloadai.freight.converter.FreightServiceTypeConverter;
import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.payment.converter.PaymentStatusConverter;
import com.ethioloadai.payment.entity.Payment;
import com.ethioloadai.recommendation.converter.BackhaulStatusConverter;
import com.ethioloadai.recommendation.entity.BackhaulRecommendation;
import com.ethioloadai.trip.converter.TripStatusConverter;
import com.ethioloadai.trip.converter.TripStopStatusConverter;
import com.ethioloadai.trip.converter.TripTypeConverter;
import com.ethioloadai.trip.entity.Trip;
import com.ethioloadai.trip.entity.TripStop;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for all new JPA attribute converters introduced in Milestone 2.
 *
 * Each converter must:
 *   - Write enum values as lowercase strings to match DB CHECK constraints.
 *   - Read lowercase DB values back to the correct Java enum constant.
 *   - Handle null safely.
 *   - Throw IllegalArgumentException for unrecognised DB values.
 */
@DisplayName("Milestone 2 — JPA Attribute Converters")
class NewConverterTest {

    // ── BidStatusConverter ────────────────────────────────────────────────────

    @Nested @DisplayName("BidStatusConverter")
    class BidStatusConverterTests {
        private final BidStatusConverter converter = new BidStatusConverter();

        @ParameterizedTest(name = "BidStatus.{0} → lowercase")
        @EnumSource(Bid.BidStatus.class)
        void allValuesWriteLowercase(Bid.BidStatus status) {
            assertThat(converter.convertToDatabaseColumn(status))
                    .isEqualTo(status.name().toLowerCase());
        }

        @ParameterizedTest(name = "'{0}' DB value round-trips correctly")
        @EnumSource(Bid.BidStatus.class)
        void allValuesRoundTrip(Bid.BidStatus status) {
            String db = converter.convertToDatabaseColumn(status);
            assertThat(converter.convertToEntityAttribute(db)).isEqualTo(status);
        }

        @Test void nullToNull() { assertThat(converter.convertToDatabaseColumn(null)).isNull(); }
        @Test void nullDbToNull() { assertThat(converter.convertToEntityAttribute(null)).isNull(); }
        @Test void invalidDbThrows() {
            assertThatThrownBy(() -> converter.convertToEntityAttribute("unknown"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test void counteredIsSupported() {
            assertThat(converter.convertToDatabaseColumn(Bid.BidStatus.COUNTERED))
                    .isEqualTo("countered");
        }
    }

    // ── BidCounterByConverter ─────────────────────────────────────────────────

    @Nested @DisplayName("BidCounterByConverter")
    class BidCounterByConverterTests {
        private final BidCounterByConverter converter = new BidCounterByConverter();

        @Test void shipperWritesLowercase() {
            assertThat(converter.convertToDatabaseColumn(Bid.CounterBy.SHIPPER)).isEqualTo("shipper");
        }
        @Test void driverWritesLowercase() {
            assertThat(converter.convertToDatabaseColumn(Bid.CounterBy.DRIVER)).isEqualTo("driver");
        }
        @Test void roundTrip() {
            assertThat(converter.convertToEntityAttribute("shipper")).isEqualTo(Bid.CounterBy.SHIPPER);
            assertThat(converter.convertToEntityAttribute("driver")).isEqualTo(Bid.CounterBy.DRIVER);
        }
        @Test void nullHandled() {
            assertThat(converter.convertToDatabaseColumn(null)).isNull();
            assertThat(converter.convertToEntityAttribute(null)).isNull();
        }
    }

    // ── BookingStatusConverter ────────────────────────────────────────────────

    @Nested @DisplayName("BookingStatusConverter")
    class BookingStatusConverterTests {
        private final BookingStatusConverter converter = new BookingStatusConverter();

        @ParameterizedTest(name = "BookingStatus.{0} round-trips")
        @EnumSource(Booking.BookingStatus.class)
        void allValuesRoundTrip(Booking.BookingStatus status) {
            String db = converter.convertToDatabaseColumn(status);
            assertThat(converter.convertToEntityAttribute(db)).isEqualTo(status);
        }

        @Test void deliveredIsIncluded() {
            // DELIVERED was added in a later Laravel migration — must be present
            assertThat(converter.convertToDatabaseColumn(Booking.BookingStatus.DELIVERED))
                    .isEqualTo("delivered");
        }
        @Test void cancelledIsIncluded() {
            assertThat(converter.convertToDatabaseColumn(Booking.BookingStatus.CANCELLED))
                    .isEqualTo("cancelled");
        }
    }

    // ── TripStatusConverter ───────────────────────────────────────────────────

    @Nested @DisplayName("TripStatusConverter")
    class TripStatusConverterTests {
        private final TripStatusConverter conv = new TripStatusConverter();

        @ParameterizedTest @EnumSource(Trip.TripStatus.class)
        void roundTrips(Trip.TripStatus s) {
            assertThat(conv.convertToEntityAttribute(conv.convertToDatabaseColumn(s))).isEqualTo(s);
        }
        @Test void ongoingLowercase() { assertThat(conv.convertToDatabaseColumn(Trip.TripStatus.ONGOING)).isEqualTo("ongoing"); }
    }

    // ── TripTypeConverter ─────────────────────────────────────────────────────

    @Nested @DisplayName("TripTypeConverter")
    class TripTypeConverterTests {
        private final TripTypeConverter conv = new TripTypeConverter();

        @Test void singleLowercase() { assertThat(conv.convertToDatabaseColumn(Trip.TripType.SINGLE)).isEqualTo("single"); }
        @Test void multiStopLowercase() { assertThat(conv.convertToDatabaseColumn(Trip.TripType.MULTI_STOP)).isEqualTo("multi_stop"); }
        @Test void multiStopRoundTrips() {
            assertThat(conv.convertToEntityAttribute("multi_stop")).isEqualTo(Trip.TripType.MULTI_STOP);
        }
    }

    // ── TripStopStatusConverter ───────────────────────────────────────────────

    @Nested @DisplayName("TripStopStatusConverter")
    class TripStopStatusConverterTests {
        private final TripStopStatusConverter conv = new TripStopStatusConverter();

        @ParameterizedTest @EnumSource(TripStop.StopStatus.class)
        void roundTrips(TripStop.StopStatus s) {
            assertThat(conv.convertToEntityAttribute(conv.convertToDatabaseColumn(s))).isEqualTo(s);
        }
    }

    // ── PaymentStatusConverter ────────────────────────────────────────────────

    @Nested @DisplayName("PaymentStatusConverter")
    class PaymentStatusConverterTests {
        private final PaymentStatusConverter conv = new PaymentStatusConverter();

        @ParameterizedTest @EnumSource(Payment.PaymentStatus.class)
        void roundTrips(Payment.PaymentStatus s) {
            assertThat(conv.convertToEntityAttribute(conv.convertToDatabaseColumn(s))).isEqualTo(s);
        }
        @Test void paidLowercase() { assertThat(conv.convertToDatabaseColumn(Payment.PaymentStatus.PAID)).isEqualTo("paid"); }
    }

    // ── DocumentStatusConverter ───────────────────────────────────────────────

    @Nested @DisplayName("DocumentStatusConverter")
    class DocumentStatusConverterTests {
        private final DocumentStatusConverter conv = new DocumentStatusConverter();

        @ParameterizedTest @EnumSource(DriverDocument.DocumentStatus.class)
        void roundTrips(DriverDocument.DocumentStatus s) {
            assertThat(conv.convertToEntityAttribute(conv.convertToDatabaseColumn(s))).isEqualTo(s);
        }
    }

    // ── DocumentTypeConverter ─────────────────────────────────────────────────

    @Nested @DisplayName("DocumentTypeConverter")
    class DocumentTypeConverterTests {
        private final DocumentTypeConverter conv = new DocumentTypeConverter();

        @ParameterizedTest @EnumSource(DriverDocument.DocumentType.class)
        void roundTrips(DriverDocument.DocumentType t) {
            assertThat(conv.convertToEntityAttribute(conv.convertToDatabaseColumn(t))).isEqualTo(t);
        }

        @Test void vehicleRegistrationLowercase() {
            assertThat(conv.convertToDatabaseColumn(DriverDocument.DocumentType.VEHICLE_REGISTRATION))
                    .isEqualTo("vehicle_registration");
        }

        @Test void labelsAreNonEmpty() {
            for (DriverDocument.DocumentType type : DriverDocument.DocumentType.values()) {
                assertThat(type.getLabel()).isNotBlank();
            }
        }
    }

    // ── BackhaulStatusConverter ───────────────────────────────────────────────

    @Nested @DisplayName("BackhaulStatusConverter")
    class BackhaulStatusConverterTests {
        private final BackhaulStatusConverter conv = new BackhaulStatusConverter();

        @ParameterizedTest @EnumSource(BackhaulRecommendation.BackhaulStatus.class)
        void roundTrips(BackhaulRecommendation.BackhaulStatus s) {
            assertThat(conv.convertToEntityAttribute(conv.convertToDatabaseColumn(s))).isEqualTo(s);
        }

        @Test void bidPlacedWritesCorrectly() {
            assertThat(conv.convertToDatabaseColumn(BackhaulRecommendation.BackhaulStatus.BID_PLACED))
                    .isEqualTo("bid_placed");
            assertThat(conv.convertToEntityAttribute("bid_placed"))
                    .isEqualTo(BackhaulRecommendation.BackhaulStatus.BID_PLACED);
        }
    }

    // ── FreightServiceTypeConverter ───────────────────────────────────────────

    @Nested @DisplayName("FreightServiceTypeConverter")
    class FreightServiceTypeConverterTests {
        private final FreightServiceTypeConverter conv = new FreightServiceTypeConverter();

        @ParameterizedTest @EnumSource(Freight.ServiceType.class)
        void roundTrips(Freight.ServiceType t) {
            assertThat(conv.convertToEntityAttribute(conv.convertToDatabaseColumn(t))).isEqualTo(t);
        }
        @Test void intracityLowercase() {
            assertThat(conv.convertToDatabaseColumn(Freight.ServiceType.INTRACITY)).isEqualTo("intracity");
        }
    }

    // ── FreightPriceTypeConverter ─────────────────────────────────────────────

    @Nested @DisplayName("FreightPriceTypeConverter")
    class FreightPriceTypeConverterTests {
        private final FreightPriceTypeConverter conv = new FreightPriceTypeConverter();

        @ParameterizedTest @EnumSource(Freight.PriceType.class)
        void roundTrips(Freight.PriceType t) {
            assertThat(conv.convertToEntityAttribute(conv.convertToDatabaseColumn(t))).isEqualTo(t);
        }
        @Test void fixedLowercase() {
            assertThat(conv.convertToDatabaseColumn(Freight.PriceType.FIXED)).isEqualTo("fixed");
        }
    }
}
