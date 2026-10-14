<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('trip_stops', function (Blueprint $table) {
            $table->id();
            $table->foreignId('trip_id')->constrained('trips')->onDelete('cascade');
            $table->foreignId('cargo_request_id')->nullable()->constrained('cargo_requests')->nullOnDelete();
            $table->unsignedInteger('stop_order');
            $table->string('location_name');
            $table->decimal('pickup_lat', 10, 8)->nullable();
            $table->decimal('pickup_lng', 11, 8)->nullable();
            $table->decimal('agreed_price', 10, 2);
            $table->enum('status', ['pending', 'arrived', 'loaded', 'completed'])->default('pending');
            $table->text('notes')->nullable();
            $table->timestamp('arrived_at')->nullable();
            $table->timestamp('completed_at')->nullable();
            $table->timestamps();

            $table->index(['trip_id', 'stop_order']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('trip_stops');
    }
};
