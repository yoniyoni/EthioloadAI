<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::table('payments', function (Blueprint $table) {
            $table->decimal('commission_amount', 10, 2)->default(0)->after('amount');
            $table->decimal('driver_net_amount', 10, 2)->default(0)->after('commission_amount');
            $table->foreignId('paid_by')->nullable()->constrained('users')->nullOnDelete()->after('driver_net_amount');
        });
    }

    public function down(): void
    {
        Schema::table('payments', function (Blueprint $table) {
            $table->dropForeign(['paid_by']);
            $table->dropColumn(['commission_amount', 'driver_net_amount', 'paid_by']);
        });
    }
};
