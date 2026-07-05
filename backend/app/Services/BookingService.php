<?php

namespace App\Services;

use App\Models\Booking;
use App\Notifications\BookingCreatedNotification;

class BookingService
{
    public function createBooking(array $data)
    {
        // Commission is 10% of estimated price
        if (isset($data['estimated_price'])) {
            $data['commission_fee'] = $data['estimated_price'] * 0.10;
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
            $data['commission_fee'] = $data['estimated_price'] * 0.10;
        }

        $booking->update($data);
        return $booking;
    }

    public function deleteBooking(Booking $booking)
    {
        $booking->delete();
    }
}
