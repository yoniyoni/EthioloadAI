<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Support\Collection;

class Vehicle extends Model
{
    protected $fillable = [
        'user_id',
        'fleet_owner_id',
        'truck_type',
        'vehicle_category',
        'plate_number',
        'capacity',
        'current_city',
        'latitude',
        'longitude',
        'last_location_at',
        'availability_status',
        'rating',
        'verification_status',
    ];

    protected $casts = [
        'last_location_at' => 'datetime',
        'latitude'         => 'float',
        'longitude'        => 'float',
    ];

    public function user()
    {
        return $this->belongsTo(User::class);
    }

    public function fleetOwner()
    {
        return $this->belongsTo(User::class, 'fleet_owner_id');
    }

    public function bookings()
    {
        return $this->hasMany(Booking::class);
    }

    public function documents()
    {
        return $this->hasMany(VehicleDocument::class);
    }

    /**
     * Which of these vehicle IDs are currently mid-trip — a non-terminal
     * booking whose trip is 'ongoing'. This is the one place "is this
     * vehicle busy" is computed; don't trust availability_status for this —
     * it's only ever set by the bookDirect flow and only ever reset on
     * cancellation, so it goes stale for the normal bid-accept flow and
     * never resets on natural delivery/completion. One bulk query, safe to
     * call with any number of IDs (not N+1).
     */
    public static function busyVehicleIds(iterable $vehicleIds): Collection
    {
        return Booking::whereIn('vehicle_id', $vehicleIds)
            ->whereNotIn('booking_status', ['cancelled', 'completed', 'delivered'])
            ->whereHas('trip', fn ($q) => $q->where('trip_status', 'ongoing'))
            ->pluck('vehicle_id')
            ->unique();
    }
}
