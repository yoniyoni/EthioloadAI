<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Support\Facades\DB;

return new class extends Migration
{
    /**
     * DB-level backstop for the accept-bid race: at most one non-cancelled
     * booking per cargo request. Partial index (not a plain unique column)
     * so a cargo can still be legitimately re-booked after a cancellation —
     * see BookingController::cancel(), which resets the cargo to 'pending'.
     */
    public function up(): void
    {
        DB::statement(
            "CREATE UNIQUE INDEX bookings_cargo_id_unique_active ON bookings (cargo_id) WHERE booking_status <> 'cancelled'"
        );
    }

    public function down(): void
    {
        DB::statement('DROP INDEX IF EXISTS bookings_cargo_id_unique_active');
    }
};
