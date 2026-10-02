<?php

namespace App\Http\Requests;

use Illuminate\Foundation\Http\FormRequest;

class TripCreateRequest extends FormRequest
{
    public function authorize(): bool
    {
        return true; // Handle in controller
    }

    public function rules(): array
    {
        return [
            'booking_id' => 'required|exists:bookings,id',
        ];
    }
}
