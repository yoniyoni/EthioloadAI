<?php

namespace App\Http\Requests;

use Illuminate\Foundation\Http\FormRequest;

class ChapaInitializeRequest extends FormRequest
{
    public function authorize(): bool
    {
        // Ownership (shipper-only) is enforced in PaymentController::initializeChapa.
        return true;
    }

    public function rules(): array
    {
        return [
            'booking_id' => 'required|exists:bookings,id',
        ];
    }
}
