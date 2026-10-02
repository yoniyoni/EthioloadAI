<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Http\Requests\CargoCreateRequest;
use App\Http\Requests\CargoUpdateRequest;
use App\Http\Resources\BidResource;
use App\Http\Resources\CargoResource;
use App\Jobs\GenerateBackhaulRecommendations;
use App\Models\Booking;
use App\Models\CargoRequest;
use App\Models\PlatformSetting;
use App\Models\Trip;
use App\Models\User;
use App\Models\Vehicle;
use App\Notifications\BidPlacedNotification;
use App\Notifications\BookingCreatedNotification;
use App\Notifications\FixedPriceBookedNotification;
use App\Services\BidService;
use App\Services\CargoExpiryService;
use App\Services\CityCoordinates;
use App\Services\DistanceService;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;

class CargoRequestController extends Controller
{
    public function __construct(
        protected BidService $bidService,
        protected CargoExpiryService $cargoExpiry,
    ) {}

    /**
     * Display a listing of the resource.
     *
     * For drivers:
     *  - heavy vehicle → intercity cargo only. Requires the vehicle to have a
     *    GPS position (no GPS → empty list), limited to a PlatformSetting-
     *    configurable radius (spatial.cargo_search_radius_km), PostGIS-sorted
     *    nearest first.
     *  - light vehicle → intracity cargo only, exact-matched to the driver's
     *    current_city (LOWER(city) = LOWER(current_city)) — a hard boundary
     *    that GPS never relaxes or widens. If current_city is set, the full
     *    city-matched list is returned even without GPS (never empty just
     *    because GPS is missing). If GPS is also available, that same
     *    city-filtered list is additionally sorted by distance (PostGIS
     *    ST_Distance / KNN `<->`) — GPS is a sort enhancement only, it never
     *    adds or removes rows. location_unset is purely informational
     *    (true unless both current_city and GPS are known) and is never a
     *    gate — except the one deliberate exception: if neither current_city
     *    nor GPS is known at all, there is nothing to scope the list to, so
     *    an empty list is returned.
     * For shippers: show only their own cargo.
     */
    public function index(Request $request)
    {
        // Lazily close bidding on any cargo whose bid_deadline has passed —
        // see CargoExpiryService. Keeps this endpoint's 'pending' status
        // correct with no scheduler running (true of this dev environment).
        $this->cargoExpiry->expireOverdue();

        $user  = $request->user();
        $query = CargoRequest::query()->where('status', 'pending');

        if ($user && $user->role === 'shipper') {
            $query = CargoRequest::query()->where('user_id', $user->id);
            return CargoResource::collection($query->latest()->get());
        }

        if ($user && in_array($user->role, ['driver', 'fleet_owner'])) {
            $vehicle   = Vehicle::where('user_id', $user->id)->latest()->first();
            $category  = $vehicle?->vehicle_category ?? 'heavy';
            $driverLat = $vehicle?->latitude !== null ? (float) $vehicle->latitude : null;
            $driverLng = $vehicle?->longitude !== null ? (float) $vehicle->longitude : null;

            if ($category === 'light') {
                $query->where('service_type', 'intracity');

                $currentCity = $vehicle?->current_city;
                $hasGps      = $driverLat !== null && $driverLng !== null;

                if ($currentCity) {
                    // Exact-city hard boundary — never relaxed or widened by
                    // GPS proximity, regardless of whether GPS is available.
                    $query->whereRaw('LOWER(city) = LOWER(?)', [$currentCity]);
                }

                // Purely informational: true unless BOTH current_city and GPS
                // are known. Never gates the response except in the one case
                // below where neither is known at all.
                $locationUnset = !$currentCity || !$hasGps;

                // Case 3 (deliberate product decision, diverging from the
                // literal original behavior which would have returned an
                // unfiltered nationwide intracity dump here instead): with
                // neither a known city nor GPS there is nothing to scope the
                // list to, so return empty rather than everything.
                if (!$currentCity && !$hasGps) {
                    return response()->json([
                        'data'           => [],
                        'location_unset' => true,
                        'current_city'   => $currentCity,
                    ]);
                }

                if (!$hasGps) {
                    // Case 1: exact-city-filtered list, no GPS to sort by
                    // distance. The full matched list is returned — never
                    // empty just because GPS is missing.
                    $cargoList = $query->latest()->get();

                    return response()->json([
                        'data'           => CargoResource::collection($cargoList),
                        'location_unset' => $locationUnset,
                        'current_city'   => $currentCity,
                    ]);
                }

                // Case 2 / implicit case (current_city unset but GPS
                // available): GPS-based PostGIS distance sort layered on top
                // of whatever the (optional) city filter above already
                // produced. The city filter, when present, is a hard
                // boundary already baked into $query — this step only ranks
                // the resulting rows, it never pulls in cargo from a
                // different city. Same Tier A (PostGIS)/Tier B (PHP
                // Haversine fallback) split as the heavy-vehicle branch
                // below, minus the ST_DWithin radius filter (light vehicles
                // are never radius-limited, only city-limited).
                $tierA = (clone $query)
                    ->whereNotNull('pickup_point')
                    ->orderByRaw(
                        'pickup_point <-> ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography',
                        [$driverLng, $driverLat]
                    )
                    ->orderBy('cargo_requests.created_at', 'desc')
                    ->selectRaw(
                        'cargo_requests.*, ST_Distance(pickup_point, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography) / 1000 AS distance_km_pg',
                        [$driverLng, $driverLat]
                    )
                    ->get()
                    ->each(function ($c) {
                        $c->distance_km = round((float) $c->distance_km_pg, 1);
                    });

                // Tier B: rows without a stored pickup_point. Fallback is an
                // EXACT lookup of CityCoordinates::COORDS keyed on the
                // cargo's own `city` column (not a substring search over
                // pickup_location — that distinction only applies to the
                // intercity/heavy fallback), matching the original
                // algorithm's fallback shape.
                $tierB = (clone $query)
                    ->whereNull('pickup_point')
                    ->latest()
                    ->get()
                    ->map(function ($c) use ($driverLat, $driverLng) {
                        $coords = CityCoordinates::COORDS[$c->city] ?? null;
                        if (!$coords) {
                            $c->distance_km = null;
                            return $c;
                        }
                        [$pLat, $pLng] = $coords;
                        $c->distance_km = round(DistanceService::haversineKm($driverLat, $driverLng, $pLat, $pLng), 1);
                        return $c;
                    });

                // Unlike the heavy-vehicle branch, tierB is not filtered by
                // radius here (light vehicles have no radius concept), so
                // rows with an unresolvable distance can reach the final
                // sort. NULLS LAST, deliberately: the original code's plain
                // sortBy('distance_km') let PHP's null-comparison quirk
                // (null <=> N behaves like 0 <=> N) sort unresolvable rows
                // to the FRONT, as if they were 0 km away. That is worse
                // than putting them last, so we deviate from the literal
                // original here on this narrow point.
                $cargoList = $tierA->concat($tierB)
                    ->sortBy(fn ($c) => $c->distance_km ?? PHP_FLOAT_MAX)
                    ->values();

                return response()->json([
                    'data'           => CargoResource::collection($cargoList),
                    'location_unset' => $locationUnset,
                    'current_city'   => $currentCity,
                ]);
            }

            $query->where(function ($q) {
                $q->where('service_type', 'intercity')
                  ->orWhereNull('service_type');
            });

            // No known GPS position — cannot confirm which cargo is nearby,
            // so show none rather than everything (nearby-only visibility).
            if ($driverLat === null || $driverLng === null) {
                return response()->json([
                    'data'           => [],
                    'location_unset' => true,
                    'current_city'   => $vehicle?->current_city,
                ]);
            }

            // Radius (km) is admin-editable via PlatformSetting (Phase 2 Step 3,
            // key distinct from the drivers-search radius keys); read fresh on
            // every request so changes apply without a deploy. The driver can
            // override it per-request (e.g. widen to 100km) via ?radius_km=.
            $request->validate(['radius_km' => 'nullable|numeric|min:1|max:1000']);
            $radiusKm = $request->filled('radius_km')
                ? (float) $request->query('radius_km')
                : (float) PlatformSetting::get('spatial.cargo_search_radius_km', 50);
            $radiusMeters = $radiusKm * 1000;

            // Tier A: cargo rows that already have a real stored pickup_point —
            // the common case (CargoRequestController::store()'s city-centroid
            // fallback means almost every row has one). PostGIS-accelerated:
            // ST_DWithin does the radius filter using the
            // cargo_requests_pickup_point_gist index; the KNN `<->` operator
            // does the index-accelerated distance sort. created_at DESC is a
            // second ORDER BY key (tie-breaker) that reproduces what the prior
            // latest()->get()->sortBy('distance_km') (PHP's sort is stable)
            // used to do for rows with equal distance — very plausible here
            // since many cargo rows share the exact same city-centroid
            // fallback coordinates.
            $tierA = (clone $query)
                ->whereNotNull('pickup_point')
                ->whereRaw(
                    'ST_DWithin(pickup_point, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?)',
                    [$driverLng, $driverLat, $radiusMeters]
                )
                ->orderByRaw(
                    'pickup_point <-> ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography',
                    [$driverLng, $driverLat]
                )
                ->orderBy('cargo_requests.created_at', 'desc')
                ->selectRaw(
                    'cargo_requests.*, ST_Distance(pickup_point, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography) / 1000 AS distance_km_pg',
                    [$driverLng, $driverLat]
                )
                ->get()
                ->each(function ($c) {
                    $c->distance_km = round((float) $c->distance_km_pg, 1);
                });

            // Tier B: cargo rows without a stored pickup_point (rare/legacy —
            // currently 0 of 3 pending rows, but preserved as a safety net for
            // any row that slipped through without one). Same city-name-match
            // fallback logic as before, just isolated to this smaller subset
            // since Tier A now handles the rest via SQL.
            $tierB = (clone $query)
                ->whereNull('pickup_point')
                ->latest()
                ->get()
                ->map(function ($c) use ($driverLat, $driverLng) {
                    $cityKey = null;
                    $loc = $c->service_type === 'intracity' ? ($c->city ?? '') : ($c->pickup_location ?? '');
                    foreach (array_keys(CityCoordinates::COORDS) as $city) {
                        if (stripos($loc, $city) !== false) { $cityKey = $city; break; }
                    }
                    if (!$cityKey) { $c->distance_km = null; return $c; }
                    [$pLat, $pLng] = CityCoordinates::COORDS[$cityKey];
                    $c->distance_km = round(DistanceService::haversineKm($driverLat, $driverLng, $pLat, $pLng), 1);
                    return $c;
                })
                ->filter(fn ($c) => $c->distance_km !== null && $c->distance_km <= $radiusKm);

            $cargoList = $tierA->concat($tierB)
                ->sortBy('distance_km')
                ->values();

            return response()->json([
                'data'           => CargoResource::collection($cargoList),
                'location_unset' => false,
                'current_city'   => $vehicle?->current_city,
                'radius_km'      => $radiusKm,
            ]);
        }

        return CargoResource::collection($query->latest()->get());
    }

    // GET /freight — list for React frontend (camelCase shape)
    public function freightIndex(Request $request)
    {
        $this->cargoExpiry->expireOverdue();

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
        $this->cargoExpiry->expireOverdue();

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

        // Use the bidder's first available vehicle — fleet owners' vehicles
        // are linked via fleet_owner_id, not user_id.
        $vehicleColumn = $user->role === 'fleet_owner' ? 'fleet_owner_id' : 'user_id';

        try {
            $booking = DB::transaction(function () use ($cargo, $user, $vehicleColumn) {
                // Lock cargo first, then vehicle — same order used by BidService's
                // accept flows, so the two paths can never deadlock each other.
                $lockedCargo = CargoRequest::whereKey($cargo->id)->lockForUpdate()->firstOrFail();
                if ($lockedCargo->status !== 'pending') {
                    throw new \Exception('This cargo is no longer available.');
                }

                $vehicle = Vehicle::where($vehicleColumn, $user->id)
                    ->where('availability_status', 'available')
                    ->lockForUpdate()
                    ->first();

                if (!$vehicle) {
                    $vehicle = Vehicle::where($vehicleColumn, $user->id)->lockForUpdate()->first();
                }

                if (!$vehicle) {
                    throw new \Exception('You have no registered vehicle. Please register a vehicle first.');
                }

                // Cap at 2 (one active + one queued) — same rule as
                // BidService::assertVehicleFree(); TripService::startTrip()
                // is what stops two from being driven at once.
                $activeCount = Booking::where('vehicle_id', $vehicle->id)
                    ->whereNotIn('booking_status', ['cancelled', 'completed', 'delivered'])
                    ->count();
                if ($activeCount >= 2) {
                    throw new \Exception('Your vehicle already has an active trip and a queued one — finish or deliver one before taking on another.');
                }

                // For a fleet owner, the vehicle's assigned driver executes the trip,
                // not the fleet owner themself (vehicle->user_id falls back to the
                // owner's own id when the vehicle has no driver assigned yet).
                $driverId = $user->role === 'fleet_owner' ? $vehicle->user_id : $user->id;

                $booking = Booking::create([
                    'cargo_id'         => $lockedCargo->id,
                    'vehicle_id'       => $vehicle->id,
                    'driver_id'        => $driverId,
                    'booking_status'   => 'confirmed',
                    'estimated_price'  => $lockedCargo->budget,
                    'commission_fee'   => $lockedCargo->budget * config('services.commission_rate'),
                ]);

                $lockedCargo->update(['status' => 'matched']);
                $vehicle->update(['availability_status' => 'busy']);

                return $booking;
            });
        } catch (\Exception $e) {
            return response()->json(['message' => $e->getMessage()], 422);
        }

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

        $vehicleColumn = $user->role === 'fleet_owner' ? 'fleet_owner_id' : 'user_id';
        $vehicle = Vehicle::where($vehicleColumn, $user->id)
            ->where('availability_status', 'available')
            ->first()
            ?? Vehicle::where($vehicleColumn, $user->id)->first();

        if (!$vehicle) {
            return response()->json(['message' => 'You have no registered vehicle.'], 422);
        }

        // For a fleet owner, the bid is recorded against the vehicle's
        // assigned driver, not the fleet owner themself — same reasoning
        // as bookDirect() above.
        $bidder = $user->role === 'fleet_owner' ? ($vehicle->user ?? $user) : $user;

        try {
            $bid = $this->bidService->acceptFixedPrice($cargo, $bidder, $vehicle);
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
                foreach (array_keys(CityCoordinates::COORDS) as $city) {
                    if (stripos($cargoRequest->pickup_location, $city) !== false) {
                        $cityName = $city;
                        break;
                    }
                }
            }
            if ($cityName && isset(CityCoordinates::COORDS[$cityName])) {
                [$lat, $lng] = CityCoordinates::COORDS[$cityName];
                $cargoRequest->update(['pickup_lat' => $lat, 'pickup_lng' => $lng]);
            }
        }

        // Use GPS coords from client if supplied; otherwise fall back to city-centre lookup.
        if (!$cargoRequest->destination_lat || !$cargoRequest->destination_lng) {
            $destCityName = null;
            if ($cargoRequest->service_type === 'intracity') {
                $destCityName = $cargoRequest->city;
            } elseif ($cargoRequest->destination) {
                foreach (array_keys(CityCoordinates::COORDS) as $city) {
                    if (stripos($cargoRequest->destination, $city) !== false) {
                        $destCityName = $city;
                        break;
                    }
                }
            }
            if ($destCityName && isset(CityCoordinates::COORDS[$destCityName])) {
                [$dlat, $dlng] = CityCoordinates::COORDS[$destCityName];
                $cargoRequest->update(['destination_lat' => $dlat, 'destination_lng' => $dlng]);
            }
        }

        // GenerateBackhaulRecommendations otherwise only ever runs once, at
        // trip start — cargo posted after a driver is already en route would
        // never surface as a backhaul opportunity. Re-run it for every
        // ongoing trip now that new pending cargo exists; the job is
        // idempotent (updateOrCreate keyed on trip/driver/cargo) so this is
        // safe to fire repeatedly and cheap when nothing new matches.
        if ($cargoRequest->status === 'pending') {
            Trip::where('trip_status', 'ongoing')->each(
                fn (Trip $trip) => GenerateBackhaulRecommendations::dispatch($trip)
            );
        }

        return (new CargoResource($cargoRequest))->response()->setStatusCode(201);
    }

    /**
     * GET /nearby-drivers/by-location?lat=X&lng=Y&serviceType=intercity|intracity
     * Query nearby available drivers by pickup coordinates.
     * Used by shippers during cargo creation to preview available drivers.
     * Returns empty array if coordinates are invalid or no drivers are available.
     */
    public function nearbyDriversByLocation(Request $request)
    {
        $lat = $request->query('lat');
        $lng = $request->query('lng');
        $serviceType = $request->query('serviceType', 'intercity');

        if (!$lat || !$lng) {
            return response()->json(['drivers' => []]);
        }

        $pickupLat = (float) $lat;
        $pickupLng = (float) $lng;

        // Validate coordinates
        if ($pickupLat < -90 || $pickupLat > 90 || $pickupLng < -180 || $pickupLng > 180) {
            return response()->json(['drivers' => []]);
        }

        $requiredCategory = $serviceType === 'intracity' ? 'light' : 'heavy';
        $cutoff = now()->subHours(24);

        // Radius (km) is admin-editable via PlatformSetting (Phase 2 Step 2);
        // read fresh on every request so changes apply without a deploy.
        $radiusKm = (float) PlatformSetting::get('spatial.nearby_drivers_radius_km', 50);
        $radiusMeters = $radiusKm * 1000;

        // PostGIS-accelerated spatial query (pilot for Phase 1 of the PostGIS
        // migration). Non-spatial filters (category, availability, freshness,
        // driver verification) are unchanged from the prior Haversine-in-PHP
        // implementation. ST_DWithin does the radius filter using the
        // vehicles_position_gist index; the KNN `<->` operator does the
        // index-accelerated distance sort. Floats are bound via parameter
        // binding (whereRaw/orderByRaw/selectRaw placeholders), not string
        // interpolation.
        $vehicles = Vehicle::with('user')
            ->where('vehicle_category', $requiredCategory)
            ->where('availability_status', 'available')
            ->whereNotNull('position')
            ->where('last_location_at', '>=', $cutoff)
            ->whereRaw(
                'ST_DWithin(position, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?)',
                [$pickupLng, $pickupLat, $radiusMeters]
            )
            ->orderByRaw(
                'position <-> ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography',
                [$pickupLng, $pickupLat]
            )
            ->selectRaw(
                'vehicles.*, ST_Distance(position, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography) / 1000 AS distance_km_pg',
                [$pickupLng, $pickupLat]
            )
            ->get();

        $drivers = $vehicles->map(function ($v) {
            $driver = $v->user;
            if (!$driver || !$driver->verification_status) return null;

            $dist = (float) $v->distance_km_pg;
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
                'trip_count'    => $driver->trip_count ?? 0,
            ];
        })->filter()->sortBy('distance_km')->values();

        return response()->json(['drivers' => $drivers]);
    }

    /**
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
                foreach (CityCoordinates::COORDS as $city => $c) {
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

        // Radius (km) is admin-editable via PlatformSetting (Phase 2 Step 2);
        // read fresh on every request so changes apply without a deploy.
        $radiusKm = (float) PlatformSetting::get('spatial.nearby_drivers_radius_km', 50);
        $radiusMeters = $radiusKm * 1000;

        $vehicles = Vehicle::with('user')
            ->where('vehicle_category', $requiredCategory)
            ->where('availability_status', 'available')
            ->whereNotNull('position')
            ->where('last_location_at', '>=', $cutoff)
            ->whereRaw(
                'ST_DWithin(position, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?)',
                [$pickupLng, $pickupLat, $radiusMeters]
            )
            ->orderByRaw(
                'position <-> ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography',
                [$pickupLng, $pickupLat]
            )
            ->selectRaw(
                'vehicles.*, ST_Distance(position, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography) / 1000 AS distance_km_pg',
                [$pickupLng, $pickupLat]
            )
            ->get();

        $drivers = $vehicles->map(function ($v) {
            $driver = $v->user;
            if (!$driver || !$driver->verification_status) return null;

            $dist = (float) $v->distance_km_pg;
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

        try {
            $cargoRequest = DB::transaction(function () use ($cargoRequest, $user, $request) {
                // Re-check under lock — a bid-accept may have matched this
                // cargo between the read above and this transaction starting.
                $locked = CargoRequest::whereKey($cargoRequest->id)->lockForUpdate()->firstOrFail();

                if (!$user->is_admin && $locked->status !== 'pending') {
                    throw new \Exception('Cargo can only be edited while pending.');
                }

                $locked->update($request->validated());
                return $locked;
            });
        } catch (\Exception $e) {
            return response()->json(['message' => $e->getMessage()], 422);
        }

        return new CargoResource($cargoRequest);
    }

    /**
     * POST /cargo-requests/{cargo}/repost
     * Shipper reopens bidding on cargo whose deadline already passed
     * (status === 'expired', set by CargoExpiryService) by setting a new,
     * future bid_deadline. Deliberately separate from update() above, which
     * stays gated to status === 'pending' only — reopening is an explicit
     * action, not a general edit. Old bids from the expired round are left
     * as historical 'expired'/'rejected' rows; drivers are not re-notified.
     */
    public function repost(Request $request, CargoRequest $cargo)
    {
        $cargoRequest = $cargo;
        $user = auth()->user();

        if (!$user->is_admin && $cargoRequest->user_id !== $user->id) {
            return response()->json(['message' => 'Forbidden'], 403);
        }

        $validated = $request->validate([
            'bid_deadline' => 'required|date|after:now',
        ]);

        try {
            $cargoRequest = DB::transaction(function () use ($cargoRequest, $validated) {
                // Re-check under lock — a lazy expiry sweep or another
                // request could change status between the read above and here.
                $locked = CargoRequest::whereKey($cargoRequest->id)->lockForUpdate()->firstOrFail();

                if ($locked->status !== 'expired') {
                    throw new \Exception('Only cargo whose bidding has closed can be reposted.');
                }

                $locked->update([
                    'bid_deadline' => $validated['bid_deadline'],
                    'status'       => 'pending',
                ]);

                return $locked;
            });
        } catch (\Exception $e) {
            return response()->json(['message' => $e->getMessage()], 422);
        }

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

        try {
            DB::transaction(function () use ($cargoRequest, $user) {
                // Re-check under lock: without this, a bid-accept racing this
                // request could commit a Booking for this cargo right after the
                // check above passed, and the delete()/cascade below would then
                // silently wipe out that just-created Booking.
                $locked = CargoRequest::whereKey($cargoRequest->id)->lockForUpdate()->firstOrFail();

                if (!$user->is_admin && $locked->status !== 'pending') {
                    throw new \Exception('Only pending cargo can be cancelled. A booking already exists for this cargo.');
                }

                // Notify every driver who bid on this cargo, then remove their bids
                $pendingBids = $locked->bids()
                    ->with('driver')
                    ->whereIn('status', ['pending', 'countered'])
                    ->get();

                foreach ($pendingBids as $bid) {
                    $bid->driver?->notify(
                        new \App\Notifications\BidRejectedNotification($bid, 'cargo_cancelled')
                    );
                }

                // Remove all bids (FK constraint) then delete the cargo
                $locked->bids()->delete();
                $locked->delete();
            });
        } catch (\Exception $e) {
            return response()->json(['message' => $e->getMessage()], 422);
        }

        return response()->json(['message' => 'Cargo cancelled and all pending bids have been notified.']);
    }
}
