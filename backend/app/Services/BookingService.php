<?php

namespace App\Services;

use App\Models\Booking;
use App\Models\Payment;
use App\Notifications\BookingCreatedNotification;

class BookingService
{
    public function __construct(private PaymentService $paymentService)
    {
    }

    public function createBooking(array $data)
    {
        if (isset($data['estimated_price'])) {
            $data['commission_fee'] = $data['estimated_price'] * config('services.commission_rate');
        }

        $booking = Booking::create($data);

        // Mark the cargo request as matched so it's no longer shown as available
        if ($booking->cargoRequest) {
            $booking->cargoRequest->update(['status' => 'matched']);
        }

        if ($booking->driver) {
            $booking->driver->notify(new BookingCreatedNotification($booking));
        }
        
        return $booking;
    }

    public function updateBooking(Booking $booking, array $data)
    {
        if (isset($data['estimated_price'])) {
            $data['commission_fee'] = $data['estimated_price'] * config('services.commission_rate');
        }

        $booking->update($data);
        return $booking;
    }

    public function deleteBooking(Booking $booking)
    {
        $booking->delete();
    }

    /**
     * Releases the shipper's already-collected payment to the driver and
     * closes out the booking. The booking becomes 'completed' regardless of
     * whether the payout itself succeeds — a failed transfer is an
     * operational follow-up (see Payment::payout_status), not a reason to
     * leave the shipper's confirmation stuck.
     */
    public function confirmArrival(Booking $booking): Booking
    {
        // Queried fresh rather than via $booking->payment, same reasoning as
        // PaymentService::initializeChapaPayment() — never trust a relation
        // that might have been cached before this row's current state.
        // Prefers a 'paid' row if one exists, same reasoning as the
        // trip-start gate in TripService::startTrip() — a booking should
        // never have more than one Payment row, but this stays correct even
        // if that invariant is ever violated.
        $payment = Payment::where('booking_id', $booking->id)->where('payment_status', 'paid')->first()
            ?? Payment::where('booking_id', $booking->id)->first();
        if ($payment) {
            $this->paymentService->releaseToDriver($payment);
        }

        $booking->update(['booking_status' => 'completed']);

        return $booking->fresh(['payment']);
    }
}
