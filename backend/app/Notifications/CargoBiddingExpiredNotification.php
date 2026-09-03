<?php

namespace App\Notifications;

use App\Models\CargoRequest;
use Illuminate\Bus\Queueable;
use Illuminate\Notifications\Notification;

/**
 * Sent to the shipper once, when CargoExpiryService flips their cargo from
 * 'pending' to 'expired' — the bid deadline passed with no booking made.
 * Prompts them to repost with a new bid_deadline via
 * POST /cargo-requests/{id}/repost.
 */
class CargoBiddingExpiredNotification extends Notification
{
    use Queueable;

    public function __construct(protected CargoRequest $cargo) {}

    public function via($notifiable): array
    {
        return ['database'];
    }

    public function toDatabase($notifiable): array
    {
        $route = $this->cargo->service_type === 'intracity'
            ? "{$this->cargo->city} — {$this->cargo->pickup_area}"
            : "{$this->cargo->pickup_location} → {$this->cargo->destination}";

        return [
            'cargo_id' => $this->cargo->id,
            'route'    => $route,
            'message'  => 'No driver was booked before your bid deadline passed. Set a new date to reopen bidding.',
            'type'     => 'cargo_bidding_expired',
        ];
    }
}
