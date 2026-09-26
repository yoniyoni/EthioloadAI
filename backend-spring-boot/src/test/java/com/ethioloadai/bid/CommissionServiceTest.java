package com.ethioloadai.bid;

import com.ethioloadai.bid.service.CommissionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.assertj.core.api.Assertions.*;

class CommissionServiceTest {

    private CommissionServiceImpl commissionService;

    @BeforeEach
    void setUp() {
        commissionService = new CommissionServiceImpl();
    }

    @Test
    @DisplayName("Should calculate standard commission at 10%")
    void testStandardCommission() {
        // Given
        BigDecimal amount = new BigDecimal("1000.00");
        boolean isFleetBooking = false;

        // When
        BigDecimal commission = commissionService.calculateCommission(amount, isFleetBooking);

        // Then
        assertThat(commission).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("Should calculate fleet commission at 5%")
    void testFleetCommission() {
        // Given
        BigDecimal amount = new BigDecimal("1000.00");
        boolean isFleetBooking = true;

        // When
        BigDecimal commission = commissionService.calculateCommission(amount, isFleetBooking);

        // Then
        assertThat(commission).isEqualByComparingTo(new BigDecimal("50.00"));
    }

    @Test
    @DisplayName("Should handle decimal amounts correctly")
    void testDecimalAmounts() {
        // Given
        BigDecimal amount = new BigDecimal("1234.56");
        
        // When - standard commission
        BigDecimal standardCommission = commissionService.calculateCommission(amount, false);
        
        // Then
        BigDecimal expected = new BigDecimal("1234.56")
                .multiply(new BigDecimal("0.10"))
                .setScale(2, RoundingMode.HALF_UP);
        assertThat(standardCommission).isEqualByComparingTo(expected);

        // When - fleet commission
        BigDecimal fleetCommission = commissionService.calculateCommission(amount, true);
        
        // Then
        BigDecimal expectedFleet = new BigDecimal("1234.56")
                .multiply(new BigDecimal("0.05"))
                .setScale(2, RoundingMode.HALF_UP);
        assertThat(fleetCommission).isEqualByComparingTo(expectedFleet);
    }

    @Test
    @DisplayName("Should handle edge cases")
    void testEdgeCases() {
        // Null amount
        assertThat(commissionService.calculateCommission(null, false))
                .isEqualByComparingTo(BigDecimal.ZERO);

        // Zero amount
        assertThat(commissionService.calculateCommission(BigDecimal.ZERO, false))
                .isEqualByComparingTo(BigDecimal.ZERO);

        // Negative amount
        assertThat(commissionService.calculateCommission(new BigDecimal("-100.00"), false))
                .isEqualByComparingTo(BigDecimal.ZERO);

        // Very small amount
        BigDecimal smallAmount = new BigDecimal("0.01");
        BigDecimal smallCommission = commissionService.calculateCommission(smallAmount, false);
        assertThat(smallCommission).isEqualByComparingTo(new BigDecimal("0.00"));
    }

    @Test
    @DisplayName("Should return correct commission rates")
    void testCommissionRates() {
        // Standard rate
        assertThat(commissionService.getCommissionRate(false))
                .isEqualByComparingTo(new BigDecimal("0.10"));

        // Fleet rate  
        assertThat(commissionService.getCommissionRate(true))
                .isEqualByComparingTo(new BigDecimal("0.05"));
    }

    @Test
    @DisplayName("Should round commission correctly")
    void testCommissionRounding() {
        // Test rounding scenarios
        BigDecimal amount1 = new BigDecimal("33.33"); // 10% = 3.333
        BigDecimal commission1 = commissionService.calculateCommission(amount1, false);
        assertThat(commission1).isEqualByComparingTo(new BigDecimal("3.33"));

        BigDecimal amount2 = new BigDecimal("66.67"); // 10% = 6.667  
        BigDecimal commission2 = commissionService.calculateCommission(amount2, false);
        assertThat(commission2).isEqualByComparingTo(new BigDecimal("6.67"));

        BigDecimal amount3 = new BigDecimal("33.35"); // 10% = 3.335
        BigDecimal commission3 = commissionService.calculateCommission(amount3, false);
        assertThat(commission3).isEqualByComparingTo(new BigDecimal("3.34"));
    }

    @Test
    @DisplayName("Should match Laravel commission calculation exactly")
    void testLaravelCompatibility() {
        // Laravel uses: round((float) $amount * 0.10, 2)
        // Test common scenarios to ensure compatibility

        BigDecimal[] testAmounts = {
                new BigDecimal("500.00"),
                new BigDecimal("750.50"), 
                new BigDecimal("1234.56"),
                new BigDecimal("2000.00"),
                new BigDecimal("999.99")
        };

        for (BigDecimal amount : testAmounts) {
            BigDecimal commission = commissionService.calculateCommission(amount, false);
            
            // Should match PHP round($amount * 0.10, 2)
            BigDecimal expected = amount.multiply(new BigDecimal("0.10"))
                    .setScale(2, RoundingMode.HALF_UP);
            
            assertThat(commission)
                    .isEqualByComparingTo(expected)
                    .describedAs("Commission for amount %s should match Laravel calculation", amount);
        }
    }
}