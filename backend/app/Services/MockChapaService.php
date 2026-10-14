<?php

namespace App\Services;

use Illuminate\Support\Facades\Cache;

/**
 * Stands in for ChapaService while there's no real Chapa merchant account —
 * same method signatures, so PaymentService/PaymentController need zero
 * changes to use it. Bound in place of ChapaService by
 * AppServiceProvider::register() when config('services.payment_gateway.driver')
 * is 'mock' (the default). Flip that one config value to 'chapa' once a real
 * account exists — nothing else changes.
 *
 * No real network calls anywhere in this class. "Verification" is a tiny
 * cache flag set by MockPaymentController::confirm() when the user taps
 * Confirm on the mock checkout page — everything downstream of that
 * (finalizeChapaPayment's re-verify, idempotency, amount cross-check) is
 * the exact same real logic already built for Chapa.
 */
class MockChapaService extends ChapaService
{
    public function __construct()
    {
        // No secret key needed — deliberately not calling parent::__construct().
    }

    public function initialize(array $payload): ?array
    {
        return [
            'checkout_url' => route('mock.checkout', ['tx_ref' => $payload['tx_ref']]),
        ];
    }

    public function verify(string $txRef): ?array
    {
        $confirmed = Cache::get('mock_confirmed_' . $txRef);
        if ($confirmed === null) {
            return ['status' => 'pending'];
        }
        return ['status' => 'success', 'amount' => $confirmed['amount']];
    }

    public function transfer(array $payload): ?array
    {
        // Simulates an instant, always-successful payout — there's no real
        // money to move in mock mode.
        return ['status' => 'success'];
    }

    public function listBanks(): ?array
    {
        return [
            ['id' => 'MOCK-CBE', 'name' => 'Commercial Bank of Ethiopia (mock)'],
            ['id' => 'MOCK-AWASH', 'name' => 'Awash Bank (mock)'],
            ['id' => 'MOCK-DASHEN', 'name' => 'Dashen Bank (mock)'],
        ];
    }
}
