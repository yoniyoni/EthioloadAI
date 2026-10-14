<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::table('trips', function (Blueprint $table) {
            $table->string('trip_type', 10)->default('single');
            $table->unsignedInteger('total_stops')->default(1);
            $table->unsignedInteger('completed_stops')->default(0);
        });

        DB::statement("ALTER TABLE trips ADD CONSTRAINT trips_trip_type_check CHECK (trip_type IN ('single','multi_stop'))");
    }

    public function down(): void
    {
        DB::statement("ALTER TABLE trips DROP CONSTRAINT IF EXISTS trips_trip_type_check");

        Schema::table('trips', function (Blueprint $table) {
            $table->dropColumn(['trip_type', 'total_stops', 'completed_stops']);
        });
    }
};
