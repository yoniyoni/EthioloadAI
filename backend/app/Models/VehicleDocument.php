<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Support\Facades\Storage;

class VehicleDocument extends Model
{
    protected $fillable = [
        'vehicle_id',
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

    public function vehicle()
    {
        return $this->belongsTo(Vehicle::class);
    }

    public function reviewer()
    {
        return $this->belongsTo(User::class, 'reviewed_by');
    }

    public static function labelFor(string $type): string
    {
        return match($type) {
            'registration_certificate'   => 'Vehicle Registration Certificate',
            'insurance_certificate'      => 'Insurance Certificate',
            'roadworthiness_certificate' => 'Roadworthiness / Inspection Certificate',
            'ownership_document'         => 'Ownership Document',
            default                      => ucfirst(str_replace('_', ' ', $type)),
        };
    }
}
