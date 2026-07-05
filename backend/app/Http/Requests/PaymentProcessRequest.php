<?php

namespace App\Http\Requests;

use Illuminate\Foundation\Http\FormRequest;

class PaymentProcessRequest extends FormRequest
{
    /**
     * Determine if the user is authorized to make this request.
     */
    public function authorize(): bool
    {
        // Add authorization logic if needed, usually anyone making a booking can pay
        return true;
    }

    /**
     * Get the validation rules that apply to the request.
     *
     * @return array<string, \Illuminate\Contracts\Validation\ValidationRule|array<mixed>|string>
     */
    public function rules(): array
    {
        return [
            'booking_id'     => 'required|exists:bookings,id',
            'payment_method' => 'required|string|in:in_app,cash,bank_transfer,telebirr,cbe_birr,chapa,awash_bank,dashen_bank',
        ];
    }
}
