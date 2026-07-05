<?php

namespace App\Http\Requests;

use Illuminate\Contracts\Validation\ValidationRule;
use Illuminate\Foundation\Http\FormRequest;

class VehicleUpdateRequest extends FormRequest
{
    public function authorize(): bool
    {
        return true;
    }

    public function rules(): array
    {
        $vehicleId = $this->route('vehicle');
        return [
            'truck_type' => 'sometimes|string|max:255',
            'plate_number' => 'sometimes|string|max:255|unique:vehicles,plate_number,' . $vehicleId,
            'capacity' => 'sometimes|numeric',
            'current_city' => 'sometimes|string|max:255',
            'latitude' => 'sometimes|numeric|between:-90,90',
            'longitude' => 'sometimes|numeric|between:-180,180',
            'availability_status' => 'sometimes|string',
            'rating' => 'sometimes|numeric|min:0|max:5',
        ];
    }
}
