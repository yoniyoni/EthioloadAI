<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Support\Facades\DB;

return new class extends Migration
{
    public function up(): void
    {
        DB::table('platform_settings')->insert([
            ['key' => 'spatial.nearby_trucks_radius_km',  'value' => '50',  'created_at' => now(), 'updated_at' => now()],
            ['key' => 'spatial.nearby_drivers_radius_km', 'value' => '50',  'created_at' => now(), 'updated_at' => now()],
            ['key' => 'spatial.cargo_search_radius_km',   'value' => '50',  'created_at' => now(), 'updated_at' => now()],
            ['key' => 'spatial.fleet_dispatch_radius_km', 'value' => '200', 'created_at' => now(), 'updated_at' => now()],
            ['key' => 'spatial.backhaul_radius_km',       'value' => '200', 'created_at' => now(), 'updated_at' => now()],
        ]);
    }

    public function down(): void
    {
        DB::table('platform_settings')->where('key', 'like', 'spatial.%')->delete();
    }
};
