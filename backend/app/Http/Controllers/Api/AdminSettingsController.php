<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\PlatformSetting;
use Illuminate\Http\Request;

class AdminSettingsController extends Controller
{
    public function pricingShow()
    {
        return response()->json([
            'rate_min'              => (float) PlatformSetting::get('pricing.rate_min', 18),
            'rate_max'              => (float) PlatformSetting::get('pricing.rate_max', 28),
            'intracity_rate_min'    => (float) PlatformSetting::get('pricing.intracity_rate_min', 50),
            'intracity_rate_max'    => (float) PlatformSetting::get('pricing.intracity_rate_max', 100),
        ]);
    }

    public function pricingUpdate(Request $request)
    {
        $data = $request->validate([
            'rate_min' => 'required|numeric|min:1',
            'rate_max' => 'required|numeric|min:1',
        ]);

        if ($data['rate_min'] >= $data['rate_max']) {
            return response()->json(['message' => 'rate_min must be less than rate_max'], 422);
        }

        PlatformSetting::set('pricing.rate_min', $data['rate_min']);
        PlatformSetting::set('pricing.rate_max', $data['rate_max']);

        return response()->json([
            'success'  => true,
            'rate_min' => (float) $data['rate_min'],
            'rate_max' => (float) $data['rate_max'],
        ]);
    }

    public function spatialShow()
    {
        return response()->json([
            'nearby_trucks_radius_km'  => (float) PlatformSetting::get('spatial.nearby_trucks_radius_km', 50),
            'nearby_drivers_radius_km' => (float) PlatformSetting::get('spatial.nearby_drivers_radius_km', 50),
            'cargo_search_radius_km'   => (float) PlatformSetting::get('spatial.cargo_search_radius_km', 50),
            'fleet_dispatch_radius_km' => (float) PlatformSetting::get('spatial.fleet_dispatch_radius_km', 200),
            'backhaul_radius_km'       => (float) PlatformSetting::get('spatial.backhaul_radius_km', 200),
        ]);
    }

    public function spatialUpdate(Request $request)
    {
        $data = $request->validate([
            'nearby_trucks_radius_km'  => 'required|numeric|min:1|max:2000',
            'nearby_drivers_radius_km' => 'required|numeric|min:1|max:2000',
            'cargo_search_radius_km'   => 'required|numeric|min:1|max:2000',
            'fleet_dispatch_radius_km' => 'required|numeric|min:1|max:2000',
            'backhaul_radius_km'       => 'required|numeric|min:1|max:2000',
        ]);

        foreach ($data as $key => $value) {
            PlatformSetting::set("spatial.{$key}", $value);
        }

        return response()->json(array_merge(['success' => true], array_map(fn ($v) => (float) $v, $data)));
    }
}
