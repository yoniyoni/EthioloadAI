<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Http\Requests\VehicleLocationUpdateRequest;
use App\Http\Requests\VehicleRegisterRequest;
use App\Http\Requests\VehicleUpdateRequest;
use App\Http\Resources\VehicleResource;
use App\Models\Trip;
use App\Models\Vehicle;
use Illuminate\Http\Request;

class VehicleController extends Controller
{
    /**
     * Display a listing of the resource.
     * Drivers see only their own vehicles (so bid placement uses the correct vehicle_id).
     * Fleet owners see only vehicles in their fleet.
     * Admins/shippers see all.
     */
    public function index(Request $request)
    {
        $user = $request->user();

        if ($user?->role === 'driver') {
            return VehicleResource::collection(Vehicle::where('user_id', $user->id)->get());
        }

        if ($user?->role === 'fleet_owner') {
            return VehicleResource::collection(Vehicle::where('fleet_owner_id', $user->id)->get());
        }

        return VehicleResource::collection(Vehicle::all());
    }

    /**
     * GET /my-vehicles — current user's own vehicles, camelCase shape.
     */
    public function myVehicles()
    {
        $vehicles = Vehicle::where('user_id', auth()->id())->get()->map(fn ($v) => [
            'id'           => $v->id,
            'truckType'    => $v->truck_type,
            'plateNumber'  => $v->plate_number,
            'capacityTons' => $v->capacity,
            'currentCity'  => $v->current_city,
            'isAvailable'  => $v->availability_status === 'available',
        ]);
        return response()->json(['vehicles' => $vehicles]);
    }

    /**
     * Register a new vehicle.
     */
    public function register(VehicleRegisterRequest $request)
    {
        $validated = $request->validated();
        $vehicle = Vehicle::create(array_merge($validated, [
            'user_id' => auth()->id(),
            'availability_status' => $validated['availability_status'] ?? 'available',
            'rating' => $validated['rating'] ?? 0,
        ]));

        return (new VehicleResource($vehicle))->response()->setStatusCode(201);
    }

    /**
     * Store a newly created resource in storage.
     */
    public function store(VehicleRegisterRequest $request)
    {
        return $this->register($request);
    }

    /**
     * Display the specified resource.
     */
    public function show(string $id)
    {
       return new VehicleResource(Vehicle::findOrFail($id));
    }

    /**
     * Update the specified resource in storage.
     */
    public function update(VehicleUpdateRequest $request, string $id)
    {
        $vehicle = Vehicle::findOrFail($id);
        $user = auth()->user();

        if (!$user->is_admin && $vehicle->user_id !== $user->id) {
            return response()->json(['message' => 'Forbidden'], 403);
        }

        $vehicle->update($request->validated());
        return new VehicleResource($vehicle);
    }

    /**
     * Update the vehicle GPS location and auto-detect nearest city.
     */
    public function updateLocation(VehicleLocationUpdateRequest $request, string $id)
    {
        $vehicle = Vehicle::findOrFail($id);
        $user = auth()->user();

        if (!$user->is_admin && $vehicle->user_id !== $user->id) {
            return response()->json(['message' => 'Forbidden'], 403);
        }

        $data = $request->validated();
        $vehicle->update($data);

        // Auto-detect nearest city from GPS coordinates
        if (isset($data['latitude']) && isset($data['longitude'])) {
            $city = self::nearestCity((float) $data['latitude'], (float) $data['longitude']);
            $vehicle->update(['current_city' => $city]);
        }

        return new VehicleResource($vehicle);
    }

    /**
     * POST /driver/location
     * Driver pushes their GPS position (called every ~25 minutes by the app).
     * Updates vehicle lat/lng/last_location_at, auto-detects city,
     * and appends to route_data of any ongoing trip.
     */
    public function driverLocation(Request $request)
    {
        $validated = $request->validate([
            'lat' => 'required|numeric|between:-90,90',
            'lng' => 'required|numeric|between:-180,180',
        ]);

        $user    = auth()->user();
        $vehicle = Vehicle::where('user_id', $user->id)->latest()->first();

        if (!$vehicle) {
            return response()->json(['message' => 'No vehicle registered.'], 422);
        }

        $lat  = (float) $validated['lat'];
        $lng  = (float) $validated['lng'];
        $city = self::nearestCity($lat, $lng);

        $vehicle->update([
            'latitude'         => $lat,
            'longitude'        => $lng,
            'last_location_at' => now(),
            'current_city'     => $city,
        ]);

        // Append to route_data of the driver's current ongoing trip
        $ongoingTrip = Trip::whereHas('booking', fn ($q) =>
                $q->where('driver_id', $user->id)
            )
            ->where('trip_status', 'ongoing')
            ->latest()
            ->first();

        if ($ongoingTrip) {
            $history   = $ongoingTrip->route_data ?? [];
            $history[] = ['lat' => $lat, 'lng' => $lng, 'ts' => now()->toISOString()];
            $ongoingTrip->update(['route_data' => $history]);
        }

        return response()->json([
            'success'          => true,
            'current_city'     => $city,
            'last_location_at' => $vehicle->last_location_at->toISOString(),
        ]);
    }

    /**
     * PATCH /driver/current-city
     * Driver manually sets their current city (without GPS).
     */
    public function updateCurrentCity(Request $request)
    {
        $validated = $request->validate([
            'city' => ['required', 'string', 'max:100', function ($attr, $value, $fail) {
                if (!array_key_exists($value, self::CITY_COORDS)) {
                    $fail("'{$value}' is not a recognised Ethiopian city. Use the list from the app.");
                }
            }],
        ]);

        $user    = auth()->user();
        $vehicle = Vehicle::where('user_id', $user->id)->latest()->first();

        if (!$vehicle) {
            return response()->json(['message' => 'No vehicle registered. Please register a vehicle first.'], 422);
        }

        $vehicle->update(['current_city' => $validated['city']]);

        return response()->json([
            'success'      => true,
            'current_city' => $vehicle->current_city,
            'vehicle'      => new VehicleResource($vehicle),
        ]);
    }

    // ── City lookup helpers ──────────────────────────────────────────────────

    const CITY_COORDS = [
        'Addis Ababa'    => [9.0320,  38.7469],
        'Gondar'         => [12.6030, 37.4521],
        'Bahir Dar'      => [11.5931, 37.3911],
        'Mekele'         => [13.4967, 39.4697],
        'Hawassa'        => [7.0504,  38.4955],
        'Dire Dawa'      => [9.5931,  41.8661],
        'Jimma'          => [7.6731,  36.8346],
        'Adama'          => [8.5400,  39.2700],
        'Dessie'         => [11.1333, 39.6333],
        'Debre Birhan'   => [9.6833,  39.5333],
        'Debre Markos'   => [10.3333, 37.7167],
        'Debre Tabor'    => [11.8500, 38.0167],
        'Humera'         => [14.3000, 36.6000],
        'Metema'         => [12.8500, 36.2000],
        'Shire'          => [14.1000, 38.2833],
        'Axum'           => [14.1200, 38.7200],
        'Adwa'           => [14.1667, 38.9000],
        'Adigrat'        => [14.2667, 39.4500],
        'Lalibela'       => [12.0333, 39.0500],
        'Woldia'         => [11.8333, 39.6000],
        'Kombolcha'      => [11.0833, 39.7333],
        'Addis Zemen'    => [12.1333, 37.7833],
        'Woreta'         => [11.9167, 37.7000],
        'Injibara'       => [10.9833, 36.9833],
        'Motta'          => [11.0833, 37.8667],
        'Bure'           => [10.7000, 37.0667],
        'Finote Selam'   => [10.7000, 37.2667],
        'Arba Minch'     => [6.0333,  37.5500],
        'Assosa'         => [10.0667, 34.5333],
        'Gambela'        => [8.2500,  34.5833],
        'Hosaena'        => [7.5500,  37.8500],
        'Shashamane'     => [7.2000,  38.5833],
        'Dilla'          => [6.4167,  38.3167],
        'Yirgalem'       => [6.7500,  38.4000],
        'Asela'          => [7.9500,  39.1333],
        'Robe'           => [7.1167,  40.0000],
        'Goba'           => [7.0000,  39.9833],
        'Jijiga'         => [9.3500,  42.8000],
        'Harar'          => [9.3167,  42.1333],
        'Nekemte'        => [9.0833,  36.5500],
        'Gimbi'          => [9.1667,  35.8167],
        'Ambo'           => [8.9833,  37.8500],
        'Welkite'        => [8.2833,  37.7833],
        'Butajira'       => [8.1167,  38.3667],
    ];

    public static function nearestCity(float $lat, float $lng): string
    {
        $best     = null;
        $bestDist = PHP_FLOAT_MAX;

        foreach (self::CITY_COORDS as $city => [$clat, $clng]) {
            $dist = self::haversineKm($lat, $lng, $clat, $clng);
            if ($dist < $bestDist) {
                $bestDist = $dist;
                $best     = $city;
            }
        }

        return $best ?? 'Addis Ababa';
    }

    private static function haversineKm(float $lat1, float $lon1, float $lat2, float $lon2): float
    {
        $R    = 6371.0;
        $phi1 = deg2rad($lat1); $phi2 = deg2rad($lat2);
        $dphi = deg2rad($lat2 - $lat1);
        $dlam = deg2rad($lon2 - $lon1);
        $a    = sin($dphi / 2) ** 2 + cos($phi1) * cos($phi2) * sin($dlam / 2) ** 2;
        return 2 * $R * asin(sqrt($a));
    }

    /**
     * Display nearby available vehicles.
     */
    public function nearby(Request $request)
    {
        $validated = $request->validate([
            'latitude' => 'required|numeric|between:-90,90',
            'longitude' => 'required|numeric|between:-180,180',
            'radius_km' => 'sometimes|numeric|min:1',
        ]);

        $radiusKm = $validated['radius_km'] ?? 50;
        $latitude = $validated['latitude'];
        $longitude = $validated['longitude'];

        $latDelta = $radiusKm / 110;
        $lngDelta = $radiusKm / (111 * max(cos(deg2rad($latitude)), 0.0001));

        $vehicles = Vehicle::where('availability_status', 'available')
            ->whereBetween('latitude', [$latitude - $latDelta, $latitude + $latDelta])
            ->whereBetween('longitude', [$longitude - $lngDelta, $longitude + $lngDelta])
            ->get();

        return VehicleResource::collection($vehicles);
    }

    /**
     * GET /nearby-trucks?lat=&lng=&radius_km=&category=
     * Returns available vehicles with driver info, sorted by distance.
     * Used by the shipper nearby-trucks map (Feature 4).
     */
    public function nearbyTrucks(Request $request)
    {
        $validated = $request->validate([
            'lat'       => 'required|numeric|between:-90,90',
            'lng'       => 'required|numeric|between:-180,180',
            'radius_km' => 'sometimes|numeric|min:1|max:500',
            'category'  => 'sometimes|in:light,heavy',
        ]);

        $lat      = (float) $validated['lat'];
        $lng      = (float) $validated['lng'];
        $radius   = (float) ($validated['radius_km'] ?? 100);
        $category = $validated['category'] ?? null;

        $latDelta = $radius / 110;
        $lngDelta = $radius / (111 * max(cos(deg2rad($lat)), 0.0001));

        $query = Vehicle::with('user')
            ->where('availability_status', 'available')
            ->whereNotNull('latitude')
            ->whereNotNull('longitude')
            ->whereBetween('latitude',  [$lat - $latDelta, $lat + $latDelta])
            ->whereBetween('longitude', [$lng - $lngDelta, $lng + $lngDelta])
            ->where('last_location_at', '>=', now()->subHours(48));

        if ($category) {
            $query->where('vehicle_category', $category);
        }

        $cutoff = now()->subHours(48);
        $trucks = $query->get()->map(function ($v) use ($lat, $lng, $cutoff) {
            $driver = $v->user;
            $dist   = self::haversineKm($lat, $lng, (float) $v->latitude, (float) $v->longitude);

            $minsAgo = $v->last_location_at
                ? (int) now()->diffInMinutes($v->last_location_at)
                : null;

            if ($minsAgo === null)     $lastSeen = 'unknown';
            elseif ($minsAgo < 60)    $lastSeen = "{$minsAgo} min ago";
            elseif ($minsAgo < 1440)  $lastSeen = ((int)($minsAgo / 60)) . ' h ago';
            else                       $lastSeen = '> 24h ago';

            return [
                'vehicle_id'      => $v->id,
                'driver_id'       => $driver?->id,
                'driver_name'     => $driver?->full_name ?? 'Unknown',
                'vehicle_type'    => $v->truck_type,
                'vehicle_plate'   => $v->plate_number,
                'vehicle_category'=> $v->vehicle_category,
                'latitude'        => (float) $v->latitude,
                'longitude'       => (float) $v->longitude,
                'current_city'    => $v->current_city,
                'rating'          => round((float) $v->rating, 1),
                'distance_km'     => round($dist, 1),
                'last_seen'       => $lastSeen,
            ];
        })
        ->filter(fn ($t) => $t['distance_km'] <= $radius)
        ->sortBy('distance_km')
        ->values();

        return response()->json(['trucks' => $trucks]);
    }

    /**
     * Remove the specified resource from storage.
     */
    public function destroy(string $id)
    {
        $vehicle = Vehicle::findOrFail($id);
        $user = auth()->user();

        if (!$user->is_admin && $vehicle->user_id !== $user->id) {
            return response()->json(['message' => 'Forbidden'], 403);
        }

        $vehicle->delete();
        return response()->json(['message' => 'Vehicle deleted successfully']);
    }
}
