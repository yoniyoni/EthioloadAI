<?php

namespace App\Services;

use Illuminate\Support\Facades\Http;
use Illuminate\Support\Facades\Log;

/**
 * Thin client for the Chapa payment gateway (https://developer.chapa.co).
 * Knows nothing about Payment/Booking — PaymentService owns what the
 * response means. Every failure mode (unreachable, non-2xx, unexpected
 * shape) collapses to null so the caller has one thing to check.
 */
class ChapaService
{
    private const BASE = 'https://api.chapa.co/v1';
    private const TIMEOUT = 15;

    private ?string $secretKey;

    public function __construct()
    {
        $this->secretKey = config('services.chapa.secret_key');
    }

    public function initialize(array $payload): ?array
    {
        $response = $this->post('/transaction/initialize', $payload);
        return $response['data'] ?? null;
    }

    public function verify(string $txRef): ?array
    {
        $response = $this->get('/transaction/verify/' . $txRef);
        return $response['data'] ?? null;
    }

    /**
     * List of banks Chapa can transfer to, each with the `code` needed for
     * transfer(). Used by the driver payout-details screen to offer a
     * picker instead of a free-text bank code.
     */
    public function listBanks(): ?array
    {
        $response = $this->get('/banks');
        return $response['data'] ?? null;
    }

    /**
     * Pays out to a driver's bank account. $payload: account_name,
     * account_number, bank_code, amount, currency, reference. Returns the
     * full decoded response (top-level 'status'/'message'), not narrowed to
     * 'data' like initialize()/verify() — Chapa's transfer response isn't
     * confirmed to carry a meaningful 'data' payload (transfers may be
     * queued/async); the top-level 'status' is the safer signal to check.
     * VERIFY THIS against real Chapa docs/dashboard once available —
     * flagged in the plan as an assumption, not a confirmed contract.
     * Same collapse-to-null-on-failure contract otherwise.
     */
    public function transfer(array $payload): ?array
    {
        return $this->post('/transfers', $payload);
    }

    private function get(string $path): ?array
    {
        if (!$this->secretKey) {
            Log::error('Chapa secret key not configured — set CHAPA_SECRET_KEY');
            return null;
        }

        try {
            $response = Http::timeout(self::TIMEOUT)
                ->withToken($this->secretKey)
                ->get(self::BASE . $path);

            if (!$response->successful()) {
                Log::warning('Chapa request failed', [
                    'path' => $path,
                    'status' => $response->status(),
                    'body' => $response->body(),
                ]);
                return null;
            }

            return $response->json();
        } catch (\Throwable $e) {
            Log::error('Chapa request error', ['path' => $path, 'error' => $e->getMessage()]);
            return null;
        }
    }

    private function post(string $path, array $payload): ?array
    {
        if (!$this->secretKey) {
            Log::error('Chapa secret key not configured — set CHAPA_SECRET_KEY');
            return null;
        }

        try {
            $response = Http::timeout(self::TIMEOUT)
                ->withToken($this->secretKey)
                ->post(self::BASE . $path, $payload);

            if (!$response->successful()) {
                Log::warning('Chapa request failed', [
                    'path' => $path,
                    'status' => $response->status(),
                    'body' => $response->body(),
                ]);
                return null;
            }

            return $response->json();
        } catch (\Throwable $e) {
            Log::error('Chapa request error', ['path' => $path, 'error' => $e->getMessage()]);
            return null;
        }
    }
}
