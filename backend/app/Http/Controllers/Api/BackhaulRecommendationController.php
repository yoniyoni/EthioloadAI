<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\BackhaulRecommendation;
use App\Models\Trip;

class BackhaulRecommendationController extends Controller
{
    /**
     * GET /trips/{trip}/backhaul-recommendations
     * Returns the top recommendations for an ongoing trip, scored DESC.
     * Only the trip's driver may view their own recommendations.
     */
    public function index(Trip $trip)
    {
        $user = auth()->user();

        if (!$user->is_admin && $trip->booking?->driver_id !== $user->id) {
            return response()->json(['message' => 'Forbidden'], 403);
        }

        $recs = BackhaulRecommendation::where('trip_id', $trip->id)
            ->where('driver_id', $user->is_admin ? $trip->booking?->driver_id : $user->id)
            ->where('status', '!=', 'dismissed')
            ->with('cargoRequest')
            ->orderByDesc('score')
            ->get()
            ->map(fn (BackhaulRecommendation $r) => $this->format($r));

        return response()->json(['data' => $recs]);
    }

    /**
     * PATCH /recommendations/{recommendation}/dismiss
     * Driver dismisses a card — it won't reappear after refresh.
     */
    public function dismiss(BackhaulRecommendation $recommendation)
    {
        $user = auth()->user();

        if ($recommendation->driver_id !== $user->id) {
            return response()->json(['message' => 'Forbidden'], 403);
        }

        $recommendation->update(['status' => 'dismissed']);

        return response()->json(['message' => 'Dismissed']);
    }

    private function format(BackhaulRecommendation $r): array
    {
        $cargo = $r->cargoRequest;
        $meta  = $r->metadata ?? [];

        return [
            'id'     => $r->id,
            'score'  => $r->score,
            'status' => $r->status,
            'cargo_request' => $cargo ? [
                'id'             => $cargo->id,
                'pickup_location'=> $cargo->pickup_location,
                'destination'    => $cargo->destination,
                'material_type'  => $cargo->material_type,
                'weight'         => (float) $cargo->weight,
                'urgency_level'  => $cargo->urgency_level,
                'budget'         => $cargo->budget ? (float) $cargo->budget : null,
                'status'         => $cargo->status,
            ] : null,
            'metadata' => [
                'distance_km'           => $meta['distance_km'] ?? null,
                'urgency'               => $meta['urgency'] ?? null,
                'estimated_price_range' => $meta['estimated_price_range'] ?? null,
                'pickup_location_name'  => $meta['pickup_location_name'] ?? null,
            ],
        ];
    }
}
