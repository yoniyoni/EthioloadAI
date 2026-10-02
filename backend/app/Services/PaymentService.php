<?php

namespace App\Services;

use App\Models\Booking;
use App\Models\Payment;
use App\Models\User;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Log;
use Illuminate\Support\Str;

class PaymentService
{
    public function __construct(private ChapaService $chapa)
    {
    }

    public function processPayment(Booking $booking, array $paymentDetails)
    {
        $amount     = (float) $booking->estimated_price;
        $commission = round($amount * config('services.commission_rate'), 2);
        $driverNet  = round($amount - $commission, 2);

        // Reuse an existing non-paid row (e.g. a 'pending' one left behind by
        // an abandoned Chapa checkout) instead of always creating a new one —
        // Booking::payment() is a hasOne, so a stray second row here would
        // make it ambiguous which payment the rest of the app sees.
        $payment = Payment::where('booking_id', $booking->id)->first() ?? new Payment();
        $payment->fill([
            'booking_id'        => $booking->id,
            'amount'            => $amount,
            'commission_amount' => $commission,
            'driver_net_amount' => $driverNet,
            'paid_by'           => $paymentDetails['paid_by'] ?? null,
            'payment_method'    => $paymentDetails['payment_method'] ?? 'cash',
            'payment_status'    => 'paid',
        ]);
        $payment->save();

        $booking->update(['booking_status' => 'completed']);

        return $payment;
    }

    /**
     * Starts a real, gateway-verified payment for the shipper's "Pay Now"
     * flow. Creates (or reuses, if the shipper abandoned a previous attempt)
     * a 'pending' Payment row and returns a Chapa-hosted checkout URL — the
     * row is never marked paid here, only finalizeChapaPayment() does that,
     * and only after independently re-verifying with Chapa.
     */
    public function initializeChapaPayment(Booking $booking, User $shipper): array
    {
        // Queried fresh rather than via $booking->payment — a caller holding
        // an already-loaded Booking (with the relation cached from before
        // this row existed, or before it was marked paid) must never see a
        // stale answer here; this is the guard that stops double-paying.
        $existing = Payment::where('booking_id', $booking->id)->first();
        if ($existing && $existing->payment_status === 'paid') {
            throw new \RuntimeException('Payment already completed for this booking.');
        }

        $amount = (float) $booking->estimated_price;
        $txRef  = 'ELA-' . $booking->id . '-' . Str::random(10);

        $payment = $existing ?? new Payment(['booking_id' => $booking->id]);
        $payment->fill([
            'amount'          => $amount,
            'paid_by'         => $shipper->id,
            'payment_method'  => 'chapa',
            'payment_status'  => 'pending',
            'transaction_ref' => $txRef,
        ]);
        $payment->save();

        $email = $shipper->email ?: sprintf('user-%d@users.ethioloadai.et', $shipper->id);
        [$firstName, $lastName] = $this->splitName($shipper->full_name);

        $result = $this->chapa->initialize([
            'amount'       => $amount,
            'currency'     => 'ETB',
            'email'        => $email,
            'first_name'   => $firstName,
            'last_name'    => $lastName,
            'tx_ref'       => $txRef,
            'callback_url' => route('chapa.webhook'),
            'return_url'   => route('chapa.return', ['tx_ref' => $txRef]),
            'customization' => [
                'title'       => 'EthioLoadAI',
                'description' => "Booking #{$booking->id}",
            ],
        ]);

        if ($result === null || empty($result['checkout_url'])) {
            throw new \RuntimeException('Unable to reach Chapa — please try again.');
        }

        return ['checkout_url' => $result['checkout_url'], 'tx_ref' => $txRef];
    }

    /**
     * The only place a Chapa payment is ever marked paid. Called from both
     * the webhook and the return-page hit (and safe to call from either,
     * repeatedly, or concurrently) — never trusts the caller's payload,
     * always re-verifies with Chapa directly first. Marks the booking
     * 'paid', not 'completed' — money verified means the trip may start,
     * not that the booking is done; that only happens once the shipper
     * confirms arrival (see BookingService::confirmArrival / releaseToDriver).
     */
    public function finalizeChapaPayment(string $txRef): string
    {
        return DB::transaction(function () use ($txRef) {
            $payment = Payment::where('transaction_ref', $txRef)->lockForUpdate()->first();
            if (!$payment) {
                return 'unknown_tx_ref';
            }
            if ($payment->payment_status === 'paid') {
                return 'already_paid';
            }

            $verified = $this->chapa->verify($txRef);
            if ($verified === null) {
                return 'verify_failed';
            }

            if (($verified['status'] ?? null) !== 'success') {
                $payment->update(['payment_status' => 'failed']);
                return 'marked_failed';
            }

            $verifiedAmount = (float) ($verified['amount'] ?? 0);
            if (abs($verifiedAmount - (float) $payment->amount) > 0.01) {
                Log::error('Chapa amount mismatch — not marking paid', [
                    'tx_ref'         => $txRef,
                    'expected'       => $payment->amount,
                    'chapa_reported' => $verified['amount'] ?? null,
                ]);
                return 'amount_mismatch';
            }

            $commission = round((float) $payment->amount * config('services.commission_rate'), 2);
            $payment->update([
                'payment_status'    => 'paid',
                'commission_amount' => $commission,
                'driver_net_amount' => round((float) $payment->amount - $commission, 2),
            ]);
            $payment->booking->update(['booking_status' => 'paid']);

            return 'paid';
        });
    }

    /**
     * Pays the driver their net share once the shipper has confirmed
     * arrival. Called from BookingService::confirmArrival() — deliberately
     * never throws and never blocks that caller: a driver missing payout
     * details, or a failed Chapa transfer, is recorded plainly on the
     * Payment row for operational follow-up, not treated as a reason the
     * shipper's confirmation itself should fail. Idempotent — safe to call
     * more than once (e.g. a retried request) without double-paying.
     */
    public function releaseToDriver(Payment $payment): string
    {
        return DB::transaction(function () use ($payment) {
            $locked = Payment::whereKey($payment->id)->lockForUpdate()->first();
            if (!$locked) {
                return 'unknown_payment';
            }
            if ($locked->payout_status === 'paid_out') {
                return 'already_paid_out';
            }
            if ($locked->payment_status !== 'paid') {
                // Nothing was actually collected — cash bookings, or a
                // Chapa payment that never verified. Nothing to release.
                return 'not_applicable';
            }

            $driver = $locked->booking?->driver;
            if (!$driver || !$driver->payout_bank_code || !$driver->payout_account_number) {
                $locked->update(['payout_status' => 'pending']);
                return 'awaiting_driver_details';
            }

            $payoutRef = 'ELA-PAYOUT-' . $locked->id . '-' . Str::random(10);
            $result = $this->chapa->transfer([
                'account_name'   => $driver->payout_account_name ?: $driver->full_name,
                'account_number' => $driver->payout_account_number,
                'bank_code'      => $driver->payout_bank_code,
                'amount'         => (float) $locked->driver_net_amount,
                'currency'       => 'ETB',
                'reference'      => $payoutRef,
            ]);

            if ($result === null || ($result['status'] ?? null) !== 'success') {
                Log::error('Chapa payout failed', [
                    'payment_id' => $locked->id,
                    'driver_id'  => $driver->id,
                    'response'   => $result,
                ]);
                $locked->update(['payout_status' => 'failed', 'payout_reference' => $payoutRef]);
                return 'failed';
            }

            $locked->update(['payout_status' => 'paid_out', 'payout_reference' => $payoutRef]);
            return 'paid_out';
        });
    }

    private function splitName(?string $fullName): array
    {
        $parts = preg_split('/\s+/', trim((string) $fullName), 2);
        return [$parts[0] ?: 'Customer', $parts[1] ?? 'EthioLoadAI'];
    }
}
