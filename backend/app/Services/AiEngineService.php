<?php

namespace App\Services;

use Illuminate\Support\Facades\Http;

class AiEngineService
{
    protected $baseUrl;

    public function __construct()
    {
        $this->baseUrl = config('services.ai_engine.url', env('AI_ENGINE_URL', 'http://localhost:8000'));
    }

    public function recommendTruck(array $payload)
    {
        return $this->post('/ai/recommend-truck', $payload);
    }

    public function backhaulOpportunities(array $payload)
    {
        return $this->post('/ai/backhaul-opportunities', $payload);
    }

    public function predictPrice(array $payload)
    {
        return $this->post('/ai/predict-price', $payload);
    }

    public function predictEmptyReturn(array $payload)
    {
        return $this->post('/ai/predict-empty-return', $payload);
    }

    public function optimizeRoute(array $payload)
    {
        return $this->post('/ai/optimize-route', $payload);
    }

    protected function post($endpoint, $payload)
    {
        try {
            $response = Http::timeout(5)->post($this->baseUrl . $endpoint, $payload);
            if ($response->successful()) {
                return $response->json();
            }
            return [
                'error' => true,
                'message' => $response->body(),
                'status' => $response->status(),
            ];
        } catch (\Exception $e) {
            return ['error' => true, 'message' => $e->getMessage()];
        }
    }
}
