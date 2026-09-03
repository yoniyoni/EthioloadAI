<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Support\Facades\DB;

return new class extends Migration
{
    public function up(): void
    {
        // CRITICAL: ST_MakePoint takes (longitude, latitude) — NOT (latitude, longitude).
        DB::statement("UPDATE vehicles SET position = ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)::geography WHERE latitude IS NOT NULL AND longitude IS NOT NULL");
        DB::statement("UPDATE cargo_requests SET pickup_point = ST_SetSRID(ST_MakePoint(pickup_lng, pickup_lat), 4326)::geography WHERE pickup_lat IS NOT NULL AND pickup_lng IS NOT NULL");
        DB::statement("UPDATE cargo_requests SET destination_point = ST_SetSRID(ST_MakePoint(destination_lng, destination_lat), 4326)::geography WHERE destination_lat IS NOT NULL AND destination_lng IS NOT NULL");
    }

    public function down(): void
    {
        // No-op: the columns themselves are dropped by step 2's down().
        // Setting back to NULL explicitly anyway, in case this migration is
        // ever rolled back independently of the column-adding migration.
        DB::statement("UPDATE vehicles SET position = NULL");
        DB::statement("UPDATE cargo_requests SET pickup_point = NULL, destination_point = NULL");
    }
};
