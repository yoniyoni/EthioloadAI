package com.ethioloadai.trip;

import com.ethioloadai.trip.entity.Trip;
import jakarta.persistence.Column;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TripStatusSchemaTest {
    @Test
    void everyLifecycleStateFitsThePersistedColumn() throws Exception {
        int length = Trip.class.getDeclaredField("tripStatus").getAnnotation(Column.class).length();
        for (Trip.TripStatus status : Trip.TripStatus.values()) {
            assertTrue(status.name().length() <= length, () -> "Status exceeds database column: " + status);
        }
    }
}
