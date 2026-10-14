<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Support\Facades\DB;

return new class extends Migration
{
    /**
     * Adds 'expired' to cargo_requests.status — a cargo whose bid_deadline
     * passed while still 'pending' (no booking made) flips here. See
     * CargoExpiryService. Same CHECK-constraint swap pattern already used
     * for bids.status in 2026_06_13_000001_add_bid_enhancements.php.
     */
    public function up(): void
    {
        DB::statement('ALTER TABLE cargo_requests DROP CONSTRAINT IF EXISTS cargo_requests_status_check');
        DB::statement("ALTER TABLE cargo_requests ADD CONSTRAINT cargo_requests_status_check CHECK (status::text = ANY (ARRAY['pending'::text,'matched'::text,'completed'::text,'expired'::text]))");
    }

    public function down(): void
    {
        DB::statement('ALTER TABLE cargo_requests DROP CONSTRAINT IF EXISTS cargo_requests_status_check');
        DB::statement("ALTER TABLE cargo_requests ADD CONSTRAINT cargo_requests_status_check CHECK (status::text = ANY (ARRAY['pending'::text,'matched'::text,'completed'::text]))");
    }
};
