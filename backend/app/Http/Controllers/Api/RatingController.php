<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\Booking;
use App\Models\Rating;
use Illuminate\Http\Request;

class RatingController extends Controller
{
    /**
     * Submit a rating for a completed booking.
     * The authenticated user can rate the other party.
     */
    public function store(Request $request)
    {
        $request->validate([
            'booking_id' => 'required|exists:bookings,id',
            'rating'     => 'required|integer|min:1|max:5',
            'feedback'   => 'nullable|string|max:500',
        ]);

        $booking = Booking::findOrFail($request->booking_id);
        $user    = auth()->user();

        $isShipper = $booking->cargoRequest && $booking->cargoRequest->user_id === $user->id;
        $isDriver  = $booking->driver_id === $user->id;

        if (!$isShipper && !$user->is_admin) {
            return response()->json(['message' => 'Only the shipper can rate a completed delivery.'], 403);
        }

        if (!in_array($booking->booking_status, ['completed', 'delivered', 'confirmed'])) {
            return response()->json(['message' => 'Can only rate a completed booking.'], 422);
        }

        if (Rating::where('booking_id', $booking->id)->where('rater_id', $user->id)->exists()) {
            return response()->json(['message' => 'You have already rated this booking.'], 400);
        }

        $shipperId = $booking->cargoRequest?->user_id ?? $user->id;
        $driverId  = $booking->driver_id;

        $rating = Rating::create([
            'booking_id' => $booking->id,
            'shipper_id' => $shipperId,
            'driver_id'  => $driverId,
            'rater_id'   => $user->id,
            'rating'     => $request->rating,
            'feedback'   => $request->feedback,
        ]);

        return response()->json([
            'message' => 'Rating submitted successfully',
            'data'    => $rating,
        ], 201);
    }

    /**
     * Get ratings for a booking.
     */
    public function show(string $booking_id)
    {
        $booking = Booking::findOrFail($booking_id);
        $ratings = Rating::where('booking_id', $booking_id)->get();
        return response()->json(['data' => $ratings]);
    }

    /**
     * GET /driver/my-ratings
     * Driver sees all ratings they have received from shippers.
     */
    public function myRatings()
    {
        $driverId = auth()->id();

        $ratings = Rating::where('driver_id', $driverId)
            ->with(['booking.cargoRequest'])
            ->latest()
            ->get()
            ->map(fn ($r) => [
                'id'           => $r->id,
                'booking_id'   => $r->booking_id,
                'rating'       => $r->rating,
                'feedback'     => $r->feedback,
                'route'        => $r->booking?->cargoRequest
                    ? $r->booking->cargoRequest->pickup_location . ' → ' . $r->booking->cargoRequest->destination
                    : null,
                'created_at'   => $r->created_at,
            ]);

        $avg = $ratings->avg('rating');

        return response()->json([
            'average_rating' => $avg ? round($avg, 1) : null,
            'total_ratings'  => $ratings->count(),
            'data'           => $ratings,
        ]);
    }
}
