<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Http\Requests\ChapaInitializeRequest;
use App\Http\Requests\PaymentProcessRequest;
use App\Models\Booking;
use App\Models\Payment;
use App\Services\ChapaService;
use App\Services\PaymentService;
use Illuminate\Http\Request;

class PaymentController extends Controller
{
    protected $paymentService;
    protected $chapa;

    public function __construct(PaymentService $paymentService, ChapaService $chapa)
    {
        $this->paymentService = $paymentService;
        $this->chapa = $chapa;
    }

    /**
     * GET /chapa/banks — the driver payout-details screen's bank picker.
     * Read-only passthrough; ChapaService already collapses every failure
     * to null, so a 502 here just means "try again," never a partial list.
     */
    public function listBanks()
    {
        $banks = $this->chapa->listBanks();
        if ($banks === null) {
            return response()->json(['message' => 'Unable to reach Chapa — please try again.'], 502);
        }
        return response()->json(['data' => $banks]);
    }

    public function store(PaymentProcessRequest $request)
    {
        $booking = Booking::findOrFail($request->booking_id);

        $user      = auth()->user();
        $isShipper = $booking->cargoRequest && $booking->cargoRequest->user_id === $user->id;
        $isDriver  = $booking->driver_id === $user->id;

        if (!$user->is_admin && !$isShipper && !$isDriver) {
            return response()->json([
                'message' => 'Only the shipper or driver can record a payment for this booking.',
            ], 403);
        }

        if ($booking->booking_status !== 'delivered') {
            return response()->json(['message' => 'Payment can only be made after the cargo has been delivered.'], 422);
        }

        // Only block if a payment already went through — a shipper who
        // abandoned a Chapa checkout (leaving a 'pending' row behind) can
        // still fall back to cash instead of getting stuck.
        if ($booking->payment && $booking->payment->payment_status === 'paid') {
            return response()->json(['message' => 'Payment already processed for this booking.'], 400);
        }

        $payment = $this->paymentService->processPayment($booking, array_merge(
            $request->validated(),
            ['paid_by' => $user->id]
        ));

        return response()->json([
            'message' => 'Payment processed successfully',
            'data' => $payment
        ], 201);
    }

    /**
     * Shipper-only: starts a real, gateway-verified Chapa payment and
     * returns a checkout URL. Unlike store(), only the shipper who owns the
     * booking may call this — no driver/admin self-report path here, since
     * this is the one payment endpoint that actually moves money.
     */
    public function initializeChapa(ChapaInitializeRequest $request)
    {
        $booking = Booking::findOrFail($request->booking_id);

        $user      = auth()->user();
        $isShipper = $booking->cargoRequest && $booking->cargoRequest->user_id === $user->id;

        if (!$isShipper) {
            return response()->json([
                'message' => 'Only the shipper can pay via Chapa for this booking.',
            ], 403);
        }

        // Upfront escrow: payment is now made as soon as a price is agreed
        // ('accepted' from bid acceptance/fleet dispatch, 'confirmed' from
        // bookDirect's direct fixed-price flow — both mean the same thing),
        // not after delivery like the old "pay after delivery" flow this
        // replaced.
        if (!in_array($booking->booking_status, ['accepted', 'confirmed'], true)) {
            return response()->json(['message' => 'Payment can be made once a price is agreed.'], 422);
        }

        try {
            $result = $this->paymentService->initializeChapaPayment($booking, $user);
        } catch (\RuntimeException $e) {
            return response()->json(['message' => $e->getMessage()], 400);
        }

        return response()->json(['data' => $result]);
    }

    public function show(string $booking_id)
    {
        $booking = Booking::with('payment')->findOrFail($booking_id);

        $user = auth()->user();
        $canView = $user->is_admin
            || $booking->driver_id === $user->id
            || ($booking->cargoRequest && $booking->cargoRequest->user_id === $user->id);

        if (!$canView) {
            return response()->json(['message' => 'Forbidden'], 403);
        }

        if (!$booking->payment) {
            return response()->json(['message' => 'No payment found for this booking'], 404);
        }

        return response()->json(['data' => $booking->payment]);
    }
}
