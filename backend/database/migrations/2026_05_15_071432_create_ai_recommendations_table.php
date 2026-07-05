<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    /**
     * Run the migrations.
     */
    public function up(): void
    {
        Schema::create('ai_recommendations', function (Blueprint $table) {
            $table->id();
            $table->timestamps();
            $table->foreignId('cargo_request_id')->constrained()->onDelete('cascade');
            $table->foreignId('vehicle_id')->constrained()->onDelete('cascade');
            $table->decimal('recommendation_score', 5, 2);
            $table->decimal('predicted_price', 10, 2);
            $table->decimal('backhaul_probability', 5, 2);
        });
    }

    /**
     * Reverse the migrations.
     */
    public function down(): void
    {
        Schema::dropIfExists('ai_recommendations');
    }
};
