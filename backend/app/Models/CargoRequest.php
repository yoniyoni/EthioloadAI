<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class CargoRequest extends Model
{
    /**
     * Canonical, UI-facing urgency tiers (low -> high). Every new cargo
     * request should be written with one of these. Matches the tiers
     * AiController's price multiplier and BackhaulService's scoring
     * already recognize by name.
     */
    public const URGENCY_LEVELS = ['low', 'normal', 'high', 'express'];

    /**
     * Accepted on write: the canonical set plus 'urgent', an older synonym
     * for 'express' still produced by TestScenario.php and possibly present
     * in older data — BackhaulService's scoring already treats the two as
     * equivalent. Kept separate from URGENCY_LEVELS so new UI never offers
     * 'urgent' as a choice, but existing callers using it aren't rejected.
     */
    public const ALLOWED_URGENCY_LEVELS = ['low', 'normal', 'high', 'express', 'urgent'];

    protected $fillable = [
        'user_id',
        'pickup_location',
        'pickup_lat',
        'pickup_lng',
        'pickup_latitude',
        'pickup_longitude',
        'destination',
        'destination_lat',
        'destination_lng',
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
