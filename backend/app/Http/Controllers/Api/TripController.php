<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Http\Requests\TripCreateRequest;
use App\Http\Requests\TripUpdateStatusRequest;
use App\Http\Requests\TripLocationUpdateRequest;
use App\Jobs\GenerateBackhaulRecommendations;
use App\Models\Booking;
use App\Models\Trip;
use App\Models\Vehicle;
use App\Services\TripService;
use Illuminate\Support\Facades\Log;

class TripController extends Controller
{
    protected $tripService;

    public function __construct(TripService $tripService)
    {
        $this->tripService = $tripService;
    }

    /**
     * GET /trips  — Admin only: list all trips with booking + cargo + driver info.
     */
    public function index()
    {
        $trips = Trip::with([
            'booking.cargoRequest',
            'booking.driver',
            'booking.vehicle',
            'tripStops.cargoRequest',
        ])->latest()->take(50)->get();

        return response()->json([
            'data' => $trips->map(fn (Trip $trip) => array_merge($trip->toArray(), [
                'total_amount'          => $trip->total_amount,
                'total_amount_formatted' => 'ETB ' . number_format($trip->total_amount, 0, '.', ','),
                'stops'                 => $trip->tripStops->map(fn ($s) => [
                    'id'             => $s->id,
                    'stop_order'     => $s->stop_order,
                    'location_name'  => $s->location_name,
                    'agreed_price'   => $s->agreed_price,
                    'status'         => $s->status,
                    'cargo_material' => $s->cargoRequest?->material_type,
                    'cargo_weight'   => $s->cargoRequest?->weight,
                    'arrived_at'     => $s->arrived_at,
                    'completed_at'   => $s->completed_at,
                ]),
            ]))->values(),
        ]);
    }

    public function store(TripCreateRequest $request)
    {
        $booking = Booking::findOrFail($request->booking_id);

        $user = auth()->user();
        if (!$user->is_admin && $booking->driver_id !== $user->id) {
            return response()->json(['message' => 'Forbidden'], 403);
        }

        if ($booking->trip) {
            return response()->json(['message' => 'Trip already exists for this booking'], 400);
        }

        $trip = $this->tripService->startTrip($booking);

        // Generate backhaul recommendations asynchronously (non-blocking)
        GenerateBackhaulRecommendations::dispatch($trip);

        return response()->json([
            'message' => 'Trip started successfully',
            'data'    => $trip,
            'backhaul_recommendations_pending' => true,
        ], 201);
    }

    public function updateStatus(TripUpdateStatusRequest $request, string $id)
    {
        $trip = Trip::findOrFail($id);
        
        $user = auth()->user();
        if (!$user->is_admin && $trip->booking->driver_id !== $user->id) {
            return response()->json(['message' => 'Forbidden'], 403);
        }

        if ($request->trip_status === 'completed') {
            try {
                $trip = $this->tripService->completeTrip($trip);
                $trip->load('booking');
            } catch (\Throwable $e) {
                Log::error('Trip completion failed', [
                    'trip_id' => $id,
                    'error'   => $e->getMessage(),
                    'trace'   => $e->getTraceAsString(),
                ]);
                return response()->json(['message' => $e->getMessage()], 422);
            }
        } else {
            $trip->update(['trip_status' => $request->trip_status]);
        }

        return response()->json([
            'data' => array_merge($trip->toArray(), [
                'booking_estimated_price' => $trip->booking?->estimated_price,
                'booking_commission_fee'  => $trip->booking?->commission_fee,
            ]),
        ]);
    }

    public function updateLocation(TripLocationUpdateRequest $request, string $id)
    {
        $trip = Trip::findOrFail($id);
        
        $user = auth()->user();
        if (!$user->is_admin && $trip->booking->driver_id !== $user->id) {
            return response()->json(['message' => 'Forbidden'], 403);
        }

        $trip = $this->tripService->updateLocation($trip, $request->validated());

        return response()->json(['data' => $trip]);
    }

    public function show(string $id)
    {
        $trip = Trip::findOrFail($id);

        $user = auth()->user();
        $canView = $user->is_admin
            || $trip->booking->driver_id === $user->id
            || ($trip->booking->cargoRequest && $trip->booking->cargoRequest->user_id === $user->id);

        if (!$canView) {
            return response()->json(['message' => 'Forbidden'], 403);
        }

        return response()->json(['data' => $trip]);
    }

    /**
     * GET /trips/{trip}/location
     * Returns the driver's last known position and recent route breadcrumbs.
     * Accessible by: shipper who owns the cargo, driver on this trip, admin.
     */
    public function getLocation(string $id)
    {
        $trip = Trip::with(['booking.cargoRequest', 'booking.vehicle'])->findOrFail($id);

        $user = auth()->user();
        $canView = $user->is_admin
            || $trip->booking->driver_id === $user->id
            || ($trip->booking->cargoRequest && $trip->booking->cargoRequest->user_id === $user->id);

        if (!$canView) {
            return response()->json(['message' => 'Forbidden'], 403);
        }

        $vehicle = $trip->booking->vehicle
            ?? Vehicle::where('user_id', $trip->booking->driver_id)->latest()->first();

        $lat          = $vehicle?->latitude;
        $lng          = $vehicle?->longitude;
        $city         = $vehicle?->current_city;
        $lastAt       = $vehicle?->last_location_at;
        $minutesSince = $lastAt ? (int) now()->diffInMinutes($lastAt) : null;

        // Last 50 breadcrumb points for the traveled-route overlay
        $routeData = array_slice($trip->route_data ?? [], -50);

        // Destination coords — resolve from cargo request
        $cargo           = $trip->booking->cargoRequest;
        $destinationName = $trip->destination ?? $cargo?->destination;

        [$destLat, $destLng] = $this->resolveCityCoords($destinationName);
        [$pickLat, $pickLng] = $cargo?->pickup_lat && $cargo?->pickup_lng
            ? [(float) $cargo->pickup_lat, (float) $cargo->pickup_lng]
            : $this->resolveCityCoords($cargo?->pickup_location ?? $cargo?->city);

        return response()->json([
            'current_lat'          => $lat,
            'current_lng'          => $lng,
            'current_city'         => $city,
            'last_updated_at'      => $lastAt?->toISOString(),
            'minutes_since_update' => $minutesSince,
            'route_data'           => $routeData,
            'destination'          => $destinationName,
            'destination_lat'      => $destLat,
            'destination_lng'      => $destLng,
            'pickup_lat'           => $pickLat,
            'pickup_lng'           => $pickLng,
        ]);
    }

    /** Look up city-centre coordinates from the VehicleController city table. */
    private function resolveCityCoords(?string $cityName): array
    {
        if (!$cityName) return [null, null];
        foreach (VehicleController::CITY_COORDS as $city => [$clat, $clng]) {
            if (stripos($cityName, $city) !== false || stripos($city, $cityName) !== false) {
                return [(float) $clat, (float) $clng];
            }
        }
        return [null, null];
    }
}
