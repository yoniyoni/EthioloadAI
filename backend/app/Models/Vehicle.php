<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

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
}
