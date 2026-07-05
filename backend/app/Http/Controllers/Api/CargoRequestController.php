<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Http\Requests\CargoCreateRequest;
use App\Http\Requests\CargoUpdateRequest;
use App\Http\Resources\BidResource;
use App\Http\Resources\CargoResource;
use App\Models\Booking;
use App\Models\CargoRequest;
use App\Models\User;
use App\Models\Vehicle;
use App\Notifications\BidPlacedNotification;
use App\Notifications\BookingCreatedNotification;
use App\Notifications\FixedPriceBookedNotification;
use App\Services\BidService;
use App\Http\Controllers\Api\VehicleController;
use Illuminate\Http\Request;

class CargoRequestController extends Controller
{
    public function __construct(protected BidService $bidService) {}

    /**
     * Display a listing of the resource.
     *
     * For drivers:
     *  - heavy vehicle → show only intercity cargo
     *  - light vehicle → show only intracity cargo, filtered to driver's current_city
     *    (if current_city is null, show all intracity and flag location_unset: true)
     * For shippers: show only their own cargo.
     */
    public function index(Request $request)
    {
        $user  = $request->user();
        $query = CargoRequest::query()->where('status', 'pending');

        if ($user && $user->role === 'shipper') {
            $query = CargoRequest::query()->where('user_id', $user->id);
            return CargoResource::collection($query->latest()->get());
        }

        if ($user && in_array($user->role, ['driver', 'fleet_owner'])) {
            $vehicle  = Vehicle::where('user_id', $user->id)->latest()->first();
            $category = $vehicle?->vehicle_category ?? 'heavy';
            $driverLat = $vehicle?->latitude;
            $driverLng = $vehicle?->longitude;

            if ($category === 'light') {
                $query->where('service_type', 'intracity');
                $currentCity   = $vehicle?->current_city;
                $locationUnset = false;

                if ($currentCity) {
                    $query->whereRaw('LOWER(city) = ?', [strtolower($currentCity)]);
                } else {
                    $locationUnset = true;
                }

                $cargoList = $query->latest()->get();

                // Sort by distance from driver if GPS is available
                if ($driverLat && $driverLng) {
                    $cargoList = $cargoList->map(function ($c) use ($driverLat, $driverLng) {
                        if ($c->pickup_lat && $c->pickup_lng) {
                            $pLat = (float) $c->pickup_lat;
                            $pLng = (float) $c->pickup_lng;
                        } else {
                            $coords = VehicleController::CITY_COORDS[$c->city] ?? null;
                            if (!$coords) { $c->distance_km = null; return $c; }
                            [$pLat, $pLng] = $coords;
                        }
                        $c->distance_km = round(
                            self::haversineKm($driverLat, $driverLng, $pLat, $pLng), 1
                        );
                        return $c;
                    })->sortBy('distance_km')->values();
                }

                return response()->json([
                    'data'           => CargoResource::collection($cargoList),
                    'location_unset' => $locationUnset,
                    'current_city'   => $currentCity,
                ]);
            }

            // heavy → intercity only, sort by distance if GPS available
            $query->where(function ($q) {
                $q->where('service_type', 'intercity')
                  ->orWhereNull('service_type');
            });

            $cargoList = $query->latest()->get();

            if ($driverLat && $driverLng) {
                $cargoList = $cargoList->map(function ($c) use ($driverLat, $driverLng) {
                    if ($c->pickup_lat && $c->pickup_lng) {
                        $pLat = (float) $c->pickup_lat;
                        $pLng = (float) $c->pickup_lng;
                    } else {
                        // Fall back to city-centre coordinates from pickup_location name
                        $cityKey = null;
                        $loc     = $c->pickup_location ?? '';
                        foreach (array_keys(VehicleController::CITY_COORDS) as $city) {
                            if (stripos($loc, $city) !== false) { $cityKey = $city; break; }
                        }
                        if (!$cityKey) { $c->distance_km = null; return $c; }
                        [$pLat, $pLng] = VehicleController::CITY_COORDS[$cityKey];
                    }
                    $c->distance_km = round(
                        self::haversineKm($driverLat, $driverLng, $pLat, $pLng), 1
                    );
                    return $c;
                })->sortBy(fn ($c) => $c->distance_km ?? PHP_INT_MAX)->values();

                return response()->json([
                    'data'           => CargoResource::collection($cargoList),
                    'location_unset' => false,
                    'current_city'   => $vehicle?->current_city,
                ]);
            }

            return response()->json([
                'data'           => CargoResource::collection($cargoList),
                'location_unset' => true,
                'current_city'   => null,
            ]);
        }

        return CargoResource::collection($query->latest()->get());
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

    // GET /freight — list for React frontend (camelCase shape)
    public function freightIndex(Request $request)
    {
        $user   = $request->user();
        $limit  = (int) $request->query('limit', 20);
        $status = $request->query('status');
        $type   = $request->query('cargoType');

        $query = CargoRequest::with('user')->latest();

        if ($user && $user->role === 'shipper') {
            $query->where('user_id', $user->id);
        }

        if ($status && $status !== 'all') {
            $dbStatus = $status === 'posted' ? 'pending' : $status;
            $query->where('status', $dbStatus);
        }
        if ($type && $type !== 'all') {
            $query->where('material_type', $type);
        }

        $total  = $query->count();
        $items  = $query->take($limit)->get()->map(fn ($c) => $this->toFreightShape($c));

        return response()->json(['freight' => $items, 'total' => $total]);
    }

    // GET /freight/{id} — single cargo for React frontend (camelCase shape)
    public function freightShow($id)
    {
        $cargo = CargoRequest::with('user')->findOrFail($id);
        return response()->json($this->toFreightShape($cargo));
    }

    private function toFreightShape(CargoRequest $c): array
    {
        return [
            'id'               => $c->id,
            'pickupLocation'   => $c->pickup_location,
            'deliveryLocation' => $c->destination,
            'cargoType'        => $c->material_type,
            'weightTons'       => $c->weight,
            'budget'           => $c->budget,
            'urgencyLevel'     => $c->urgency_level,
            'status'           => $c->status === 'pending' ? 'posted' : $c->status,
            'cargoDescription' => null,
            'distanceKm'       => null,
            'volumeM3'         => null,
            'deadline'         => null,
            'shipperId'        => $c->user_id,
            'shipperName'      => $c->user?->full_name,
            'shipperPhone'     => $c->user?->phone,
            'createdAt'        => $c->created_at,
        ];
    }

    /**
     * GET /driver/return-cargo
     * Finds available cargo near the driver so they avoid driving empty.
     *
     * Priority:
     *  1. Driver has an active booking → search near the booking's destination city
     *     (classic "return load" — pick up cargo at the delivery city to bring back)
     *  2. No active booking → search near the driver's vehicle current_city
     *     (show what's available where the driver is parked right now)
     */
    public function returnCargo(Request $request)
    {
        $user = auth()->user();

        // 1. Try to use the destination of the driver's active booking/trip
        $activeBooking = Booking::with('cargoRequest')
            ->where('driver_id', $user->id)
            ->where(function ($q) {
                $q->whereIn('booking_status', ['confirmed', 'in_transit'])
                  ->orWhereHas('trip', fn ($t) => $t->where('trip_status', 'ongoing'));
            })
            ->latest()
            ->first();

        $excludeCargoId = null;

        if ($activeBooking?->cargoRequest) {
            $rawCity = $activeBooking->cargoRequest->destination;
            $city    = trim(preg_split('/[,\/]/', $rawCity)[0]);
            $excludeCargoId = $activeBooking->cargo_request_id;
        } else {
            // 2. Fall back to the driver's vehicle current_city
            $vehicle = \App\Models\Vehicle::where('user_id', $user->id)->latest()->first();
            $city    = $vehicle?->current_city ?? null;
        }

        if (!$city) {
            return response()->json(['data' => [], 'destination_city' => null]);
        }

        $query = CargoRequest::where('status', 'pending')
            ->whereRaw('LOWER(pickup_location) LIKE ?', ['%' . strtolower($city) . '%'])
            ->latest()
            ->limit(5);

        if ($excludeCargoId) {
            $query->where('id', '!=', $excludeCargoId);
        }

        $returnCargo = $query->get();

        return response()->json([
            'destination_city' => $city,
            'data'             => CargoResource::collection($returnCargo),
        ]);
    }

    /**
     * POST /cargo-requests/{cargo}/book-direct
     * Driver immediately books a fixed-price cargo without bidding.
     * The cargo must have price_type='fixed' and status='pending'.
     */
    public function bookDirect(CargoRequest $cargo)
    {
        $user = auth()->user();

        if (!$user->verification_status) {
            return response()->json([
                'message' => 'Your account is not yet verified. Upload your documents and wait for Admin approval before booking cargo.',
            ], 403);
        }

        if ($cargo->price_type !== 'fixed') {
            return response()->json(['message' => 'This cargo requires a bid, not a direct booking.'], 422);
        }

        if ($cargo->status !== 'pending') {
            return response()->json(['message' => 'This cargo is no longer available.'], 422);
        }

        if (!$cargo->budget) {
            return response()->json(['message' => 'Fixed-price cargo must have a budget set.'], 422);
        }

        // Use the driver's first available vehicle
        $vehicle = \App\Models\Vehicle::where('user_id', $user->id)
            ->where('availability_status', 'available')
            ->first();

        if (!$vehicle) {
            $vehicle = \App\Models\Vehicle::where('user_id', $user->id)->first();
        }

        if (!$vehicle) {
            return response()->json(['message' => 'You have no registered vehicle. Please register a vehicle first.'], 422);
        }

        $booking = \App\Models\Booking::create([
            'cargo_id'         => $cargo->id,
            'vehicle_id'       => $vehicle->id,
            'driver_id'        => $user->id,
            'booking_status'   => 'confirmed',
            'estimated_price'  => $cargo->budget,
            'commission_fee'   => $cargo->budget * 0.10,
        ]);

        $cargo->update(['status' => 'matched']);
        $vehicle->update(['availability_status' => 'busy']);

        $booking->load(['cargoRequest', 'driver']);

        // Notify the shipper that their fixed-price cargo has been booked
        $cargo->user?->notify(new FixedPriceBookedNotification($booking));

        // Notify the driver that their booking is confirmed
        $booking->driver?->notify(new BookingCreatedNotification($booking));

        return response()->json([
            'success' => true,
            'message' => 'Cargo booked at fixed price.',
            'data'    => new \App\Http\Resources\BookingResource(
                $booking->load(['cargoRequest.user', 'driver', 'vehicle'])
            ),
        ], 201);
    }

    /**
     * POST /cargo-requests/{cargo}/accept-price
     * Driver registers interest in a fixed-price offer.
     * Creates a bid at the fixed budget so multiple drivers can apply;
     * the shipper reviews all applicants ranked by rating and selects one.
     */
    public function acceptPrice(CargoRequest $cargo)
    {
        $user = auth()->user();

        if (!$user->verification_status) {
            return response()->json([
                'message' => 'Your account is not yet verified.',
            ], 403);
        }

        if ($cargo->price_type !== 'fixed') {
            return response()->json(['message' => 'This cargo is not fixed-price.'], 422);
        }

        if ($cargo->status !== 'pending') {
            return response()->json(['message' => 'This cargo is no longer available.'], 422);
        }

        if (!$cargo->budget) {
            return response()->json(['message' => 'No price is set for this cargo.'], 422);
        }

        $vehicle = Vehicle::where('user_id', $user->id)
            ->where('availability_status', 'available')
            ->first()
            ?? Vehicle::where('user_id', $user->id)->first();

        if (!$vehicle) {
            return response()->json(['message' => 'You have no registered vehicle.'], 422);
        }

        try {
            $bid = $this->bidService->acceptFixedPrice($cargo, $user, $vehicle);
        } catch (\Exception $e) {
            return response()->json(['message' => $e->getMessage()], 422);
        }

        $cargo->user?->notify(new BidPlacedNotification($bid->load(['driver', 'cargoRequest'])));

        return (new BidResource($bid->load(['driver', 'vehicle'])))->response()->setStatusCode(201);
    }

    /**
     * Store a newly created resource in storage.
     */
    public function store(CargoCreateRequest $request)
    {
        $validated = $request->validated();
        $cargoRequest = CargoRequest::create(array_merge($validated, [
            'user_id' => auth()->id(),
            'status' => $validated['status'] ?? 'pending',
        ]));

        // Use GPS coords from client if supplied; otherwise fall back to city-centre lookup.
        if (!$cargoRequest->pickup_lat || !$cargoRequest->pickup_lng) {
            $cityName = null;
            if ($cargoRequest->service_type === 'intracity') {
                $cityName = $cargoRequest->city;
            } elseif ($cargoRequest->pickup_location) {
                foreach (array_keys(VehicleController::CITY_COORDS) as $city) {
                    if (stripos($cargoRequest->pickup_location, $city) !== false) {
                        $cityName = $city;
                        break;
                    }
                }
            }
            if ($cityName && isset(VehicleController::CITY_COORDS[$cityName])) {
                [$lat, $lng] = VehicleController::CITY_COORDS[$cityName];
                $cargoRequest->update(['pickup_lat' => $lat, 'pickup_lng' => $lng]);
            }
        }

        return (new CargoResource($cargoRequest))->response()->setStatusCode(201);
    }

    /**
     * GET /cargo-requests/{id}/nearby-drivers
     * Returns nearby available drivers for the cargo's pickup location.
     * Accessible only to the shipper who owns the cargo.
     */
    public function nearbyDrivers(CargoRequest $cargo)
    {
        $user = auth()->user();
        if ($cargo->user_id !== $user->id && !$user->is_admin) {
            return response()->json(['message' => 'Forbidden'], 403);
        }

        // Determine required vehicle category
        $requiredCategory = $cargo->service_type === 'intracity' ? 'light' : 'heavy';

        // Pickup reference point
        $pickupLat = (float) $cargo->pickup_lat;
        $pickupLng = (float) $cargo->pickup_lng;

        if (!$pickupLat || !$pickupLng) {
            // Try city lookup if coords not set
            $cityName = $cargo->service_type === 'intracity' ? $cargo->city : $cargo->pickup_location;
            $coords   = null;
            if ($cityName) {
                foreach (VehicleController::CITY_COORDS as $city => $c) {
                    if (stripos($cityName, $city) !== false) {
                        $coords = $c;
                        break;
                    }
                }
            }
            if (!$coords) {
                return response()->json(['drivers' => []]);
            }
            [$pickupLat, $pickupLng] = $coords;
        }

        $cutoff = now()->subHours(24);

        $vehicles = Vehicle::with('user')
            ->where('vehicle_category', $requiredCategory)
            ->where('availability_status', 'available')
            ->whereNotNull('latitude')
            ->whereNotNull('longitude')
            ->where('last_location_at', '>=', $cutoff)
            ->get();

        $drivers = $vehicles->map(function ($v) use ($pickupLat, $pickupLng) {
            $driver = $v->user;
            if (!$driver || !$driver->verification_status) return null;

            $dist = self::haversineKm($pickupLat, $pickupLng, (float) $v->latitude, (float) $v->longitude);
            $minutesAgo = (int) now()->diffInMinutes($v->last_location_at);

            if ($minutesAgo < 60) {
                $lastSeen = "{$minutesAgo} min ago";
            } elseif ($minutesAgo < 1440) {
                $hours = (int) ($minutesAgo / 60);
                $lastSeen = "{$hours} hour" . ($hours > 1 ? 's' : '') . " ago";
            } else {
                $lastSeen = "> 24 hours ago";
            }

            return [
                'driver_id'     => $driver->id,
                'driver_name'   => $driver->full_name,
                'vehicle_type'  => $v->truck_type,
                'vehicle_plate' => $v->plate_number,
                'rating'        => round((float) $v->rating, 1),
                'distance_km'   => round($dist, 1),
                'last_seen'     => $lastSeen,
                'current_city'  => $v->current_city,
                'latitude'      => $v->latitude !== null ? (float) $v->latitude : null,
                'longitude'     => $v->longitude !== null ? (float) $v->longitude : null,
            ];
        })
        ->filter()
        ->sortBy('distance_km')
        ->values()
        ->take(20);

        return response()->json(['drivers' => $drivers]);
    }

    /**
     * Display the specified resource.
     */
    public function show(string $id)
    {
        return new CargoResource(CargoRequest::findOrFail($id));
    }

    /**
     * Update the specified resource in storage.
     */
    public function update(CargoUpdateRequest $request, string $id)
    {
        $cargoRequest = CargoRequest::findOrFail($id);
        $user = auth()->user();

        if (!$user->is_admin && $cargoRequest->user_id !== $user->id) {
            return response()->json(['message' => 'Forbidden'], 403);
        }

        if (!$user->is_admin && $cargoRequest->status !== 'pending') {
            return response()->json(['message' => 'Cargo can only be edited while pending.'], 422);
        }

        $cargoRequest->update($request->validated());
        return new CargoResource($cargoRequest);
    }

    /**
     * Remove the specified resource from storage.
     * Only allowed while cargo is still pending (no booking exists).
     * Notifies any drivers who placed bids so they aren't left waiting.
     */
    public function destroy(string $id)
    {
        $cargoRequest = CargoRequest::findOrFail($id);
        $user = auth()->user();

        if (!$user->is_admin && $cargoRequest->user_id !== $user->id) {
            return response()->json(['message' => 'Forbidden'], 403);
        }

        if (!$user->is_admin && $cargoRequest->status !== 'pending') {
            return response()->json(['message' => 'Only pending cargo can be cancelled. A booking already exists for this cargo.'], 422);
        }

        // Notify every driver who bid on this cargo, then remove their bids
        $pendingBids = $cargoRequest->bids()
            ->with('driver')
            ->whereIn('status', ['pending', 'countered'])
            ->get();

        foreach ($pendingBids as $bid) {
            $bid->driver?->notify(
                new \App\Notifications\BidRejectedNotification($bid, 'cargo_cancelled')
            );
        }

        // Remove all bids (FK constraint) then delete the cargo
        $cargoRequest->bids()->delete();
        $cargoRequest->delete();

        return response()->json(['message' => 'Cargo cancelled and all pending bids have been notified.']);
    }
}
