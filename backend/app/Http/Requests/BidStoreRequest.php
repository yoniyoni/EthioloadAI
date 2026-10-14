<?php

namespace App\Http\Requests;

use Illuminate\Foundation\Http\FormRequest;

class BidStoreRequest extends FormRequest
{
    public function authorize(): bool
    {
        return auth()->check() && in_array(auth()->user()->role, ['driver', 'fleet_owner']);
    }

    public function rules(): array
    {
        return [
            'vehicle_id'         => 'required|integer|exists:vehicles,id',
            'amount'             => 'required|numeric|min:1|max:9999999.99',
            'note'               => 'nullable|string|max:500',
            'available_datetime' => 'nullable|date|after:now',
        ];
    }

    public function messages(): array
    {
        return [
            'amount.min'                  => 'Bid amount must be at least 1 ETB.',
            'available_datetime.after'    => 'Available datetime must be in the future.',
        ];
    }
}
