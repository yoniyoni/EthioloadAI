<?php

namespace App\Models;

// use Illuminate\Contracts\Auth\MustVerifyEmail;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Foundation\Auth\User as Authenticatable;
use Illuminate\Notifications\Notifiable;
use Laravel\Sanctum\HasApiTokens;

class User extends Authenticatable
{
    /** @use HasFactory<\Database\Factories\UserFactory> */
    use HasApiTokens, HasFactory, Notifiable;

    /**
     * The attributes that are mass assignable.
     *
     * @var list<string>
     */
    protected $fillable = [
        'full_name',
        'phone',
        'email',
        'password',
        'role',
        'fleet_owner_id',
        'location',
        'verification_status',
        'is_active',
    ];

    /**
     * The attributes that should be hidden for serialization.
     *
     * @var list<string>
     */
    protected $hidden = [
        'password',
        'remember_token',
    ];

    /**
     * Get the attributes that should be cast.
     *
     * @return array<string, string>
     */
    protected function casts(): array
    {
        return [
            'email_verified_at'   => 'datetime',
            'password'            => 'hashed',
            'verification_status' => 'boolean',
            'is_active'           => 'boolean',
        ];
    }

    // Relationships
    public function vehicles()
    {
        return $this->hasMany(Vehicle::class);
    }
    public function cargoRequests()
    {
        return $this->hasMany(CargoRequest::class);
    }
    public function bookings()
    {
        return $this->hasMany(Booking::class, 'driver_id');
    }

    public function documents()
    {
        return $this->hasMany(DriverDocument::class);
    }

    public function getIsAdminAttribute(): bool
    {
        return strtolower($this->role) === 'admin';
    }

    public function getIsFleetOwnerAttribute(): bool
    {
        return strtolower($this->role) === 'fleet_owner';
    }

    // Fleet owner → has many drivers
    public function drivers()
    {
        return $this->hasMany(User::class, 'fleet_owner_id');
    }

    // Driver → belongs to fleet owner
    public function fleetOwner()
    {
        return $this->belongsTo(User::class, 'fleet_owner_id');
    }
}
