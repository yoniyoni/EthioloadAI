<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class Rating extends Model
{
    protected $fillable = [
        'booking_id',
        'shipper_id',
        'driver_id',
        'rater_id',
        'rating',
        'feedback',
    ];

    public function booking()
    {
        return $this->belongsTo(Booking::class);
    }

    public function shipper()
    {
        return $this->belongsTo(User::class, 'shipper_id');
    }

    public function driver()
    {
        return $this->belongsTo(User::class, 'driver_id');
    }
}
