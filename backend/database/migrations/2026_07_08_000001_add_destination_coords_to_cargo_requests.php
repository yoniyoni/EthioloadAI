<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::table('cargo_requests', function (Blueprint $table) {
            $table->decimal('destination_lat', 10, 7)->nullable();
            $table->decimal('destination_lng', 10, 7)->nullable();
        });
    }

    public function down(): void
    {
        Schema::table('cargo_requests', function (Blueprint $table) {
            $table->dropColumn(['destination_lat', 'destination_lng']);
        });
    }
};
