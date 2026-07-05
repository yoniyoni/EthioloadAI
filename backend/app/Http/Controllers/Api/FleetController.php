<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\Booking;
use App\Models\CargoRequest;
use App\Models\User;
use App\Models\Vehicle;
use Illuminate\Http\Request;

class FleetController extends Controller
{
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
            'truck_type'   => 'required|string',
            'plate_number' => 'required|string|unique:vehicles,plate_number',
            'capacity'     => 'required|numeric',
            'current_city' => 'required|string',
            'driver_id'    => 'nullable|exists:users,id', // optionally assign a driver
        ]);

        $vehicle = Vehicle::create([
            'user_id'             => $validated['driver_id'] ?? $owner->id,
            'fleet_owner_id'      => $owner->id,
            'truck_type'          => $validated['truck_type'],
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

        $cargo = CargoRequest::where('status', 'pending')
            ->with('user')
            ->latest()
            ->get()
            ->map(fn ($c) => [
                'id'              => $c->id,
                'pickup_location' => $c->pickup_location,
                'destination'     => $c->destination,
                'material_type'   => $c->material_type,
                'weight'          => $c->weight,
                'urgency_level'   => $c->urgency_level,
                'budget'          => $c->budget,
                'status'          => $c->status,
                'shipper_name'    => $c->user?->full_name ?? 'Unknown',
                'shipper_phone'   => $c->user?->phone ?? '',
                'created_at'      => $c->created_at,
            ]);

        return response()->json(['data' => $cargo]);
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

        $booking = Booking::create([
            'cargo_id'        => $request->cargo_id,
            'vehicle_id'      => $vehicle->id,
            'driver_id'       => $driver->id,
            'booking_status'  => 'accepted',
            'estimated_price' => $request->estimated_price,
            'commission_fee'  => round($request->estimated_price * 0.05, 2),
        ]);

        CargoRequest::where('id', $request->cargo_id)->update(['status' => 'matched']);

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
}
