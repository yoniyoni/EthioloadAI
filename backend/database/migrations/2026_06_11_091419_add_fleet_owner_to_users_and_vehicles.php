<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        // Add fleet_owner_id to users — a driver can belong to a fleet owner
        Schema::table('users', function (Blueprint $table) {
            $table->foreignId('fleet_owner_id')
                  ->nullable()
                  ->constrained('users')
                  ->nullOnDelete()
                  ->after('role');
        });

        // Add fleet_owner_id to vehicles — a vehicle can be owned by a fleet owner
        Schema::table('vehicles', function (Blueprint $table) {
            $table->foreignId('fleet_owner_id')
                  ->nullable()
                  ->constrained('users')
                  ->nullOnDelete()
                  ->after('user_id');
        });
    }

    public function down(): void
    {
        Schema::table('users', function (Blueprint $table) {
            $table->dropForeign(['fleet_owner_id']);
            $table->dropColumn('fleet_owner_id');
        });

        Schema::table('vehicles', function (Blueprint $table) {
            $table->dropForeign(['fleet_owner_id']);
            $table->dropColumn('fleet_owner_id');
        });
    }
};
