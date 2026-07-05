<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class CargoRequest extends Model
{
    protected $fillable = [
        'user_id',
        'pickup_location',
        'pickup_lat',
        'pickup_lng',
        'pickup_latitude',
        'pickup_longitude',
        'destination',
        'material_type',
        'weight',
        'urgency_level',
        'budget',
        'price_type',
        'bid_deadline',
        'status',
        'service_type',
        'city',
        'pickup_area',
        'dropoff_area',
        'preferred_date',
        'items_description',
        'vehicle_type_needed',
    ];

    protected $casts = [
        'bid_deadline'   => 'datetime',
        'preferred_date' => 'date',
    ];

    public function user()
    {
        return $this->belongsTo(User::class);
    }

    public function bookings()
    {
        return $this->hasOne(Booking::class);
    }

    public function bids()
    {
        return $this->hasMany(Bid::class);
    }
}
