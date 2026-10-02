<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    /**
     * Driver-only in practice: a bank destination for Chapa's Transfers API
     * (PaymentService::releaseToDriver()). Nullable — a driver without
     * these on file simply can't be paid out automatically yet;
     * releaseToDriver() handles that gracefully rather than requiring it
     * up front for every driver.
     */
    public function up(): void
    {
        Schema::table('users', function (Blueprint $table) {
            $table->string('payout_bank_code')->nullable();
            $table->string('payout_account_number')->nullable();
            $table->string('payout_account_name')->nullable();
        });
    }

    public function down(): void
    {
        Schema::table('users', function (Blueprint $table) {
            $table->dropColumn(['payout_bank_code', 'payout_account_number', 'payout_account_name']);
        });
    }
};
