<?php

namespace App\Http\Requests;

use Illuminate\Contracts\Validation\ValidationRule;
use Illuminate\Foundation\Http\FormRequest;

class CargoUpdateRequest extends FormRequest
{
    public function authorize(): bool
    {
        return true;
    }

    public function rules(): array
    {
        return [
            'pickup_location' => 'sometimes|string|max:255',
            'destination' => 'sometimes|string|max:255',
            'material_type' => 'sometimes|string|max:255',
            'weight' => 'sometimes|numeric',
            'urgency_level' => 'sometimes|string|max:255',
            'budget' => 'sometimes|numeric',
            'status' => 'sometimes|in:pending,matched,completed',
        ];
    }
}
