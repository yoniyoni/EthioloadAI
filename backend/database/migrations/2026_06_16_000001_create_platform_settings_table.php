<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;
use Illuminate\Support\Facades\DB;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('platform_settings', function (Blueprint $table) {
            $table->id();
            $table->string('key')->unique();
            $table->text('value')->nullable();
            $table->timestamps();
        });

        DB::table('platform_settings')->insert([
            ['key' => 'pricing.rate_min', 'value' => '18', 'created_at' => now(), 'updated_at' => now()],
            ['key' => 'pricing.rate_max', 'value' => '28', 'created_at' => now(), 'updated_at' => now()],
        ]);
    }

    public function down(): void
    {
        Schema::dropIfExists('platform_settings');
    }
};
