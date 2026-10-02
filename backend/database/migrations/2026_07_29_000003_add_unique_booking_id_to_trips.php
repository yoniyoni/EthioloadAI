<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    /**
     * DB-level backstop for the start-trip race (mirrors the bookings.cargo_id
     * fix): a booking can only ever have one trip.
     */
    public function up(): void
    {
        Schema::table('trips', function (Blueprint $table) {
            $table->unique('booking_id');
        });
    }

    public function down(): void
    {
        Schema::table('trips', function (Blueprint $table) {
            $table->dropUnique(['booking_id']);
        });
    }
};
