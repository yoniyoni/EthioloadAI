<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::table('bookings', function (Blueprint $table) {
            $table->foreignId('bid_id')
                ->nullable()
                ->after('driver_id')
                ->constrained('bids')
                ->onDelete('set null');
        });
    }

    public function down(): void
    {
        Schema::table('bookings', function (Blueprint $table) {
            $table->dropForeign(['bid_id']);
            $table->dropColumn('bid_id');
        });
    }
};
