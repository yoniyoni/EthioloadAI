<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Support\Facades\DB;

/**
 * cargo_requests.destination_point (and its GiST index) were added in the
 * Phase 1 PostGIS foundation, but no query in the codebase ever came to
 * filter/sort by it — every migrated "nearby" endpoint searches by
 * pickup_point only. The index still costs write-time maintenance on every
 * cargo_requests insert/update with zero query benefit today. Dropping the
 * index only; the column and its sync trigger stay, ready for a future
 * destination-based search feature without needing a new migration.
 */
return new class extends Migration
{
    public function up(): void
    {
        DB::statement('DROP INDEX IF EXISTS cargo_requests_destination_point_gist');
    }

    public function down(): void
    {
        DB::statement('CREATE INDEX cargo_requests_destination_point_gist ON cargo_requests USING GIST (destination_point)');
    }
};
