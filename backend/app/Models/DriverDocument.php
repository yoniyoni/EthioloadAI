<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Support\Facades\Storage;

class DriverDocument extends Model
{
    protected $fillable = [
        'user_id',
        'document_type',
        'file_path',
        'original_name',
        'status',
        'rejection_reason',
        'reviewed_by',
        'reviewed_at',
    ];

    protected function casts(): array
    {
        return [
            'reviewed_at' => 'datetime',
        ];
    }

    public function user()
    {
        return $this->belongsTo(User::class);
    }

    public function reviewer()
    {
        return $this->belongsTo(User::class, 'reviewed_by');
    }

    public static function labelFor(string $type): string
    {
        return match($type) {
            'license'              => "Driver's License",
            'national_id'         => 'National ID / Kebele ID',
            'vehicle_registration' => 'Vehicle Registration',
            'insurance'           => 'Insurance Certificate',
            'tin'                 => 'TIN Certificate',
            default               => ucfirst(str_replace('_', ' ', $type)),
        };
    }
}