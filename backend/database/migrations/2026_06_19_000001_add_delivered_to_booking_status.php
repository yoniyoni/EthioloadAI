<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Support\Facades\DB;

return new class extends Migration
{
    public function up(): void
    {
        // PostgreSQL: drop the old CHECK constraint and add a new one that includes 'delivered'
        DB::statement("ALTER TABLE bookings DROP CONSTRAINT IF EXISTS bookings_booking_status_check");
        DB::statement("ALTER TABLE bookings ADD CONSTRAINT bookings_booking_status_check CHECK (booking_status IN ('pending','accepted','delivered','completed','confirmed'))");
    }

    public function down(): void
    {
        DB::statement("UPDATE bookings SET booking_status = 'accepted' WHERE booking_status = 'delivered'");
        DB::statement("ALTER TABLE bookings DROP CONSTRAINT IF EXISTS bookings_booking_status_check");
        DB::statement("ALTER TABLE bookings ADD CONSTRAINT bookings_booking_status_check CHECK (booking_status IN ('pending','accepted','completed','confirmed'))");
    }
};
