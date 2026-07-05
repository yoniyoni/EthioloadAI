<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Support\Facades\DB;

return new class extends Migration
{
    /**
     * PostgreSQL enums cannot be altered with Laravel's Schema builder.
     * We use raw SQL to add the new value to the existing enum type.
     */
    public function up(): void
    {
        // Add 'fleet_owner' to the users_role_check constraint on PostgreSQL
        DB::statement("ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check");
        DB::statement("ALTER TABLE users ADD CONSTRAINT users_role_check CHECK (role IN ('shipper', 'driver', 'admin', 'fleet_owner'))");
    }

    public function down(): void
    {
        DB::statement("ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check");
        DB::statement("ALTER TABLE users ADD CONSTRAINT users_role_check CHECK (role IN ('shipper', 'driver', 'admin'))");
    }
};
