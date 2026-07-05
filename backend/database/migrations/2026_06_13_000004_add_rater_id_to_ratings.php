<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::table('ratings', function (Blueprint $table) {
            // Tracks who submitted this specific rating (shipper or driver).
            // Allows two ratings per booking — one from each side.
            $table->foreignId('rater_id')->nullable()->constrained('users')->nullOnDelete()->after('driver_id');
        });
    }

    public function down(): void
    {
        Schema::table('ratings', function (Blueprint $table) {
            $table->dropForeign(['rater_id']);
            $table->dropColumn('rater_id');
        });
    }
};
