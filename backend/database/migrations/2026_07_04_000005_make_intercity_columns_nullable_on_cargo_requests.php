<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::table('cargo_requests', function (Blueprint $table) {
            $table->string('pickup_location')->nullable()->change();
            $table->string('destination')->nullable()->change();
            $table->string('material_type')->nullable()->change();
            $table->float('weight')->nullable()->change();
            $table->string('urgency_level')->nullable()->change();
        });
    }

    public function down(): void
    {
        Schema::table('cargo_requests', function (Blueprint $table) {
            $table->string('pickup_location')->nullable(false)->change();
            $table->string('destination')->nullable(false)->change();
            $table->string('material_type')->nullable(false)->change();
            $table->float('weight')->nullable(false)->change();
            $table->string('urgency_level')->nullable(false)->change();
        });
    }
};
