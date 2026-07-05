<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('bids', function (Blueprint $table) {
            $table->id();
            $table->foreignId('cargo_request_id')->constrained('cargo_requests')->onDelete('cascade');
            $table->foreignId('driver_id')->constrained('users')->onDelete('cascade');
            $table->foreignId('vehicle_id')->constrained('vehicles')->onDelete('cascade');
            $table->decimal('amount', 10, 2);
            $table->text('note')->nullable();
            $table->enum('status', ['pending', 'accepted', 'rejected', 'expired'])->default('pending');
            $table->float('ai_score')->nullable();
            $table->boolean('is_recommended')->default(false);
            $table->timestamps();

            // One driver can bid once per cargo request
            $table->unique(['cargo_request_id', 'driver_id']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('bids');
    }
};
