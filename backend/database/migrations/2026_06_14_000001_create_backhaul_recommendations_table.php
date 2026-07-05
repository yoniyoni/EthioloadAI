<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('backhaul_recommendations', function (Blueprint $table) {
            $table->id();
            $table->foreignId('trip_id')->constrained('trips')->onDelete('cascade');
            $table->foreignId('driver_id')->constrained('users')->onDelete('cascade');
            $table->foreignId('cargo_request_id')->constrained('cargo_requests')->onDelete('cascade');
            $table->decimal('score', 4, 3)->default(0);
            $table->enum('status', ['pending', 'viewed', 'bid_placed', 'dismissed'])->default('pending');
            $table->jsonb('metadata')->default('{}');
            $table->timestamps();

            // Prevent duplicates; updateOrCreate uses this constraint
            $table->unique(['trip_id', 'driver_id', 'cargo_request_id'], 'backhaul_unique');
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('backhaul_recommendations');
    }
};
