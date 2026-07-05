<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Http\Resources\TripStopResource;
use App\Models\Trip;
use App\Models\TripStop;
use App\Services\TripService;
use Illuminate\Http\Request;

class TripStopController extends Controller
{
    public function __construct(protected TripService $tripService) {}

    // ─────────────────────────────────────────────────────────────────────────
    // GET /trips/{trip}/stops
    // Driver or any shipper involved in the trip can view stops.
    // Each shipper only sees cargo details for stops that belong to their cargo.
    // ─────────────────────────────────────────────────────────────────────────
    public function index(Trip $trip)
    {
        $user = auth()->user();
        $this->authorizeView($trip, $user);

        $stops = $trip->tripStops()
            ->with(['cargoRequest.user'])
            ->inOrder()
            ->get();

        // Shipper privacy: blank out other shippers' cargo details
        if (!$user->is_admin && $trip->booking->driver_id !== $user->id) {
            $stops = $stops->map(function (TripStop $stop) use ($user) {
                if ($stop->cargoRequest && $stop->cargoRequest->user_id !== $user->id) {
                    $stop->setRelation('cargoRequest', null);
                    $stop->cargo_request_id = null;
                }
                return $stop;
            });
        }

        return TripStopResource::collection($stops);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /trips/{trip}/stops
    // Driver adds a new stop to an ongoing trip.
    // ─────────────────────────────────────────────────────────────────────────
    public function store(Request $request, Trip $trip)
    {
        $user = auth()->user();
        $this->authorizeDriver($trip, $user);

        if (!in_array($trip->trip_status, ['ongoing'])) {
            return response()->json(['message' => 'Stops can only be added to an ongoing trip.'], 422);
        }

        $data = $request->validate([
            'cargo_request_id' => 'nullable|exists:cargo_requests,id',
            'stop_order'       => 'required|integer|min:1',
            'location_name'    => 'required|string|max:100',
            'pickup_lat'       => 'nullable|numeric',
            'pickup_lng'       => 'nullable|numeric',
            'agreed_price'     => 'required|numeric|min:0',
            'notes'            => 'nullable|string|max:500',
        ]);

        $stop = TripStop::create(array_merge($data, ['trip_id' => $trip->id]));

        $trip->increment('total_stops');

        if ($trip->total_stops > 1) {
            $trip->update(['trip_type' => 'multi_stop']);
        }

        $trip->refresh()->load('tripStops');

        return response()->json([
            'success' => true,
            'message' => 'Stop added.',
            'data'    => [
                'stop' => new TripStopResource($stop->load('cargoRequest.user')),
                'trip' => $this->tripSummary($trip),
            ],
        ], 201);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PATCH /trips/{trip}/stops/{stop}/arrive
    // ─────────────────────────────────────────────────────────────────────────
    public function arrive(Trip $trip, TripStop $stop)
    {
        $this->authorizeDriver($trip, auth()->user());
        $this->assertBelongsToTrip($trip, $stop);

        if ($stop->status !== 'pending') {
            return response()->json(['message' => 'Stop is not in pending status.'], 422);
        }

        // Enforce sequence: previous stop must be completed
        if ($stop->stop_order > 1) {
            $prev = TripStop::where('trip_id', $trip->id)
                ->where('stop_order', $stop->stop_order - 1)
                ->first();

            if ($prev && $prev->status !== 'completed') {
                return response()->json([
                    'message' => "Complete stop {$prev->stop_order} ({$prev->location_name}) before arriving here.",
                ], 422);
            }
        }

        $stop->update(['status' => 'arrived', 'arrived_at' => now()]);

        $trip->refresh()->load('tripStops');

        return response()->json([
            'success' => true,
            'message' => "Arrived at {$stop->location_name}.",
            'data'    => [
                'stop' => new TripStopResource($stop->load('cargoRequest.user')),
                'trip' => $this->tripSummary($trip),
            ],
        ]);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PATCH /trips/{trip}/stops/{stop}/load
    // Cargo has been loaded onto the truck.
    // ─────────────────────────────────────────────────────────────────────────
    public function load(Trip $trip, TripStop $stop)
    {
        $this->authorizeDriver($trip, auth()->user());
        $this->assertBelongsToTrip($trip, $stop);

        if ($stop->status !== 'arrived') {
            return response()->json(['message' => 'Must arrive at stop before marking cargo loaded.'], 422);
        }

        $stop->update(['status' => 'loaded']);

        $trip->refresh()->load('tripStops');

        return response()->json([
            'success' => true,
            'message' => "Cargo loaded at {$stop->location_name}.",
            'data'    => [
                'stop' => new TripStopResource($stop->load('cargoRequest.user')),
                'trip' => $this->tripSummary($trip),
            ],
        ]);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PATCH /trips/{trip}/stops/{stop}/complete
    // Stop fully completed. If last stop, auto-completes the entire trip.
    // ─────────────────────────────────────────────────────────────────────────
    public function complete(Trip $trip, TripStop $stop)
    {
        $this->authorizeDriver($trip, auth()->user());
        $this->assertBelongsToTrip($trip, $stop);

        if (!in_array($stop->status, ['arrived', 'loaded'])) {
            return response()->json(['message' => 'Stop must be arrived or loaded before completing.'], 422);
        }

        $stop->update(['status' => 'completed', 'completed_at' => now()]);
        $trip->increment('completed_stops');
        $trip->refresh()->load('tripStops');

        // Auto-complete trip when all stops are done
        if ($trip->completed_stops >= $trip->total_stops) {
            $this->tripService->completeTrip($trip->load('booking.cargoRequest.user'));
            $trip->refresh()->load('tripStops');
        }

        return response()->json([
            'success' => true,
            'message' => $trip->trip_status === 'completed'
                ? 'All stops completed. Trip is now complete!'
                : "Stop {$stop->stop_order} completed.",
            'data'    => [
                'stop' => new TripStopResource($stop->load('cargoRequest.user')),
                'trip' => $this->tripSummary($trip),
            ],
        ]);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DELETE /trips/{trip}/stops/{stop}
    // Only allowed when trip is still pending (not started).
    // ─────────────────────────────────────────────────────────────────────────
    public function destroy(Trip $trip, TripStop $stop)
    {
        $this->authorizeDriver($trip, auth()->user());
        $this->assertBelongsToTrip($trip, $stop);

        if ($trip->trip_status !== 'pending') {
            return response()->json(['message' => 'Cannot remove stops from an ongoing trip.'], 422);
        }

        $deletedOrder = $stop->stop_order;
        $stop->delete();

        // Re-sequence remaining stops
        TripStop::where('trip_id', $trip->id)
            ->where('stop_order', '>', $deletedOrder)
            ->decrement('stop_order');

        $trip->decrement('total_stops');
        $newTotal = $trip->total_stops - 1; // already decremented; fetch fresh
        $trip->refresh();

        if ($trip->total_stops <= 1) {
            $trip->update(['trip_type' => 'single']);
        }

        $trip->load('tripStops');

        return response()->json([
            'success' => true,
            'message' => 'Stop removed.',
            'data'    => ['trip' => $this->tripSummary($trip)],
        ]);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private function authorizeDriver(Trip $trip, $user): void
    {
        if (!$user->is_admin && $trip->booking->driver_id !== $user->id) {
            abort(403, 'Forbidden');
        }
    }

    private function authorizeView(Trip $trip, $user): void
    {
        if ($user->is_admin || $trip->booking->driver_id === $user->id) {
            return;
        }
        // Shipper can view if their cargo is in any stop
        $isShipperInvolved = TripStop::where('trip_id', $trip->id)
            ->whereHas('cargoRequest', fn($q) => $q->where('user_id', $user->id))
            ->exists();

        // Also allow the primary booking shipper
        $isPrimaryShipper = $trip->booking->cargoRequest?->user_id === $user->id;

        if (!$isShipperInvolved && !$isPrimaryShipper) {
            abort(403, 'Forbidden');
        }
    }

    private function assertBelongsToTrip(Trip $trip, TripStop $stop): void
    {
        if ($stop->trip_id !== $trip->id) {
            abort(404, 'Stop not found on this trip.');
        }
    }

    private function tripSummary(Trip $trip): array
    {
        return [
            'id'               => $trip->id,
            'trip_type'        => $trip->trip_type,
            'trip_status'      => $trip->trip_status,
            'total_stops'      => $trip->total_stops,
            'completed_stops'  => $trip->completed_stops,
            'total_amount'     => $trip->total_amount,
            'total_amount_formatted' => 'ETB ' . number_format($trip->total_amount, 0, '.', ','),
            'start_location'   => $trip->start_location,
            'destination'      => $trip->destination,
        ];
    }
}
