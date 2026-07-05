<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class BackhaulRecommendation extends Model
{
    protected $table = 'backhaul_recommendations';

    protected $fillable = [
        'trip_id',
        'driver_id',
        'cargo_request_id',
        'score',
        'status',
        'metadata',
    ];

    protected $casts = [
        'score'    => 'float',
        'metadata' => 'array',
    ];

    public function trip(): \Illuminate\Database\Eloquent\Relations\BelongsTo
    {
        return $this->belongsTo(Trip::class);
    }

    public function driver(): \Illuminate\Database\Eloquent\Relations\BelongsTo
    {
        return $this->belongsTo(User::class, 'driver_id');
    }

    public function cargoRequest(): \Illuminate\Database\Eloquent\Relations\BelongsTo
    {
        return $this->belongsTo(CargoRequest::class);
    }
}
