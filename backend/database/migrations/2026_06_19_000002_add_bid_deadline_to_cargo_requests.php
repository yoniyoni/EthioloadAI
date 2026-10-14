<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::table('cargo_requests', function (Blueprint $table) {
            $table->timestamp('bid_deadline')->nullable()->after('price_type');
        });
    }

    public function down(): void
    {
        Schema::table('cargo_requests', function (Blueprint $table) {
            $table->dropColumn('bid_deadline');
        });
    }
};
