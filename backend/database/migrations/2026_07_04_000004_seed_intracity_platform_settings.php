<?php

use Illuminate\Database\Migrations\Migration;
use App\Models\PlatformSetting;

return new class extends Migration
{
    public function up(): void
    {
        $settings = [
            [
                'key'   => 'pricing.intracity_rate_min',
                'value' => '50',
            ],
            [
                'key'   => 'pricing.intracity_rate_max',
                'value' => '100',
            ],
        ];

        foreach ($settings as $s) {
            PlatformSetting::updateOrCreate(['key' => $s['key']], ['value' => $s['value']]);
        }
    }

    public function down(): void
    {
        PlatformSetting::whereIn('key', [
            'pricing.intracity_rate_min',
            'pricing.intracity_rate_max',
        ])->delete();
    }
};
