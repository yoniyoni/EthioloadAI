<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Builder;

class TripStop extends Model
{
    protected $fillable = [
        'trip_id',
        'cargo_request_id',
        'stop_order',
        'location_name',
        'pickup_lat',
        'pickup_lng',
        'agreed_price',
        'status',
        'notes',
        'arrived_at',
        'completed_at',
    ];

    protected function casts(): array
    {
        return [
            'agreed_price' => 'decimal:2',
            'pickup_lat'   => 'float',
            'pickup_lng'   => 'float',
            'arrived_at'   => 'datetime',
            'completed_at' => 'datetime',
        ];
    }

    public function trip(): \Illuminate\Database\Eloquent\Relations\BelongsTo
    {
        return $this->belongsTo(Trip::class);
    }

    public function cargoRequest(): \Illuminate\Database\Eloquent\Relations\BelongsTo
    {
        return $this->belongsTo(CargoRequest::class);
    }

    /** Order stops ascending by stop_order for timeline rendering. */
    public function scopeInOrder(Builder $query): Builder
    {
        return $query->orderBy('stop_order');
    }
}
