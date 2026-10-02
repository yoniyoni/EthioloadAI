<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class Bid extends Model
{
    protected $fillable = [
        'cargo_request_id',
        'driver_id',
        'vehicle_id',
        'amount',
        'note',
        'available_datetime',
        'status',
        'ai_score',
        'is_recommended',
        'distance_km',
        'counter_amount',
        'counter_note',
        'counter_by',
        'counter_at',
    ];

    protected function casts(): array
    {
        return [
            'amount'             => 'decimal:2',
            'counter_amount'     => 'decimal:2',
            'ai_score'           => 'float',
            'is_recommended'     => 'boolean',
            'counter_at'         => 'datetime',
            'available_datetime' => 'datetime',
        ];
    }

    public function cargoRequest()
    {
        return $this->belongsTo(CargoRequest::class);
    }

    public function driver()
    {
        return $this->belongsTo(User::class, 'driver_id');
    }

    public function vehicle()
    {
        return $this->belongsTo(Vehicle::class);
    }

    public function booking()
    {
        return $this->hasOne(Booking::class);
    }
}
