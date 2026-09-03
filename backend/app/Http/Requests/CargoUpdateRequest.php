<?php

namespace App\Http\Requests;

use App\Models\CargoRequest;
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
            'weight' => 'sometimes|numeric|min:0.01',
            'urgency_level' => 'sometimes|in:' . implode(',', CargoRequest::ALLOWED_URGENCY_LEVELS),
            'budget' => 'sometimes|numeric|min:0',
            'status' => 'sometimes|in:pending,matched,completed,expired',
        ];
    }
}
