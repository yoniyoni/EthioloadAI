<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Support\Facades\DB;

return new class extends Migration
{
    public function up(): void
    {
        // Change default from 1 → 0 so new trips start with 0 explicit stops.
        // A single-destination trip has 0 stops in trip_stops; multi_stop means
        // the driver explicitly added waypoints, each incrementing total_stops.
        DB::statement('ALTER TABLE trips ALTER COLUMN total_stops SET DEFAULT 0');

        // Fix any existing trips so total_stops matches actual trip_stops count.
        DB::statement('UPDATE trips SET total_stops = (SELECT COUNT(*) FROM trip_stops WHERE trip_id = trips.id)');
    }

    public function down(): void
    {
        DB::statement('ALTER TABLE trips ALTER COLUMN total_stops SET DEFAULT 1');
    }
};
