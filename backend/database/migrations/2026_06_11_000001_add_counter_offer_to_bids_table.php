<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::table('bids', function (Blueprint $table) {
            $table->decimal('counter_amount', 10, 2)->nullable()->after('note');
            $table->text('counter_note')->nullable()->after('counter_amount');
            $table->enum('counter_by', ['shipper', 'driver'])->nullable()->after('counter_note');
            $table->timestamp('counter_at')->nullable()->after('counter_by');
        });

        // Extend status check constraint to include 'countered' (PostgreSQL)
        DB::statement("ALTER TABLE bids DROP CONSTRAINT IF EXISTS bids_status_check");
        DB::statement("ALTER TABLE bids ADD CONSTRAINT bids_status_check CHECK (status::text = ANY (ARRAY['pending'::text,'accepted'::text,'rejected'::text,'expired'::text,'countered'::text]))");
    }

    public function down(): void
    {
        Schema::table('bids', function (Blueprint $table) {
            $table->dropColumn(['counter_amount', 'counter_note', 'counter_by', 'counter_at']);
        });

        DB::statement("ALTER TABLE bids DROP CONSTRAINT IF EXISTS bids_status_check");
        DB::statement("ALTER TABLE bids ADD CONSTRAINT bids_status_check CHECK (status::text = ANY (ARRAY['pending'::text,'accepted'::text,'rejected'::text,'expired'::text]))");
    }
};
