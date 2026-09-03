<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Support\Facades\DB;

return new class extends Migration
{
    /**
     * New upfront-escrow flow: PaymentService::finalizeChapaPayment() now
     * sets booking_status = 'paid' (money verified, trip may start) instead
     * of jumping straight to 'completed' — that value never existed in the
     * CHECK constraint.
     */
    public function up(): void
    {
        DB::statement("ALTER TABLE bookings DROP CONSTRAINT IF EXISTS bookings_booking_status_check");
        DB::statement("ALTER TABLE bookings ADD CONSTRAINT bookings_booking_status_check CHECK (booking_status IN ('pending','accepted','delivered','completed','confirmed','cancelled','paid'))");
    }

    public function down(): void
    {
        DB::statement("UPDATE bookings SET booking_status = 'accepted' WHERE booking_status = 'paid'");
        DB::statement("ALTER TABLE bookings DROP CONSTRAINT IF EXISTS bookings_booking_status_check");
        DB::statement("ALTER TABLE bookings ADD CONSTRAINT bookings_booking_status_check CHECK (booking_status IN ('pending','accepted','delivered','completed','confirmed','cancelled'))");
    }
};
