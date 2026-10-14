<?php

namespace App\Events;

use App\Models\Trip;
use Illuminate\Broadcasting\Channel;
use Illuminate\Broadcasting\InteractsWithSockets;
use Illuminate\Broadcasting\PresenceChannel;
use Illuminate\Broadcasting\PrivateChannel;
use Illuminate\Contracts\Broadcasting\ShouldBroadcastNow;
use Illuminate\Foundation\Events\Dispatchable;
use Illuminate\Queue\SerializesModels;

class TripLocationUpdated implements ShouldBroadcastNow
{
    use Dispatchable, InteractsWithSockets, SerializesModels;

    public $trip;
    public $gpsData;

    /**
     * Create a new event instance.
     */
    public function __construct(Trip $trip, array $gpsData)
    {
        $this->trip = $trip;
        $this->gpsData = $gpsData;
    }

    /**
     * Get the channels the event should broadcast on.
     *
     * @return array<int, \Illuminate\Broadcasting\Channel>
     */
    public function broadcastOn(): array
    {
        // Broadcasts to a private channel for this specific trip
        return [
            new PrivateChannel('trip.' . $this->trip->id),
        ];
    }

    /**
     * The event's broadcast name.
     */
    public function broadcastAs(): string
    {
        return 'TripLocationUpdated';
    }

    /**
     * Get the data to broadcast.
     *
     * @return array<string, mixed>
     */
    public function broadcastWith(): array
    {
        return [
            'trip_id' => $this->trip->id,
            'lat' => $this->gpsData['lat'],
            'lng' => $this->gpsData['lng'],
            'timestamp' => now()->toIso8601String(),
        ];
    }
}
