<?php

namespace App\Notifications;

use App\Models\Bid;
use Illuminate\Bus\Queueable;
use Illuminate\Notifications\Notification;

class BidCounteredNotification extends Notification
{
    use Queueable;

    public function __construct(protected Bid $bid) {}

    public function via($notifiable): array
    {
        return ['database'];
    }

    public function toDatabase($notifiable): array
    {
        $cargo = $this->bid->cargoRequest;
        return [
            'bid_id'          => $this->bid->id,
            'cargo_id'        => $cargo?->id,
            'route'           => $cargo ? "{$cargo->pickup_location} → {$cargo->destination}" : 'Unknown route',
            'original_amount' => $this->bid->amount,
            'counter_amount'  => $this->bid->counter_amount,
            'counter_note'    => $this->bid->counter_note,
            'message'         => 'The shipper sent a counter-offer on your bid.',
            'type'            => 'bid_countered',
        ];
    }
}
