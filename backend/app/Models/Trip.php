<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class Trip extends Model
{
    protected $fillable = [
        'booking_id',
        'start_location',
        'destination',
        'route_data',
        'trip_status',
        'trip_type',
        'total_stops',
        'completed_stops',
        'start_time',
        'end_time',
    ];

    protected $casts = [
        'route_data'      => 'array',
        'start_time'      => 'datetime',
        'end_time'        => 'datetime',
        'total_stops'     => 'integer',
        'completed_stops' => 'integer',
    ];

    protected $appends = ['total_amount'];

    public function booking(): \Illuminate\Database\Eloquent\Relations\BelongsTo
    {
        return $this->belongsTo(Booking::class);
    }

    public function tripStops(): \Illuminate\Database\Eloquent\Relations\HasMany
    {
        return $this->hasMany(TripStop::class)->orderBy('stop_order');
    }

    /** Sum of all stop agreed prices. Eager-loads stops if not already loaded. */
    public function getTotalAmountAttribute(): float
    {
        if ($this->relationLoaded('tripStops')) {
            return (float) $this->tripStops->sum('agreed_price');
        }
        return (float) $this->tripStops()->sum('agreed_price');
    }

    public function isMultiStop(): bool
    {
        return $this->trip_type === 'multi_stop';
    }
}
