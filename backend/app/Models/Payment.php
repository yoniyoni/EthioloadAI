<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class Payment extends Model
{
    protected $fillable = [
        'booking_id',
        'amount',
        'commission_amount',
        'driver_net_amount',
        'paid_by',
        'payment_method',
        'payment_status',
        'transaction_ref',
    ];

    public function booking()
    {
        return $this->belongsTo(Booking::class);
    }
}
