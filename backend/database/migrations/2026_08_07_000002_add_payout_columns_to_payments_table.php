<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    /**
     * payment_status tracks whether the shipper's money has arrived;
     * payout_status independently tracks whether that money has since been
     * transferred out to the driver — the two are deliberately separate so
     * a booking can be 'paid' (money held) for a while before it's
     * 'paid_out' (money released), and so a failed transfer never gets
     * confused with a failed payment.
     */
    public function up(): void
    {
        Schema::table('payments', function (Blueprint $table) {
            $table->enum('payout_status', ['not_applicable', 'pending', 'paid_out', 'failed'])
                ->default('not_applicable')
                ->after('payment_status');
            $table->string('payout_reference')->nullable()->after('transaction_ref');
        });
    }

    public function down(): void
    {
        Schema::table('payments', function (Blueprint $table) {
            $table->dropColumn(['payout_status', 'payout_reference']);
        });
    }
};
