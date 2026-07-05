<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        // Add distance_km (counter columns already exist from a prior migration)
        Schema::table('bids', function (Blueprint $table) {
            $table->float('distance_km')->nullable()->after('is_recommended');
        });

        // Extend the status CHECK constraint to include 'countered'
        DB::statement('ALTER TABLE bids DROP CONSTRAINT IF EXISTS bids_status_check');
        DB::statement("ALTER TABLE bids ADD CONSTRAINT bids_status_check CHECK (status::text = ANY (ARRAY['pending'::text,'accepted'::text,'rejected'::text,'expired'::text,'countered'::text]))");

        // Swap unique constraint from per-driver → per-vehicle
        DB::statement('ALTER TABLE bids DROP CONSTRAINT IF EXISTS bids_cargo_request_id_driver_id_unique');
        Schema::table('bids', function (Blueprint $table) {
            $table->unique(['cargo_request_id', 'vehicle_id'], 'bids_cargo_request_id_vehicle_id_unique');
        });

        // Add pickup coordinates to cargo_requests
        Schema::table('cargo_requests', function (Blueprint $table) {
            $table->decimal('pickup_latitude', 10, 7)->nullable()->after('pickup_location');
            $table->decimal('pickup_longitude', 10, 7)->nullable()->after('pickup_latitude');
        });
    }

    public function down(): void
    {
        DB::statement('ALTER TABLE bids DROP CONSTRAINT IF EXISTS bids_status_check');
        DB::statement("ALTER TABLE bids ADD CONSTRAINT bids_status_check CHECK (status::text = ANY (ARRAY['pending'::text,'accepted'::text,'rejected'::text,'expired'::text]))");

        DB::statement('ALTER TABLE bids DROP CONSTRAINT IF EXISTS bids_cargo_request_id_vehicle_id_unique');
        Schema::table('bids', function (Blueprint $table) {
            $table->unique(['cargo_request_id', 'driver_id'], 'bids_cargo_request_id_driver_id_unique');
        });

        Schema::table('bids', function (Blueprint $table) {
            $table->dropColumn('distance_km');
        });

        Schema::table('cargo_requests', function (Blueprint $table) {
            $table->dropColumn(['pickup_latitude', 'pickup_longitude']);
        });
    }
};
