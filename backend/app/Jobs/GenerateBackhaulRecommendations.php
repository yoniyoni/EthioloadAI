<?php

namespace App\Jobs;

use App\Models\Trip;
use App\Services\BackhaulService;
use Illuminate\Bus\Queueable;
use Illuminate\Contracts\Queue\ShouldQueue;
use Illuminate\Foundation\Bus\Dispatchable;
use Illuminate\Queue\InteractsWithQueue;
use Illuminate\Queue\SerializesModels;
use Illuminate\Support\Facades\Log;

class GenerateBackhaulRecommendations implements ShouldQueue
{
    use Dispatchable, InteractsWithQueue, Queueable, SerializesModels;

    public int $tries   = 3;
    public int $timeout = 30;

    public function __construct(private readonly Trip $trip) {}

    public function handle(BackhaulService $service): void
    {
        try {
            $recs = $service->recommendForTrip($this->trip);
            Log::info("BackhaulRecommendations: generated {$recs->count()} suggestions for trip #{$this->trip->id}");
        } catch (\Throwable $e) {
            Log::error("BackhaulRecommendations: failed for trip #{$this->trip->id} — {$e->getMessage()}");
            throw $e;
        }
    }
}
