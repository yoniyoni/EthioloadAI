<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Support\Facades\DB;

return new class extends Migration
{
    /**
     * BookingController::cancel() sets booking_status = 'cancelled', but that
     * value was never added to the CHECK constraint — cancelling a booking
     * currently throws a DB error instead of succeeding.
     */
    public function up(): void
    {
        DB::statement("ALTER TABLE bookings DROP CONSTRAINT IF EXISTS bookings_booking_status_check");
        DB::statement("ALTER TABLE bookings ADD CONSTRAINT bookings_booking_status_check CHECK (booking_status IN ('pending','accepted','delivered','completed','confirmed','cancelled'))");
    }

    public function down(): void
    {
        DB::statement("UPDATE bookings SET booking_status = 'accepted' WHERE booking_status = 'cancelled'");
        DB::statement("ALTER TABLE bookings DROP CONSTRAINT IF EXISTS bookings_booking_status_check");
        DB::statement("ALTER TABLE bookings ADD CONSTRAINT bookings_booking_status_check CHECK (booking_status IN ('pending','accepted','delivered','completed','confirmed'))");
    }
};
