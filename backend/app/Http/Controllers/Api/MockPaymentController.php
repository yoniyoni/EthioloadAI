<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\Payment;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Cache;

/**
 * Fully public, no auth — mirrors ChapaWebhookController's public routes,
 * since a real Chapa checkout page is also public/unauthenticated from the
 * app's perspective. Only active when MockChapaService is bound in place of
 * ChapaService (see AppServiceProvider::register()).
 */
class MockPaymentController extends Controller
{
    // GET /payments/mock/checkout?tx_ref=...
    public function checkout(Request $request)
    {
        $txRef = $request->query('tx_ref');
        $payment = $txRef ? Payment::where('transaction_ref', $txRef)->first() : null;

        return view('payments.mock-checkout', [
            'txRef' => $txRef,
            'amount' => $payment?->amount,
            'found' => $payment !== null,
        ]);
    }

    // POST /payments/mock/confirm
    public function confirm(Request $request)
    {
        $txRef = $request->input('tx_ref');
        $payment = $txRef ? Payment::where('transaction_ref', $txRef)->first() : null;

        if ($payment) {
            // The one piece of state MockChapaService::verify() checks —
            // everything downstream (finalizeChapaPayment's re-verify,
            // idempotent locking, amount cross-check) is the real logic
            // already built for Chapa, unchanged.
            Cache::put('mock_confirmed_' . $txRef, ['amount' => (float) $payment->amount], now()->addMinutes(30));
        }

        return redirect()->route('chapa.return', ['tx_ref' => $txRef]);
    }
}
