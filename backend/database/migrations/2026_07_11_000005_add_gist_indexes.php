<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Support\Facades\DB;

return new class extends Migration
{
    public function up(): void
    {
        DB::statement('CREATE INDEX vehicles_position_gist ON vehicles USING GIST (position)');
        DB::statement('CREATE INDEX cargo_requests_pickup_point_gist ON cargo_requests USING GIST (pickup_point)');
        DB::statement('CREATE INDEX cargo_requests_destination_point_gist ON cargo_requests USING GIST (destination_point)');
    }

    public function down(): void
    {
        DB::statement('DROP INDEX IF EXISTS vehicles_position_gist');
        DB::statement('DROP INDEX IF EXISTS cargo_requests_pickup_point_gist');
        DB::statement('DROP INDEX IF EXISTS cargo_requests_destination_point_gist');
    }
};
