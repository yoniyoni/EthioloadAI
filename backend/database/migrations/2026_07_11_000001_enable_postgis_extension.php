<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Support\Facades\DB;

return new class extends Migration
{
    public function up(): void
    {
        DB::statement('CREATE EXTENSION IF NOT EXISTS postgis');
    }

    public function down(): void
    {
        // Deliberately not dropping the extension in down() — other migrations in
        // this phase create geography columns that depend on it; dropping it here
        // would break rollback ordering. Extension removal, if ever needed, should
        // be a separate explicit operation after confirming nothing depends on it.
    }
};
