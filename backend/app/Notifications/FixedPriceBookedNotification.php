<?php

namespace App\Notifications;

use App\Models\Booking;
use Illuminate\Notifications\Notification;

class FixedPriceBookedNotification extends Notification
{
    public function __construct(private Booking $booking) {}

    public function via($notifiable): array
    {
        return ['database'];
    }

    public function toDatabase($notifiable): array
    {
        $cargo  = $this->booking->cargoRequest;
        $driver = $this->booking->driver;

        return [
            'booking_id'  => $this->booking->id,
            'cargo_id'    => $cargo?->id,
            'route'       => ($cargo?->pickup_location ?? '') . ' → ' . ($cargo?->destination ?? ''),
            'amount'      => $this->booking->estimated_price,
            'driver_name' => $driver?->full_name ?? $driver?->name,
            'message'     => 'A driver accepted your fixed price and booked your cargo.',
            'type'        => 'fixed_price_booked',
        ];
    }
}
