<?php

namespace App\Services;

use App\Models\Booking;
use App\Models\Payment;

class PaymentService
{
    public function processPayment(Booking $booking, array $paymentDetails)
    {
        $amount     = (float) $booking->estimated_price;
        $commission = round($amount * 0.10, 2);
        $driverNet  = round($amount - $commission, 2);

        $payment = Payment::create([
            'booking_id'        => $booking->id,
            'amount'            => $amount,
            'commission_amount' => $commission,
            'driver_net_amount' => $driverNet,
            'paid_by'           => $paymentDetails['paid_by'] ?? null,
            'payment_method'    => $paymentDetails['payment_method'] ?? 'cash',
            'payment_status'    => 'paid',
        ]);

        $booking->update(['booking_status' => 'confirmed']);

        return $payment;
    }
}
