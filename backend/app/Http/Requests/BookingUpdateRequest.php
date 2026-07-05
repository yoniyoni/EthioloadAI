<?php

namespace App\Http\Requests;

use Illuminate\Contracts\Validation\ValidationRule;
use Illuminate\Foundation\Http\FormRequest;

class BookingUpdateRequest extends FormRequest
{
    public function authorize(): bool
    {
        return true;
    }

    public function rules(): array
    {
        return [
            'cargo_id' => 'sometimes|exists:cargo_requests,id',
            'vehicle_id' => 'sometimes|exists:vehicles,id',
            'driver_id' => 'sometimes|exists:users,id',
            'booking_status' => 'sometimes|string',
            'estimated_price' => 'sometimes|numeric',
        ];
    }
}
