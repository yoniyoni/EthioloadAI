<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Services\PaymentService;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Log;

/**
 * Fully public — no auth:sanctum, no CSRF (the api middleware group has
 * none). Both endpoints here treat whatever they receive as nothing more
 * than a signal to go re-check with Chapa directly; neither ever trusts a
 * request body as proof of payment. See PaymentService::finalizeChapaPayment().
 */
class ChapaWebhookController extends Controller
{
    public function __construct(private PaymentService $paymentService)
    {
    }

    // POST /webhooks/chapa — Chapa's server-to-server callback.
    public function webhook(Request $request)
    {
        $txRef = $request->input('tx_ref');
        if (!$txRef) {
            Log::warning('Chapa webhook missing tx_ref', ['payload' => $request->all()]);
            return response()->json(['message' => 'ignored'], 200);
        }

        $outcome = $this->paymentService->finalizeChapaPayment($txRef);

        // 503 only when our own call to Chapa's verify endpoint failed —
        // that's the one case worth Chapa retrying delivery for. Every
        // other outcome (paid, failed, mismatch, unknown ref) is resolved.
        return response()->json(['outcome' => $outcome], $outcome === 'verify_failed' ? 503 : 200);
    }

    // GET /payments/chapa/return — where Chapa's browser redirect lands.
    public function returnPage(Request $request)
    {
        $txRef = $request->query('tx_ref');
        $outcome = 'unknown_tx_ref';
        if ($txRef) {
            // Second, independent trigger of the same idempotent path —
            // covers the case where the server-to-server webhook is
            // delayed or briefly unreachable (common when testing behind a
            // tunnel). The page itself proves nothing beyond what this
            // verified outcome says; the mobile app still confirms via its
            // own poll of GET /payments/{booking_id} rather than trusting
            // this page was ever shown.
            $outcome = $this->paymentService->finalizeChapaPayment($txRef);
        }

        return view('payments.chapa-return', ['outcome' => $outcome]);
    }
}
