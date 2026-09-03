<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Support\Facades\DB;

return new class extends Migration
{
    /**
     * Relaxes the single-active-booking-per-vehicle backstop (see
     * 2026_07_29_000004_add_unique_active_booking_per_vehicle.php) from a
     * cap of 1 to a cap of 2 — a driver may now hold one active (mid-trip)
     * booking plus one queued (accepted/paid but not yet started) booking
     * on the same vehicle. TripService::startTrip() is what actually stops
     * a vehicle driving two loads at once; this is just the DB-level
     * last-line-of-defense for booking *creation*, matching the app-level
     * checks in BidService::assertVehicleFree(), CargoRequestController's
     * direct-booking flow, and FleetController::createBooking().
     *
     * A plain partial unique index can only express "at most 1 row", so
     * cap-of-2 needs a trigger instead.
     */
    public function up(): void
    {
        DB::statement('DROP INDEX IF EXISTS bookings_vehicle_id_unique_active');

        DB::unprepared(<<<'SQL'
            CREATE OR REPLACE FUNCTION check_vehicle_active_booking_limit() RETURNS trigger AS $$
            BEGIN
                IF NEW.booking_status NOT IN ('cancelled', 'completed', 'delivered') THEN
                    IF (
                        SELECT COUNT(*) FROM bookings
                        WHERE vehicle_id = NEW.vehicle_id
                          AND booking_status NOT IN ('cancelled', 'completed', 'delivered')
                          AND id <> NEW.id
                    ) >= 2 THEN
                        RAISE EXCEPTION 'This vehicle already has an active trip and a queued one.';
                    END IF;
                END IF;
                RETURN NEW;
            END;
            $$ LANGUAGE plpgsql;
        SQL);

        DB::unprepared(<<<'SQL'
            CREATE TRIGGER bookings_active_limit_trigger
            BEFORE INSERT OR UPDATE ON bookings
            FOR EACH ROW EXECUTE FUNCTION check_vehicle_active_booking_limit();
        SQL);
    }

    public function down(): void
    {
        DB::unprepared('DROP TRIGGER IF EXISTS bookings_active_limit_trigger ON bookings');
        DB::unprepared('DROP FUNCTION IF EXISTS check_vehicle_active_booking_limit()');

        DB::statement(
            "CREATE UNIQUE INDEX bookings_vehicle_id_unique_active ON bookings (vehicle_id) WHERE booking_status NOT IN ('cancelled', 'completed', 'delivered')"
        );
    }
};
