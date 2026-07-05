<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Http\Requests\BookingCreateRequest;
use App\Http\Requests\BookingUpdateRequest;
use App\Http\Resources\BookingResource;
use App\Models\Booking;
use App\Services\BookingService;
use Illuminate\Http\Request;

class BookingController extends Controller
{
    protected $bookingService;

    public function __construct(BookingService $bookingService)
    {
        $this->bookingService = $bookingService;
    }

    /**
     * Display a listing of the resource.
     */
    public function index()
    {
        $user = auth()->user();

        $with = ['cargoRequest.user', 'driver', 'vehicle', 'trip.tripStops.cargoRequest', 'rating', 'payment'];

        if ($user->is_admin) {
            return BookingResource::collection(Booking::with($with)->get());
        }

        if ($user->role === 'driver') {
            return BookingResource::collection(
                Booking::with($with)->where('driver_id', $user->id)->get()
            );
        }

        if ($user->role === 'fleet_owner') {
            $driverIds = \App\Models\User::where('fleet_owner_id', $user->id)->pluck('id');
            return BookingResource::collection(
                Booking::with($with)->whereIn('driver_id', $driverIds)->get()
            );
        }

        return BookingResource::collection(
            Booking::with($with)->whereHas('cargoRequest', function ($query) use ($user) {
                $query->where('user_id', $user->id);
            })->get()
        );
    }

    /**
     * Store a newly created resource in storage.
     */
    public function store(BookingCreateRequest $request)
    {
        $booking = $this->bookingService->createBooking($request->validated());
        return (new BookingResource($booking))->response()->setStatusCode(201);
    }

    /**
     * Display the specified resource.
     */
    public function show(string $id)
    {
        $booking = Booking::findOrFail($id);
        $user = auth()->user();

        $isFleetDriverBooking = $user->role === 'fleet_owner'
            && \App\Models\User::where('id', $booking->driver_id)
                               ->where('fleet_owner_id', $user->id)
                               ->exists();

        if (!$user->is_admin
            && $booking->driver_id !== $user->id
            && (! $booking->cargoRequest || $booking->cargoRequest->user_id !== $user->id)
            && !$isFleetDriverBooking) {
            return response()->json(['message' => 'Forbidden'], 403);
        }

        return new BookingResource($booking);
    }

    /**
     * Update the specified resource in storage.
     */
    public function update(BookingUpdateRequest $request, string $id)
    {
        $booking = Booking::findOrFail($id);
        $user = auth()->user();

        $isFleetDriverBooking = $user->role === 'fleet_owner'
            && \App\Models\User::where('id', $booking->driver_id)
                               ->where('fleet_owner_id', $user->id)
                               ->exists();

        $canUpdate = $user->is_admin
            || $booking->driver_id === $user->id
            || ($booking->cargoRequest && $booking->cargoRequest->user_id === $user->id)
            || $isFleetDriverBooking;

        if (!$canUpdate) {
            return response()->json(['message' => 'Forbidden'], 403);
        }

        $booking = $this->bookingService->updateBooking($booking, $request->validated());
        return new BookingResource($booking);
    }

    /**
     * PATCH /bookings/{booking}/cancel
     * Either party (driver or shipper) cancels before trip starts.
     */
    public function cancel(Booking $booking)
    {
        $user = auth()->user();

        $isFleetDriverBooking = $user->role === 'fleet_owner'
            && \App\Models\User::where('id', $booking->driver_id)
                               ->where('fleet_owner_id', $user->id)
                               ->exists();

        $canCancel = $user->is_admin
            || $booking->driver_id === $user->id
            || ($booking->cargoRequest && $booking->cargoRequest->user_id === $user->id)
            || $isFleetDriverBooking;

        if (!$canCancel) {
            return response()->json(['message' => 'Forbidden'], 403);
        }

        if ($booking->booking_status === 'completed') {
            return response()->json(['message' => 'A completed booking cannot be cancelled.'], 422);
        }

        if ($booking->trip && $booking->trip->status === 'ongoing') {
            return response()->json(['message' => 'Cannot cancel — trip is already in progress.'], 422);
        }

        $booking->update(['booking_status' => 'cancelled']);

        // Restore cargo to pending so other drivers can bid again
        if ($booking->cargoRequest) {
            $booking->cargoRequest->update(['status' => 'pending']);
        }

        // Restore vehicle to available
        if ($booking->vehicle) {
            $booking->vehicle->update(['availability_status' => 'available']);
        }

        return response()->json([
            'success' => true,
            'message' => 'Booking cancelled. Cargo is back on the market.',
        ]);
    }

    /**
     * Remove the specified resource from storage.
     */
    public function destroy(string $id)
    {
        $booking = Booking::findOrFail($id);
        $user = auth()->user();

        $canDelete = $user->is_admin
            || $booking->driver_id === $user->id
            || ($booking->cargoRequest && $booking->cargoRequest->user_id === $user->id)
            || (\App\Models\User::where('id', $booking->driver_id)->where('fleet_owner_id', $user->id)->exists());

        if (!$canDelete) {
            return response()->json(['message' => 'Forbidden'], 403);
        }

        $this->bookingService->deleteBooking($booking);
        return response()->json(['message' => 'Booking deleted successfully']);
    }
}
