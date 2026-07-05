<?php

namespace App\Notifications;

use App\Models\Booking;
use Illuminate\Bus\Queueable;
use Illuminate\Notifications\Notification;

class BookingCreatedNotification extends Notification
{
    use Queueable;

    protected $booking;
    protected string $forRole;

    public function __construct(Booking $booking, string $forRole = 'driver')
    {
        $this->booking = $booking->loadMissing('cargoRequest');
        $this->forRole = $forRole;
    }

    public function via($notifiable)
    {
        return ['database'];
    }

    public function toDatabase($notifiable)
    {
        $amount = number_format((float) $this->booking->estimated_price, 0, '.', ',');
        $route  = '';
        if ($this->booking->cargoRequest) {
            $route = $this->booking->cargoRequest->pickup_location
                   . ' → '
                   . $this->booking->cargoRequest->destination;
        }

        if ($this->forRole === 'shipper') {
            $title   = 'Booking Confirmed!';
            $message = 'A driver confirmed and accepted your cargo booking.'
                     . ($route ? " Route: {$route}." : '');
        } else {
            $title   = 'ጨረታ ተቀበልን! / Bid Accepted!';
            $message = "ጨረታዎ ተቀባይነት አግኝቷል! Your bid of ETB {$amount}"
                     . ($route ? " for {$route}" : '')
                     . ' was accepted. Prepare for pickup.';
        }

        return [
            'booking_id'      => $this->booking->id,
            'title'           => $title,
            'message'         => $message,
            'estimated_price' => $this->booking->estimated_price,
            'type'            => 'booking_created',
        ];
    }
}
