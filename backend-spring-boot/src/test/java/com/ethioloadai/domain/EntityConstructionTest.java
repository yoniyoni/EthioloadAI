package com.ethioloadai.domain;

import com.ethioloadai.bid.entity.Bid;
import com.ethioloadai.booking.entity.Booking;
import com.ethioloadai.document.entity.DriverDocument;
import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.payment.entity.Payment;
import com.ethioloadai.rating.entity.Rating;
import com.ethioloadai.recommendation.entity.AIRecommendation;
import com.ethioloadai.recommendation.entity.BackhaulRecommendation;
import com.ethioloadai.setting.entity.PlatformSetting;
import com.ethioloadai.trip.entity.Trip;
import com.ethioloadai.trip.entity.TripStop;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests verifying entity builder defaults, enum presence, and monetary type safety.
 *
 * No Spring context or database required. These guard against accidental regressions
 * (e.g. using double instead of BigDecimal, wrong default statuses).
 */
@DisplayName("Domain entity construction and defaults")
class EntityConstructionTest {

    // ── Bid ───────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Bid defaults: status=PENDING, isRecommended=false")
    void bidDefaults() {
        Bid bid = Bid.builder()
                .amount(new BigDecimal("5000.00"))
                .build();
        assertThat(bid.getStatus()).isEqualTo(Bid.BidStatus.PENDING);
        assertThat(bid.getIsRecommended()).isFalse();
        assertThat(bid.getAmount()).isInstanceOf(BigDecimal.class);
    }

    @Test
    @DisplayName("Bid amount is BigDecimal — not float/double")
    void bidAmountIsBigDecimal() {
        BigDecimal amount = new BigDecimal("12345.67");
        Bid bid = Bid.builder().amount(amount).build();
        // Exact value preserved — no floating-point imprecision
        assertThat(bid.getAmount()).isEqualByComparingTo(new BigDecimal("12345.67"));
    }

    @Test
    @DisplayName("Bid has all 5 status values including COUNTERED and EXPIRED")
    void bidStatusValuesComplete() {
        assertThat(Bid.BidStatus.values()).containsExactlyInAnyOrder(
                Bid.BidStatus.PENDING,
                Bid.BidStatus.ACCEPTED,
                Bid.BidStatus.REJECTED,
                Bid.BidStatus.EXPIRED,
                Bid.BidStatus.COUNTERED
        );
    }

    // ── Booking ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Booking defaults: status=PENDING, commissionFee=ZERO")
    void bookingDefaults() {
        Booking booking = Booking.builder().build();
        assertThat(booking.getBookingStatus()).isEqualTo(Booking.BookingStatus.PENDING);
        assertThat(booking.getCommissionFee()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Booking has all 6 status values including DELIVERED and CANCELLED")
    void bookingStatusValuesComplete() {
        assertThat(Booking.BookingStatus.values()).containsExactlyInAnyOrder(
                Booking.BookingStatus.PENDING,
                Booking.BookingStatus.ACCEPTED,
                Booking.BookingStatus.DELIVERED,
                Booking.BookingStatus.COMPLETED,
                Booking.BookingStatus.CONFIRMED,
                Booking.BookingStatus.CANCELLED
        );
    }

    // ── Trip ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Trip defaults: status=ONGOING, type=SINGLE, stops=1/0, routeData=empty list")
    void tripDefaults() {
        Trip trip = Trip.builder()
                .startLocation("Addis Ababa")
                .destination("Hawassa")
                .build();
        assertThat(trip.getTripStatus()).isEqualTo(Trip.TripStatus.ONGOING);
        assertThat(trip.getTripType()).isEqualTo(Trip.TripType.SINGLE);
        assertThat(trip.getTotalStops()).isEqualTo(1);
        assertThat(trip.getCompletedStops()).isEqualTo(0);
        assertThat(trip.getRouteData()).isNotNull().isEmpty();
        assertThat(trip.getTripStops()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("Trip.isMultiStop() returns true only for MULTI_STOP type")
    void isMultiStop() {
        Trip single = Trip.builder().tripType(Trip.TripType.SINGLE).build();
        Trip multi = Trip.builder().tripType(Trip.TripType.MULTI_STOP).build();
        assertThat(single.isMultiStop()).isFalse();
        assertThat(multi.isMultiStop()).isTrue();
    }

    @Test
    @DisplayName("Trip.getTotalAmount() sums stop agreed prices")
    void tripTotalAmountSumsStops() {
        TripStop s1 = TripStop.builder().agreedPrice(new BigDecimal("3000.00")).build();
        TripStop s2 = TripStop.builder().agreedPrice(new BigDecimal("2500.00")).build();
        Trip trip = Trip.builder().build();
        trip.getTripStops().add(s1);
        trip.getTripStops().add(s2);
        assertThat(trip.getTotalAmount()).isEqualByComparingTo(new BigDecimal("5500.00"));
    }

    // ── TripStop ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TripStop defaults: status=PENDING, agreedPrice=ZERO")
    void tripStopDefaults() {
        TripStop stop = TripStop.builder().locationName("Hawassa").stopOrder(1).build();
        assertThat(stop.getStopStatus()).isEqualTo(TripStop.StopStatus.PENDING);
        assertThat(stop.getAgreedPrice()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("TripStop has all 4 status values")
    void tripStopStatusValues() {
        assertThat(TripStop.StopStatus.values()).containsExactlyInAnyOrder(
                TripStop.StopStatus.PENDING,
                TripStop.StopStatus.ARRIVED,
                TripStop.StopStatus.LOADED,
                TripStop.StopStatus.COMPLETED
        );
    }

    // ── Payment ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Payment defaults: status=PENDING, amounts=ZERO, method=cash")
    void paymentDefaults() {
        Payment payment = Payment.builder()
                .amount(new BigDecimal("10000.00"))
                .build();
        assertThat(payment.getPaymentStatus()).isEqualTo(Payment.PaymentStatus.PENDING);
        assertThat(payment.getCommissionAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(payment.getDriverNetAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(payment.getPaymentMethod()).isEqualTo("cash");
    }

    @Test
    @DisplayName("Payment amount is BigDecimal — supports large ETB values without precision loss")
    void paymentAmountIsBigDecimal() {
        // ETB 9,999,999,999,999.99 — maximum supported value (DECIMAL 15,2)
        BigDecimal largeAmount = new BigDecimal("9999999999999.99");
        Payment payment = Payment.builder().amount(largeAmount).build();
        assertThat(payment.getAmount()).isEqualByComparingTo(largeAmount);
    }

    // ── Rating ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Rating can be built with rating value 1–5")
    void ratingConstruction() {
        Rating rating = Rating.builder().rating(5).feedback("Excellent!").build();
        assertThat(rating.getRating()).isEqualTo(5);
        assertThat(rating.getFeedback()).isEqualTo("Excellent!");
    }

    // ── DriverDocument ────────────────────────────────────────────────────────

    @Test
    @DisplayName("DriverDocument defaults: status=PENDING")
    void driverDocumentDefaults() {
        DriverDocument doc = DriverDocument.builder()
                .documentType(DriverDocument.DocumentType.LICENSE)
                .filePath("/uploads/driver/1/license.pdf")
                .originalName("license.pdf")
                .build();
        assertThat(doc.getStatus()).isEqualTo(DriverDocument.DocumentStatus.PENDING);
    }

    @Test
    @DisplayName("DriverDocument has exactly 5 document types")
    void documentTypesComplete() {
        assertThat(DriverDocument.DocumentType.values()).hasSize(5);
        assertThat(DriverDocument.DocumentType.values()).containsExactlyInAnyOrder(
                DriverDocument.DocumentType.LICENSE,
                DriverDocument.DocumentType.NATIONAL_ID,
                DriverDocument.DocumentType.VEHICLE_REGISTRATION,
                DriverDocument.DocumentType.INSURANCE,
                DriverDocument.DocumentType.TIN
        );
    }

    @Test
    @DisplayName("All DocumentType labels are non-blank")
    void documentTypeLabels() {
        for (DriverDocument.DocumentType type : DriverDocument.DocumentType.values()) {
            assertThat(type.getLabel())
                    .as("Label for %s", type)
                    .isNotBlank();
        }
    }

    // ── BackhaulRecommendation ────────────────────────────────────────────────

    @Test
    @DisplayName("BackhaulRecommendation defaults: status=PENDING, score=ZERO")
    void backhaulDefaults() {
        BackhaulRecommendation rec = BackhaulRecommendation.builder().build();
        assertThat(rec.getStatus()).isEqualTo(BackhaulRecommendation.BackhaulStatus.PENDING);
        assertThat(rec.getScore()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(rec.getMetadata()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("BackhaulStatus has all 4 values including BID_PLACED")
    void backhaulStatusValues() {
        assertThat(BackhaulRecommendation.BackhaulStatus.values()).containsExactlyInAnyOrder(
                BackhaulRecommendation.BackhaulStatus.PENDING,
                BackhaulRecommendation.BackhaulStatus.VIEWED,
                BackhaulRecommendation.BackhaulStatus.BID_PLACED,
                BackhaulRecommendation.BackhaulStatus.DISMISSED
        );
    }

    // ── AIRecommendation ──────────────────────────────────────────────────────

    @Test
    @DisplayName("AIRecommendation fields are BigDecimal")
    void aiRecommendationTypes() {
        AIRecommendation rec = AIRecommendation.builder()
                .recommendationScore(new BigDecimal("0.8500"))
                .predictedPrice(new BigDecimal("15000.00"))
                .backhaulProbability(new BigDecimal("0.3000"))
                .build();
        assertThat(rec.getRecommendationScore()).isInstanceOf(BigDecimal.class);
        assertThat(rec.getPredictedPrice()).isInstanceOf(BigDecimal.class);
        assertThat(rec.getBackhaulProbability()).isInstanceOf(BigDecimal.class);
    }

    // ── PlatformSetting ───────────────────────────────────────────────────────

    @Test
    @DisplayName("PlatformSetting can store and retrieve key-value pair")
    void platformSettingConstruction() {
        PlatformSetting setting = PlatformSetting.builder()
                .key("pricing.commission_rate")
                .value("0.10")
                .build();
        assertThat(setting.getKey()).isEqualTo("pricing.commission_rate");
        assertThat(setting.getValue()).isEqualTo("0.10");
    }

    // ── Freight new fields ────────────────────────────────────────────────────

    @Test
    @DisplayName("Freight new fields: service_type defaults to INTERCITY, price_type to NEGOTIABLE")
    void freightNewFieldDefaults() {
        Freight freight = Freight.builder()
                .pickupLocation("Addis Ababa")
                .destination("Hawassa")
                .materialType("grain")
                .weightTons(new BigDecimal("10.0"))
                .budget(new BigDecimal("5000.00"))
                .urgencyLevel("normal")
                .build();
        assertThat(freight.getServiceType()).isEqualTo(Freight.ServiceType.INTERCITY);
        assertThat(freight.getPriceType()).isEqualTo(Freight.PriceType.NEGOTIABLE);
        assertThat(freight.getBidDeadline()).isNull();
        assertThat(freight.getCity()).isNull();
    }

    @Test
    @DisplayName("Freight ServiceType has exactly 2 values")
    void freightServiceTypeValues() {
        assertThat(Freight.ServiceType.values()).containsExactlyInAnyOrder(
                Freight.ServiceType.INTERCITY,
                Freight.ServiceType.INTRACITY
        );
    }

    @Test
    @DisplayName("Freight PriceType has exactly 2 values")
    void freightPriceTypeValues() {
        assertThat(Freight.PriceType.values()).containsExactlyInAnyOrder(
                Freight.PriceType.NEGOTIABLE,
                Freight.PriceType.FIXED
        );
    }
}
