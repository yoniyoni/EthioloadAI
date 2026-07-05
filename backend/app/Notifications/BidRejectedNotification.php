<?php

namespace App\Notifications;

use App\Models\Bid;
use Illuminate\Bus\Queueable;
use Illuminate\Notifications\Notification;

class BidRejectedNotification extends Notification
{
    use Queueable;

    public function __construct(protected Bid $bid, protected string $reason = 'shipper_rejected') {}

    public function via($notifiable): array
    {
        return ['database'];
    }

    public function toDatabase($notifiable): array
    {
        $cargo = $this->bid->cargoRequest;

        $message = $this->reason === 'cargo_taken'
            ? 'Another driver was selected for this cargo. Your bid was not accepted.'
            : 'Your bid was rejected by the shipper.';

        return [
            'bid_id'    => $this->bid->id,
            'cargo_id'  => $cargo?->id,
            'route'     => $cargo ? "{$cargo->pickup_location} → {$cargo->destination}" : 'Unknown route',
            'amount'    => $this->bid->amount,
            'message'   => $message,
            'type'      => 'bid_rejected',
        ];
    }
}
