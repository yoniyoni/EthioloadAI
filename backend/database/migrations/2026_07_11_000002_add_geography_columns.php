<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Support\Facades\DB;

return new class extends Migration
{
    public function up(): void
    {
        DB::statement('ALTER TABLE vehicles ADD COLUMN position geography(Point,4326)');
        DB::statement('ALTER TABLE cargo_requests ADD COLUMN pickup_point geography(Point,4326)');
        DB::statement('ALTER TABLE cargo_requests ADD COLUMN destination_point geography(Point,4326)');
    }

    public function down(): void
    {
        DB::statement('ALTER TABLE vehicles DROP COLUMN IF EXISTS position');
        DB::statement('ALTER TABLE cargo_requests DROP COLUMN IF EXISTS pickup_point');
        DB::statement('ALTER TABLE cargo_requests DROP COLUMN IF EXISTS destination_point');
    }
};
