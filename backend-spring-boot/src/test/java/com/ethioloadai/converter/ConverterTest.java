package com.ethioloadai.converter;

import com.ethioloadai.freight.converter.FreightStatusConverter;
import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.user.converter.RoleConverter;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.vehicle.converter.VehicleCategoryConverter;
import com.ethioloadai.vehicle.entity.Vehicle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for JPA attribute converters.
 *
 * Covers:
 * - Fix 3 (verified correct): RoleConverter writes lowercase.
 * - Fix 4: VehicleCategoryConverter writes lowercase, matching the DB CHECK constraint.
 * - Fix 4 (bonus): FreightStatusConverter writes lowercase, matching the DB CHECK constraint.
 */
@DisplayName("JPA Attribute Converters")
class ConverterTest {

    // ── RoleConverter (Fix 3 — verified correct, tested for regression) ──────

    @Nested
    @DisplayName("RoleConverter")
    class RoleConverterTests {

        private final RoleConverter converter = new RoleConverter();

        @ParameterizedTest(name = "Role.{0} → lowercase DB value")
        @EnumSource(User.Role.class)
        @DisplayName("all Role enum values are persisted as lowercase")
        void allRolesPersistedAsLowercase(User.Role role) {
            String dbValue = converter.convertToDatabaseColumn(role);
            assertThat(dbValue).isEqualTo(role.name().toLowerCase());
        }

        @Test
        @DisplayName("null role converts to null DB value")
        void nullRoleConvertsToNull() {
            assertThat(converter.convertToDatabaseColumn(null)).isNull();
        }

        @Test
        @DisplayName("lowercase DB value 'shipper' reads back as SHIPPER")
        void lowercaseDbValueReadsBack() {
            assertThat(converter.convertToEntityAttribute("shipper")).isEqualTo(User.Role.SHIPPER);
        }

        @Test
        @DisplayName("all lowercase DB values read back to the correct enum constant")
        void allLowercaseValuesReadBack() {
            for (User.Role role : User.Role.values()) {
                assertThat(converter.convertToEntityAttribute(role.name().toLowerCase()))
                        .isEqualTo(role);
            }
        }

        @Test
        @DisplayName("invalid DB value throws IllegalArgumentException")
        void invalidDbValueThrows() {
            assertThatThrownBy(() -> converter.convertToEntityAttribute("unknown_role"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ── VehicleCategoryConverter (Fix 4) ────────────────────────────────────

    @Nested
    @DisplayName("VehicleCategoryConverter")
    class VehicleCategoryConverterTests {

        private final VehicleCategoryConverter converter = new VehicleCategoryConverter();

        @Test
        @DisplayName("HEAVY → 'heavy' (matches DB CHECK constraint)")
        void heavyWritesLowercase() {
            assertThat(converter.convertToDatabaseColumn(Vehicle.VehicleCategory.HEAVY))
                    .isEqualTo("heavy");
        }

        @Test
        @DisplayName("LIGHT → 'light' (matches DB CHECK constraint)")
        void lightWritesLowercase() {
            assertThat(converter.convertToDatabaseColumn(Vehicle.VehicleCategory.LIGHT))
                    .isEqualTo("light");
        }

        @Test
        @DisplayName("null category converts to null DB value")
        void nullCategoryConvertsToNull() {
            assertThat(converter.convertToDatabaseColumn(null)).isNull();
        }

        @Test
        @DisplayName("'heavy' DB value reads back as HEAVY")
        void heavyDbValueReadsBack() {
            assertThat(converter.convertToEntityAttribute("heavy"))
                    .isEqualTo(Vehicle.VehicleCategory.HEAVY);
        }

        @Test
        @DisplayName("'light' DB value reads back as LIGHT")
        void lightDbValueReadsBack() {
            assertThat(converter.convertToEntityAttribute("light"))
                    .isEqualTo(Vehicle.VehicleCategory.LIGHT);
        }

        @Test
        @DisplayName("null DB value reads back as null")
        void nullDbValueReadsBackAsNull() {
            assertThat(converter.convertToEntityAttribute(null)).isNull();
        }

        @Test
        @DisplayName("blank DB value reads back as null")
        void blankDbValueReadsBackAsNull() {
            assertThat(converter.convertToEntityAttribute("")).isNull();
            assertThat(converter.convertToEntityAttribute("   ")).isNull();
        }

        @Test
        @DisplayName("invalid DB value throws IllegalArgumentException")
        void invalidDbValueThrows() {
            // 'medium' is not a valid VehicleCategory
            assertThatThrownBy(() -> converter.convertToEntityAttribute("medium"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("unrecognised DB value throws IllegalArgumentException")
        void unrecognisedDbValueThrows() {
            assertThatThrownBy(() -> converter.convertToEntityAttribute("truck"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("truck");
        }
    }

    // ── FreightStatusConverter (Fix 4 — bonus) ───────────────────────────────

    @Nested
    @DisplayName("FreightStatusConverter")
    class FreightStatusConverterTests {

        private final FreightStatusConverter converter = new FreightStatusConverter();

        @ParameterizedTest(name = "FreightStatus.{0} → lowercase DB value")
        @EnumSource(Freight.FreightStatus.class)
        @DisplayName("all FreightStatus enum values are persisted as lowercase")
        void allStatusesPersistedAsLowercase(Freight.FreightStatus status) {
            String dbValue = converter.convertToDatabaseColumn(status);
            assertThat(dbValue).isEqualTo(status.name().toLowerCase());
        }

        @Test
        @DisplayName("POSTED → 'posted' (matches DB CHECK constraint)")
        void postedWritesLowercase() {
            assertThat(converter.convertToDatabaseColumn(Freight.FreightStatus.POSTED))
                    .isEqualTo("posted");
        }

        @Test
        @DisplayName("IN_TRANSIT → 'in_transit' (matches DB CHECK constraint)")
        void inTransitWritesLowercase() {
            assertThat(converter.convertToDatabaseColumn(Freight.FreightStatus.IN_TRANSIT))
                    .isEqualTo("in_transit");
        }

        @Test
        @DisplayName("null status converts to null DB value")
        void nullStatusConvertsToNull() {
            assertThat(converter.convertToDatabaseColumn(null)).isNull();
        }

        @Test
        @DisplayName("all lowercase DB values read back to the correct enum constant")
        void allLowercaseValuesReadBack() {
            for (Freight.FreightStatus status : Freight.FreightStatus.values()) {
                assertThat(converter.convertToEntityAttribute(status.name().toLowerCase()))
                        .isEqualTo(status);
            }
        }

        @Test
        @DisplayName("invalid DB value throws IllegalArgumentException")
        void invalidDbValueThrows() {
            assertThatThrownBy(() -> converter.convertToEntityAttribute("unknown_status"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
