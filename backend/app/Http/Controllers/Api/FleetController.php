<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\Booking;
use App\Models\CargoRequest;
use App\Models\User;
use App\Models\Vehicle;
use App\Services\FleetService;
use App\Support\PhoneNumber;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;

class FleetController extends Controller
{
    public function __construct(protected FleetService $fleetService) {}

    /**
     * Get the fleet owner's dashboard summary.
     */
    public function dashboard()
    {
        $owner = auth()->user();
        if (!$owner->is_fleet_owner && !$owner->is_admin) {
            return response()->json(['message' => 'Forbidden'], 403);
        }

        $drivers  = User::where('fleet_owner_id', $owner->id)->get();
        $vehicles = Vehicle::where('fleet_owner_id', $owner->id)->get();

        // Active bookings assigned to any of this fleet's drivers
        $driverIds = $drivers->pluck('id');
        $activeBookings = Booking::whereIn('driver_id', $driverIds)
            ->whereNotIn('booking_status', ['completed'])
            ->with('cargoRequest')
            ->get();

        return response()->json([
            'data' => [
                'driver_count'        => $drivers->count(),
                'vehicle_count'       => $vehicles->count(),
                'active_booking_count' => $activeBookings->count(),
                'drivers'             => $drivers->map(fn ($d) => [
                    'id'       => $d->id,
                    'name'     => $d->full_name,
                    'phone'    => $d->phone,
                    'verified' => $d->verification_status,
                ]),
                'vehicles' => $vehicles->map(fn ($v) => [
                    'id'                  => $v->id,
                    'plate_number'        => $v->plate_number,
                    'truck_type'          => $v->truck_type,
                    'capacity'            => $v->capacity,
                    'current_city'        => $v->current_city,
                    'availability_status' => $v->availability_status,
                    'driver_id'           => $v->user_id,
                    'verification_status' => (bool) $v->verification_status,
                    'latitude'            => $v->latitude,
                    'longitude'           => $v->longitude,
                    'last_location_at'    => $v->last_location_at?->toISOString(),
                ]),
                'active_bookings' => $activeBookings->map(fn ($b) => [
                    'id'             => $b->id,
                    'driver_id'      => $b->driver_id,
                    'booking_status' => $b->booking_status,
                    'route'          => $b->cargoRequest
                        ? $b->cargoRequest->pickup_location . ' → ' . $b->cargoRequest->destination
                        : 'N/A',
                    'estimated_price' => $b->estimated_price,
                ]),
            ],
        ]);
    }

    /**
     * Add a driver to this fleet.
     * The driver must already be registered; this links them to the fleet owner.
     */
    public function addDriver(Request $request)
    {
        $owner = auth()->user();
        if (!$owner->is_fleet_owner && !$owner->is_admin) {
            return response()->json(['message' => 'Forbidden'], 403);
        }

        $request->validate([
            'driver_id' => 'required|exists:users,id',
        ]);

        $driver = User::findOrFail($request->driver_id);

        if ($driver->role !== 'driver') {
            return response()->json(['message' => 'User is not a driver'], 422);
        }

        if ($driver->fleet_owner_id && $driver->fleet_owner_id !== $owner->id) {
            return response()->json(['message' => 'Driver already belongs to another fleet'], 422);
        }

        $driver->update(['fleet_owner_id' => $owner->id]);

        return response()->json([
            'message' => 'Driver added to fleet successfully',
            'data'    => [
                'driver_id'   => $driver->id,
                'driver_name' => $driver->full_name,
                'phone'       => $driver->phone,
            ],
        ]);
    }

    /**
     * Create a brand-new driver account directly under this fleet.
     * Initial password is the driver's own phone number (they can change
     * it afterward via the existing Change Password screen).
     */
    public function createDriver(Request $request)
    {
        $owner = auth()->user();
        if (!$owner->is_fleet_owner && !$owner->is_admin) {
            return response()->json(['message' => 'Forbidden'], 403);
        }

        if ($request->has('phone')) {
            $request->merge(['phone' => PhoneNumber::normalize($request->input('phone'))]);
        }

        $validated = $request->validate([
            'full_name' => 'required|string|max:255',
            'phone'     => ['required', 'string', 'regex:' . PhoneNumber::FORMAT_REGEX, 'unique:users,phone'],
            'email'     => 'nullable|email|unique:users,email',
        ], [
            'phone.regex' => 'Enter a valid Ethiopian phone number, e.g. 0912345678.',
        ]);

        $driver = User::create([
            'full_name'            => $validated['full_name'],
            'phone'                => $validated['phone'],
            'email'                => $validated['email'] ?? null,
            'password'             => \Illuminate\Support\Facades\Hash::make($validated['phone']),
            'role'                 => 'driver',
            'fleet_owner_id'       => $owner->id,
            'verification_status'  => false,
            'is_active'            => false,
        ]);

        return response()->json([
            'message' => 'Driver account created. Initial password is their phone number — ask them to change it after first login.',
            'data'    => [
                'driver_id'   => $driver->id,
                'driver_name' => $driver->full_name,
                'phone'       => $driver->phone,
            ],
        ], 201);
    }

    /**
     * Remove a driver from this fleet.
     */
    public function removeDriver(Request $request, string $driverId)
    {
        $owner = auth()->user();
        if (!$owner->is_fleet_owner && !$owner->is_admin) {
            return response()->json(['message' => 'Forbidden'], 403);
        }

        $driver = User::where('id', $driverId)
                      ->where('fleet_owner_id', $owner->id)
                      ->firstOrFail();

        $driver->update(['fleet_owner_id' => null]);

        return response()->json(['message' => 'Driver removed from fleet']);
    }

    /**
     * Register a vehicle directly under this fleet.
     */
    public function addVehicle(Request $request)
    {
        $owner = auth()->user();
        if (!$owner->is_fleet_owner && !$owner->is_admin) {
            return response()->json(['message' => 'Forbidden'], 403);
        }

        $validated = $request->validate([
            'truck_type'       => 'required|string',
            'plate_number'     => 'required|string|unique:vehicles,plate_number',
            'capacity'         => 'required|numeric|min:0.01',
            'current_city'     => 'required|string',
            'driver_id'        => 'nullable|exists:users,id', // optionally assign a driver
            'vehicle_category' => 'nullable|in:light,heavy',
        ]);

        $vehicle = Vehicle::create([
            'user_id'             => $validated['driver_id'] ?? $owner->id,
            'fleet_owner_id'      => $owner->id,
            'truck_type'          => $validated['truck_type'],
            'vehicle_category'    => $request->input('vehicle_category', 'heavy'),
            'plate_number'        => $validated['plate_number'],
            'capacity'            => $validated['capacity'],
            'current_city'        => $validated['current_city'],
            'availability_status' => 'available',
            'rating'              => 0,
        ]);

        return response()->json([
            'message' => 'Vehicle added to fleet',
            'data'    => $vehicle,
        ], 201);
    }

    /**
     * Assign a vehicle to a driver within the fleet.
     */
    public function assignVehicle(Request $request, string $vehicleId)
    {
        $owner = auth()->user();
        if (!$owner->is_fleet_owner && !$owner->is_admin) {
            return response()->json(['message' => 'Forbidden'], 403);
        }

        $request->validate(['driver_id' => 'required|exists:users,id']);

        $vehicle = Vehicle::where('id', $vehicleId)
                          ->where('fleet_owner_id', $owner->id)
                          ->firstOrFail();

        $driver = User::where('id', $request->driver_id)
                      ->where('fleet_owner_id', $owner->id)
                      ->firstOrFail();

        if (!$vehicle->verification_status) {
            return response()->json(['message' => 'This vehicle is not yet verified. All required vehicle documents must be approved first.'], 422);
        }
        if (!$driver->verification_status) {
            return response()->json(['message' => 'This driver is not yet verified. All required driver documents must be approved first.'], 422);
        }

        $vehicle->update(['user_id' => $driver->id]);

        return response()->json([
            'message' => "Vehicle {$vehicle->plate_number} assigned to {$driver->full_name}",
        ]);
    }

    /**
     * Dispatch a specific booking to a fleet driver.
     * Updates the booking's driver_id to the chosen driver.
     */
    public function dispatchBooking(Request $request, string $bookingId)
    {
        $owner = auth()->user();
        if (!$owner->is_fleet_owner && !$owner->is_admin) {
            return response()->json(['message' => 'Forbidden'], 403);
        }

        $request->validate(['driver_id' => 'required|exists:users,id']);

        $booking = Booking::findOrFail($bookingId);

        // Verify the booking's vehicle belongs to this fleet
        $vehicle = Vehicle::where('id', $booking->vehicle_id)
                          ->where('fleet_owner_id', $owner->id)
                          ->first();

        if (!$vehicle) {
            return response()->json(['message' => 'This booking is not for a fleet vehicle'], 403);
        }

        $driver = User::where('id', $request->driver_id)
                      ->where('fleet_owner_id', $owner->id)
                      ->firstOrFail();

        if (!$vehicle->verification_status) {
            return response()->json(['message' => 'This vehicle is not yet verified.'], 422);
        }
        if (!$driver->verification_status) {
            return response()->json(['message' => 'This driver is not yet verified.'], 422);
        }

        $booking->update([
            'driver_id'      => $driver->id,
            'booking_status' => 'accepted', // auto-accept when owner dispatches
        ]);

        return response()->json([
            'message' => "Booking #{$booking->id} dispatched to {$driver->full_name}",
            'data'    => [
                'booking_id' => $booking->id,
                'driver'     => $driver->full_name,
                'phone'      => $driver->phone,
            ],
        ]);
    }

    /**
     * GET /fleet/available-cargo
     * Pending cargo requests from shippers — fleet owner picks one to dispatch.
     */
    public function availableCargo()
    {
        $owner = auth()->user();
        if (!$owner->is_fleet_owner && !$owner->is_admin) {
            return response()->json(['message' => 'Forbidden'], 403);
        }

        $city     = request('city');
        $lat      = request('lat') !== null ? (float) request('lat') : null;
        $lng      = request('lng') !== null ? (float) request('lng') : null;
        $radiusKm = request('radius_km') !== null ? (float) request('radius_km') : null;

        $cargoList = $this->fleetService->searchCargo($city, $lat, $lng, $radiusKm);

        $cargo = $cargoList
            ->load('user')
            ->map(fn ($c) => [
                'id'                  => $c->id,
                'pickup_location'     => $c->pickup_location,
                'destination'         => $c->destination,
                'material_type'       => $c->material_type,
                'weight'              => $c->weight,
                'urgency_level'       => $c->urgency_level,
                'budget'              => $c->budget,
                'price_type'          => $c->price_type ?? 'negotiable',
                'service_type'        => $c->service_type ?? 'intercity',
                'vehicle_type_needed' => $c->vehicle_type_needed,
                'status'              => $c->status,
                'shipper_name'        => $c->user?->full_name ?? 'Unknown',
                'shipper_phone'       => $c->user?->phone ?? '',
                'distance_km'         => $c->distance_km ?? null,
                'created_at'          => $c->created_at,
            ]);

        return response()->json(['data' => $cargo]);
    }

    /**
     * GET /fleet/cargo/{cargo}/candidates
     * Ranked list of this fleet's drivers/vehicles suitable for the given cargo.
     */
    public function cargoCandidates(CargoRequest $cargo)
    {
        $owner = auth()->user();
        if (!$owner->is_fleet_owner && !$owner->is_admin) {
            return response()->json(['message' => 'Forbidden'], 403);
        }

        return response()->json([
            'data' => $this->fleetService->rankCandidatesForCargo($owner, $cargo),
        ]);
    }

    /**
     * POST /fleet/bookings
     * Fleet owner creates a booking and immediately assigns it to one of their drivers.
     * This replaces the shipper-accepts-bid flow for fleet-direct dispatches.
     */
    public function createBooking(Request $request)
    {
        $owner = auth()->user();
        if (!$owner->is_fleet_owner && !$owner->is_admin) {
            return response()->json(['message' => 'Forbidden'], 403);
        }

        $request->validate([
            'cargo_id'        => 'required|exists:cargo_requests,id',
            'vehicle_id'      => 'required|exists:vehicles,id',
            'driver_id'       => 'required|exists:users,id',
            'estimated_price' => 'required|numeric|min:0',
        ]);

        $vehicle = Vehicle::where('id', $request->vehicle_id)
                          ->where('fleet_owner_id', $owner->id)
                          ->firstOrFail();

        $driver = User::where('id', $request->driver_id)
                      ->where('fleet_owner_id', $owner->id)
                      ->firstOrFail();

        if (!$vehicle->verification_status) {
            return response()->json(['message' => 'This vehicle is not yet verified.'], 422);
        }
        if (!$driver->verification_status) {
            return response()->json(['message' => 'This driver is not yet verified.'], 422);
        }

        try {
            $booking = DB::transaction(function () use ($request, $vehicle, $driver) {
                // Lock cargo first, then vehicle — same order used throughout the
                // bid-accept and book-direct flows, to avoid cross-flow deadlocks.
                $cargo = CargoRequest::whereKey($request->cargo_id)->lockForUpdate()->firstOrFail();
                if ($cargo->status !== 'pending') {
                    throw new \Exception('This cargo is no longer available — it may have just been booked.');
                }

                $lockedVehicle = Vehicle::whereKey($vehicle->id)->lockForUpdate()->firstOrFail();

                // Cap at 2 (one active + one queued) — same rule as
                // BidService::assertVehicleFree(); TripService::startTrip()
                // is what stops two from being driven at once.
                $activeCount = Booking::where('vehicle_id', $lockedVehicle->id)
                    ->whereNotIn('booking_status', ['cancelled', 'completed', 'delivered'])
                    ->count();
                if ($activeCount >= 2) {
                    throw new \Exception('This vehicle already has an active trip and a queued one — finish or deliver one before taking on another.');
                }

                $booking = Booking::create([
                    'cargo_id'        => $cargo->id,
                    'vehicle_id'      => $lockedVehicle->id,
                    'driver_id'       => $driver->id,
                    'booking_status'  => 'accepted',
                    'estimated_price' => $request->estimated_price,
                    'commission_fee'  => round($request->estimated_price * 0.05, 2),
                ]);

                $cargo->update(['status' => 'matched']);

                return $booking;
            });
        } catch (\Exception $e) {
            return response()->json(['message' => $e->getMessage()], 422);
        }

        return response()->json([
            'message' => "Booking #{$booking->id} dispatched to {$driver->full_name}",
            'data'    => [
                'booking_id'  => $booking->id,
                'driver_name' => $driver->full_name,
                'driver_phone'=> $driver->phone,
                'vehicle'     => $vehicle->plate_number,
                'status'      => $booking->booking_status,
            ],
        ], 201);
    }

    /**
     * List all drivers in the fleet.
     */
    public function drivers()
    {
        $owner = auth()->user();
        $drivers = User::where('fleet_owner_id', $owner->id)->get();
        return response()->json(['data' => $drivers]);
    }

    /**
     * List all vehicles in the fleet.
     */
    public function vehicles()
    {
        $owner = auth()->user();
        $vehicles = Vehicle::where('fleet_owner_id', $owner->id)
                           ->with('user')
                           ->get();
        return response()->json(['data' => $vehicles]);
    }

    /**
     * GET /fleet/drivers/{driverId}
     * Full detail for one driver in this fleet: profile + document progress + assigned vehicle.
     */
    public function driverDetail(string $driverId)
    {
        $owner = auth()->user();
        if (!$owner->is_fleet_owner && !$owner->is_admin) {
            return response()->json(['message' => 'Forbidden'], 403);
        }

        $driver = User::where('id', $driverId)->where('fleet_owner_id', $owner->id)->firstOrFail();

        $requiredTypes = ['license', 'national_id', 'vehicle_registration', 'insurance', 'tin'];
        $approvedCount = \App\Models\DriverDocument::where('user_id', $driver->id)
            ->where('status', 'approved')
            ->whereIn('document_type', $requiredTypes)
            ->count();

        $vehicle = Vehicle::where('user_id', $driver->id)->where('fleet_owner_id', $owner->id)->first();

        return response()->json(['data' => [
            'id' => $driver->id,
            'full_name' => $driver->full_name,
            'phone' => $driver->phone,
            'email' => $driver->email,
            'verification_status' => (bool) $driver->verification_status,
            'is_active' => (bool) $driver->is_active,
            'created_at' => $driver->created_at,
            'documents_approved' => $approvedCount,
            'documents_required' => count($requiredTypes),
            'assigned_vehicle' => $vehicle ? [
                'id' => $vehicle->id,
                'plate_number' => $vehicle->plate_number,
                'truck_type' => $vehicle->truck_type,
            ] : null,
        ]]);
    }

    /**
     * PATCH /fleet/drivers/{driverId}
     * Update a driver's own profile fields. Deliberately excludes role, password,
     * verification_status, is_active, fleet_owner_id — those stay admin/document-approval controlled.
     */
    public function updateDriver(Request $request, string $driverId)
    {
        $owner = auth()->user();
        if (!$owner->is_fleet_owner && !$owner->is_admin) {
            return response()->json(['message' => 'Forbidden'], 403);
        }

        $driver = User::where('id', $driverId)->where('fleet_owner_id', $owner->id)->firstOrFail();

        if ($request->has('phone')) {
            $request->merge(['phone' => PhoneNumber::normalize($request->input('phone'))]);
        }

        $validated = $request->validate([
            'full_name' => 'sometimes|string|max:255',
            'phone'     => ['sometimes', 'string', 'regex:' . PhoneNumber::FORMAT_REGEX, 'unique:users,phone,' . $driver->id],
            'email'     => 'nullable|email|unique:users,email,' . $driver->id,
        ], [
            'phone.regex' => 'Enter a valid Ethiopian phone number, e.g. 0912345678.',
        ]);

        $driver->update($validated);

        return response()->json([
            'message' => 'Driver updated',
            'data' => [
                'id' => $driver->id,
                'full_name' => $driver->full_name,
                'phone' => $driver->phone,
                'email' => $driver->email,
            ],
        ]);
    }

    /**
     * DELETE /fleet/drivers/{driverId}/permanently
     * Hard-deletes a driver account. Blocked if they have booking/trip history
     * (mirrors AdminApiController::deleteUser's guard) since bookings.driver_id
     * cascades on delete and that history must be preserved. Any vehicle currently
     * assigned to this driver is first reassigned back to the fleet owner so the
     * users.id -> vehicles.user_id cascade-delete FK never fires on it.
     */
    public function deleteDriverAccount(string $driverId)
    {
        $owner = auth()->user();
        if (!$owner->is_fleet_owner && !$owner->is_admin) {
            return response()->json(['message' => 'Forbidden'], 403);
        }

        $driver = User::where('id', $driverId)->where('fleet_owner_id', $owner->id)->firstOrFail();

        if (\App\Models\Booking::where('driver_id', $driver->id)->exists()) {
            return response()->json([
                'message' => 'This driver has booking or trip history and cannot be permanently deleted. Remove them from the fleet instead to preserve records.',
                'hint' => 'DELETE /fleet/drivers/' . $driver->id,
            ], 422);
        }

        Vehicle::where('user_id', $driver->id)->where('fleet_owner_id', $owner->id)->update(['user_id' => $owner->id]);

        $driver->delete();

        return response()->json(['message' => 'Driver account permanently deleted']);
    }

    /**
     * GET /fleet/vehicles/{vehicleId}
     * Full detail for one vehicle in this fleet: profile + document progress + assigned driver.
     */
    public function vehicleDetail(string $vehicleId)
    {
        $owner = auth()->user();
        if (!$owner->is_fleet_owner && !$owner->is_admin) {
            return response()->json(['message' => 'Forbidden'], 403);
        }

        $vehicle = Vehicle::where('id', $vehicleId)->where('fleet_owner_id', $owner->id)->with('user')->firstOrFail();

        $approvedCount = \App\Models\VehicleDocument::where('vehicle_id', $vehicle->id)->where('status', 'approved')->count();

        return response()->json(['data' => [
            'id' => $vehicle->id,
            'plate_number' => $vehicle->plate_number,
            'truck_type' => $vehicle->truck_type,
            'vehicle_category' => $vehicle->vehicle_category,
            'capacity' => $vehicle->capacity,
            'current_city' => $vehicle->current_city,
            'availability_status' => $vehicle->availability_status,
            'verification_status' => (bool) $vehicle->verification_status,
            'latitude' => $vehicle->latitude,
            'longitude' => $vehicle->longitude,
            'last_location_at' => $vehicle->last_location_at?->toISOString(),
            'documents_approved' => $approvedCount,
            'documents_required' => 4,
            'assigned_driver' => ($vehicle->user_id && $vehicle->user_id !== $owner->id && $vehicle->user) ? [
                'id' => $vehicle->user->id,
                'full_name' => $vehicle->user->full_name,
                'phone' => $vehicle->user->phone,
            ] : null,
        ]]);
    }

    /**
     * GET /fleet/live-map
     * Every fleet vehicle's last known GPS position, its assigned driver (if any),
     * and its currently-active trip (if any) so the frontend can render an overview
     * map and link into GET /trips/{trip}/location for turn-by-turn detail.
     */
    public function liveMap()
    {
        $owner = auth()->user();
        if (!$owner->is_fleet_owner && !$owner->is_admin) {
            return response()->json(['message' => 'Forbidden'], 403);
        }

        $vehicles = Vehicle::where('fleet_owner_id', $owner->id)->with('user')->get();

        $driverIds = $vehicles->pluck('user_id')->filter(fn ($id) => $id !== $owner->id)->unique();

        $activeBookingsByDriver = Booking::whereIn('driver_id', $driverIds)
            ->whereNotIn('booking_status', ['completed', 'cancelled'])
            ->with(['trip', 'cargoRequest'])
            ->get()
            ->keyBy('driver_id');

        $data = $vehicles->map(function ($v) use ($owner, $activeBookingsByDriver) {
            $hasDriver = $v->user_id && $v->user_id !== $owner->id && $v->user;
            $booking = $hasDriver ? $activeBookingsByDriver->get($v->user_id) : null;
            $trip = $booking?->trip;
            $tripIsOngoing = $trip && $trip->trip_status === 'ongoing';

            return [
                'vehicle_id'          => $v->id,
                'plate_number'        => $v->plate_number,
                'truck_type'          => $v->truck_type,
                'latitude'            => $v->latitude,
                'longitude'           => $v->longitude,
                'last_location_at'    => $v->last_location_at?->toISOString(),
                'availability_status' => $v->availability_status,
                'verification_status' => (bool) $v->verification_status,
                'driver' => $hasDriver ? [
                    'id'        => $v->user->id,
                    'full_name' => $v->user->full_name,
                    'phone'     => $v->user->phone,
                ] : null,
                'active_trip' => $tripIsOngoing ? [
                    'trip_id'        => $trip->id,
                    'booking_id'     => $booking->id,
                    'destination'    => $trip->destination,
                    'booking_status' => $booking->booking_status,
                    'service_type'   => $booking->cargoRequest?->service_type ?? 'intercity',
                ] : null,
            ];
        });

        return response()->json(['data' => $data]);
    }
}
