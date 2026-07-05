<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::table('cargo_requests', function (Blueprint $table) {
            // 'negotiable' — drivers bid their own price; shipper reviews and accepts
            // 'fixed'      — shipper sets a hard price; driver accepts or moves on
            $table->enum('price_type', ['negotiable', 'fixed'])
                  ->default('negotiable')
                  ->after('budget');
        });
    }

    public function down(): void
    {
        Schema::table('cargo_requests', function (Blueprint $table) {
            $table->dropColumn('price_type');
        });
    }
};
