<?php

namespace App\Notifications;

use App\Models\Bid;
use Illuminate\Notifications\Notification;

class BidPlacedNotification extends Notification
{
    public function __construct(private Bid $bid) {}

    public function via($notifiable): array
    {
        return ['database'];
    }

    public function toDatabase($notifiable): array
    {
        $cargo = $this->bid->cargoRequest;

        return [
            'bid_id'      => $this->bid->id,
            'cargo_id'    => $this->bid->cargo_request_id,
            'route'       => ($cargo->pickup_location ?? '') . ' → ' . ($cargo->destination ?? ''),
            'amount'      => $this->bid->amount,
            'driver_name' => $this->bid->driver?->full_name ?? $this->bid->driver?->name,
            'message'     => 'A driver placed a bid on your cargo request.',
            'type'        => 'bid_placed',
        ];
    }
}
