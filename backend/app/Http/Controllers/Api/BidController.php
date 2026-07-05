<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Http\Requests\BidStoreRequest;
use App\Http\Resources\BidResource;
use App\Http\Resources\BookingResource;
use App\Models\Bid;
use App\Models\CargoRequest;
use App\Notifications\BookingCreatedNotification;
use App\Notifications\BidRejectedNotification;
use App\Notifications\BidCounteredNotification;
use App\Notifications\BidPlacedNotification;
use App\Services\BidService;
use Illuminate\Http\Request;

class BidController extends Controller
{
    public function __construct(protected BidService $bidService) {}

    /**
     * GET /cargo-requests/{cargo}/bids
     * Shipper (or admin) views all bids on their cargo request.
     */
    public function index(CargoRequest $cargo)
    {
        $user = auth()->user();

        if (!$user->is_admin && $cargo->user_id !== $user->id) {
            return response()->json(['message' => 'Forbidden'], 403);
        }

        $bids = $cargo->bids()
            ->with(['driver', 'vehicle', 'cargoRequest.user'])
            ->get();

        return BidResource::collection($this->bidService->rankBids($bids));
    }

    /**
     * POST /cargo-requests/{cargo}/bids
     * Driver places a bid on a cargo request.
     */
    public function store(BidStoreRequest $request, CargoRequest $cargo)
    {
        $user = auth()->user();
        if (!$user->verification_status) {
            return response()->json([
                'message' => 'Your account is not yet verified. Upload your documents and wait for Admin approval before placing bids.',
            ], 403);
        }

        try {
            $bid = $this->bidService->placeBid(
                $request->validated(),
                auth()->user(),
                $cargo,
            );
        } catch (\Exception $e) {
            return response()->json(['message' => $e->getMessage()], 422);
        }

        // Notify the shipper that a new bid has been placed on their cargo
        $cargo->load('user');
        $cargo->user?->notify(new BidPlacedNotification($bid->load(['driver', 'cargoRequest'])));

        return (new BidResource($bid))->response()->setStatusCode(201);
    }

    /**
     * PATCH /bids/{bid}/accept
     * Shipper accepts a bid — creates a confirmed Booking automatically.
     */
    public function accept(Bid $bid)
    {
        $user  = auth()->user();
        $cargo = $bid->cargoRequest;

        if (!$user->is_admin && $cargo->user_id !== $user->id) {
            return response()->json(['message' => 'Forbidden'], 403);
        }

        if ($bid->status !== 'pending') {
            return response()->json(['message' => 'This bid is no longer pending.'], 422);
        }

        // Collect competing bids before the transaction wipes their status
        $otherActiveBids = Bid::where('cargo_request_id', $bid->cargo_request_id)
            ->where('id', '!=', $bid->id)
            ->whereIn('status', ['pending', 'countered'])
            ->with(['driver', 'cargoRequest'])
            ->get();

        $booking = $this->bidService->acceptBid($bid);

        // Notify the driver (or fleet owner) that their bid was accepted
        $bid->driver?->notify(new BookingCreatedNotification($booking));

        // Notify every driver whose bid was auto-rejected
        foreach ($otherActiveBids as $rejected) {
            $rejected->driver?->notify(new BidRejectedNotification($rejected, 'cargo_taken'));
        }

        return response()->json([
            'success' => true,
            'message' => 'Bid accepted. Booking confirmed.',
            'data'    => new BookingResource($booking->load(['cargoRequest.user', 'driver', 'vehicle', 'trip', 'rating'])),
        ]);
    }

    /**
     * PATCH /bids/{bid}/reject
     * Shipper rejects a bid (pending or countered).
     */
    public function reject(Bid $bid)
    {
        $user  = auth()->user();
        $cargo = $bid->cargoRequest;

        if (!$user->is_admin && $cargo->user_id !== $user->id) {
            return response()->json(['message' => 'Forbidden'], 403);
        }

        if (!in_array($bid->status, ['pending', 'countered'])) {
            return response()->json(['message' => 'This bid can no longer be rejected.'], 422);
        }

        $bid->update(['status' => 'rejected']);

        // Notify the driver their bid was rejected
        $bid->driver?->notify(new BidRejectedNotification($bid));

        return response()->json(['success' => true, 'message' => 'Bid rejected.']);
    }

    /**
     * PATCH /bids/{bid}/counter
     * Either the shipper or the driver sends a counter-offer.
     */
    public function counter(Request $request, Bid $bid)
    {
        $request->validate([
            'counter_amount' => 'required|numeric|min:1',
            'counter_note'   => 'nullable|string|max:500',
        ]);

        try {
            $bid = $this->bidService->counterBid(
                $bid,
                auth()->user(),
                (float) $request->counter_amount,
                $request->counter_note,
            );
        } catch (\Exception $e) {
            return response()->json(['message' => $e->getMessage()], 422);
        }

        // Notify the other party about the counter-offer
        if ($bid->counter_by === 'shipper') {
            $bid->driver?->notify(new BidCounteredNotification($bid));
        } else {
            $bid->cargoRequest?->user?->notify(new BidCounteredNotification($bid));
        }

        return response()->json([
            'success' => true,
            'message' => 'Counter-offer sent.',
            'data'    => new BidResource($bid),
        ]);
    }

    /**
     * PATCH /bids/{bid}/accept-counter
     * Accept the standing counter-offer. Creates a booking at counter_amount.
     */
    public function acceptCounter(Bid $bid)
    {
        $actor = auth()->user();

        // Collect competing bids before the transaction wipes their status
        $otherActiveBids = Bid::where('cargo_request_id', $bid->cargo_request_id)
            ->where('id', '!=', $bid->id)
            ->whereIn('status', ['pending', 'countered'])
            ->with(['driver', 'cargoRequest'])
            ->get();

        try {
            $booking = $this->bidService->acceptCounter($bid, $actor);
        } catch (\Exception $e) {
            return response()->json(['message' => $e->getMessage()], 422);
        }

        // Notify the other party that the counter-offer was accepted and a booking is confirmed
        if ($actor->id === $bid->driver_id) {
            // Driver accepted shipper's counter — notify the shipper
            $bid->cargoRequest?->user?->notify(new BookingCreatedNotification($booking, 'shipper'));
        } else {
            // Shipper accepted driver's counter — notify the driver
            $bid->driver?->notify(new BookingCreatedNotification($booking));
        }

        // Notify every driver whose bid was auto-rejected
        foreach ($otherActiveBids as $rejected) {
            $rejected->driver?->notify(new BidRejectedNotification($rejected, 'cargo_taken'));
        }

        return response()->json([
            'success' => true,
            'message' => 'Counter-offer accepted. Booking confirmed.',
            'data'    => new BookingResource(
                $booking->load(['cargoRequest', 'driver', 'vehicle', 'trip', 'rating'])
            ),
        ]);
    }

    /**
     * PATCH /bids/{bid}/withdraw
     * Driver withdraws their own pending bid.
     */
    public function withdraw(Bid $bid)
    {
        $user = auth()->user();

        if ($bid->driver_id !== $user->id) {
            return response()->json(['message' => 'Forbidden'], 403);
        }

        if (!in_array($bid->status, ['pending', 'countered'])) {
            return response()->json(['message' => 'This bid can no longer be withdrawn.'], 422);
        }

        $bid->update(['status' => 'rejected']);

        return response()->json(['success' => true, 'message' => 'Bid withdrawn.']);
    }

    /**
     * PATCH /bids/{bid}
     * Driver updates the amount (and optional note) on their own pending bid.
     */
    public function update(Request $request, Bid $bid)
    {
        $user = auth()->user();

        if ($bid->driver_id !== $user->id) {
            return response()->json(['message' => 'Forbidden'], 403);
        }

        if (!in_array($bid->status, ['pending', 'countered'])) {
            return response()->json(['message' => 'This bid can no longer be edited.'], 422);
        }

        $validated = $request->validate([
            'amount' => 'required|numeric|min:1|max:9999999.99',
            'note'   => 'nullable|string|max:500',
        ]);

        $bid->update([
            'amount' => $validated['amount'],
            'note'   => $validated['note'] ?? $bid->note,
        ]);

        return response()->json([
            'success' => true,
            'message' => 'Bid updated.',
            'data'    => new \App\Http\Resources\BidResource($bid->fresh()),
        ]);
    }

    /**
     * GET /driver/bids
     * Driver views all their own bids (with counter-offer state).
     */
    public function myBids()
    {
        $bids = Bid::where('driver_id', auth()->id())
            ->with(['cargoRequest.user', 'vehicle'])
            ->orderByRaw("CASE status
                WHEN 'countered'  THEN 1
                WHEN 'pending'    THEN 2
                WHEN 'accepted'   THEN 3
                WHEN 'rejected'   THEN 4
                WHEN 'expired'    THEN 5
                ELSE 6 END")
            ->orderByDesc('updated_at')
            ->get();

        return BidResource::collection($bids);
    }
}
