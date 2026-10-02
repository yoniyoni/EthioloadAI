<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Http\Requests\VehicleLocationUpdateRequest;
use App\Http\Requests\VehicleRegisterRequest;
use App\Http\Requests\VehicleUpdateRequest;
use App\Http\Resources\VehicleResource;
use App\Models\Booking;
use App\Models\PlatformSetting;
use App\Models\Trip;
use App\Models\Vehicle;
use App\Services\CityCoordinates;
use App\Services\DistanceService;
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

        if (!$user->is_admin && $vehicle->user_id !== $user->id && $vehicle->fleet_owner_id !== $user->id) {
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
                if (!array_key_exists($value, CityCoordinates::COORDS)) {
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
    // City coordinate table and nearest-city lookup now live in CityCoordinates
    // (single canonical source, replacing this class's former CITY_COORDS const).

    public static function nearestCity(float $lat, float $lng): string
    {
        return CityCoordinates::nearestCity($lat, $lng);
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
        // Caller-supplied radius_km wins (Flutter's nearby-trucks screen
        // always sends one); otherwise fall back to the admin-configurable
        // default instead of a hardcoded value.
        $radius   = (float) ($validated['radius_km'] ?? PlatformSetting::get('spatial.nearby_trucks_radius_km', 50));
        $category = $validated['category'] ?? null;
        $radiusMeters = $radius * 1000;

        $cutoff = now()->subHours(48);

        // PostGIS-accelerated: ST_DWithin does the radius filter using the
        // vehicles_position_gist index (replacing the old rectangular
        // whereBetween bounding-box pre-filter); the KNN `<->` operator does
        // the index-accelerated distance sort.
        // Shows both free and busy trucks (see Vehicle::busyVehicleIds) —
        // availability_status isn't trusted here, it goes stale for the
        // normal bid-accept flow (see that helper's docblock).
        $query = Vehicle::with('user')
            ->whereNotNull('position')
            ->where('last_location_at', '>=', $cutoff)
            ->whereHas('user', fn ($q) => $q->where('verification_status', true))
            ->whereRaw(
                'ST_DWithin(position, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?)',
                [$lng, $lat, $radiusMeters]
            )
            ->orderByRaw(
                'position <-> ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography',
                [$lng, $lat]
            )
            ->selectRaw(
                'vehicles.*, ST_Distance(position, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography) / 1000 AS distance_km_pg',
                [$lng, $lat]
            );

        if ($category) {
            $query->where('vehicle_category', $category);
        }

        $candidates = $query->get();
        $busyIds    = Vehicle::busyVehicleIds($candidates->pluck('id'));

        $trucks = $candidates->map(function ($v) use ($busyIds) {
            $driver = $v->user;
            $dist   = (float) $v->distance_km_pg;

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
                'is_busy'         => $busyIds->contains($v->id),
            ];
        })->values();

        return response()->json(['trucks' => $trucks]);
    }

    /**
     * Remove the specified resource from storage.
     */
    public function destroy(string $id)
    {
        $vehicle = Vehicle::findOrFail($id);
        $user = auth()->user();

        if (!$user->is_admin && $vehicle->user_id !== $user->id && $vehicle->fleet_owner_id !== $user->id) {
            return response()->json(['message' => 'Forbidden'], 403);
        }

        if (Booking::where('vehicle_id', $vehicle->id)->exists()) {
            return response()->json([
                'message' => 'This vehicle has booking or trip history and cannot be deleted.',
            ], 422);
        }

        $vehicle->delete();
        return response()->json(['message' => 'Vehicle deleted successfully']);
    }
}
