<?php

namespace App\Http\Resources;

use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\JsonResource;

class UserResource extends JsonResource
{
    /**
     * Transform the resource into an array.
     *
     * @return array<string, mixed>
     */
    public function toArray(Request $request): array
    {
        return [
            'id' => $this->id,
            'full_name' => $this->full_name,
            'phone' => $this->phone,
            'email' => $this->email,
            'role' => $this->role,
            'location' => $this->location,
            'verification_status' => $this->verification_status,
            'is_active'           => $this->is_active,
            // Own-profile only — never exposed to the other party in a
            // booking (BidResource/BookingResource don't include these).
            'payout_bank_code'      => $this->payout_bank_code,
            'payout_account_number' => $this->payout_account_number,
            'payout_account_name'   => $this->payout_account_name,
            'created_at' => $this->created_at,
            'updated_at' => $this->updated_at,
        ];
    }
}
