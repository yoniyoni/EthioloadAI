<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::table('cargo_requests', function (Blueprint $table) {
            $table->enum('service_type', ['intercity', 'intracity'])
                  ->default('intercity')
                  ->after('bid_deadline');

            $table->string('city')->nullable()->after('service_type');
            $table->string('pickup_area')->nullable()->after('city');
            $table->string('dropoff_area')->nullable()->after('pickup_area');
            $table->date('preferred_date')->nullable()->after('dropoff_area');
            $table->text('items_description')->nullable()->after('preferred_date');
            $table->string('vehicle_type_needed')->nullable()->after('items_description');
        });
    }

    public function down(): void
    {
        Schema::table('cargo_requests', function (Blueprint $table) {
            $table->dropColumn([
                'service_type', 'city', 'pickup_area', 'dropoff_area',
                'preferred_date', 'items_description', 'vehicle_type_needed',
            ]);
        });
    }
};
