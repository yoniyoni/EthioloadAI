<?php

namespace App\Notifications;

use App\Models\Trip;
use Illuminate\Bus\Queueable;
use Illuminate\Notifications\Notification;

class TripPickedUpNotification extends Notification
{
    use Queueable;

    protected $trip;

    public function __construct(Trip $trip)
    {
        $this->trip = $trip;
    }

    public function via($notifiable)
    {
        return ['database'];
    }

    public function toDatabase($notifiable)
    {
        return [
            'trip_id'    => $this->trip->id,
            'booking_id' => $this->trip->booking_id,
            'status'     => 'picked_up',
            'message'    => 'Your driver has picked up the cargo and is on the way to the destination.',
        ];
    }
}
