<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Support\Facades\DB;

return new class extends Migration
{
    /**
     * DB-level backstop: a vehicle can only have one active (not
     * cancelled/completed/delivered) booking at a time. Without this, nothing
     * stopped the same truck being committed to two trips simultaneously —
     * acceptBid/acceptCounter/bookDirect/createBooking now also check this
     * under a row lock, but this is the last line of defense if any future
     * code path creates a Booking directly.
     */
    public function up(): void
    {
        DB::statement(
            "CREATE UNIQUE INDEX bookings_vehicle_id_unique_active ON bookings (vehicle_id) WHERE booking_status NOT IN ('cancelled', 'completed', 'delivered')"
        );
    }

    public function down(): void
    {
        DB::statement('DROP INDEX IF EXISTS bookings_vehicle_id_unique_active');
    }
};
