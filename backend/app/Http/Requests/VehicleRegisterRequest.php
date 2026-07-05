<?php

namespace App\Http\Requests;

use Illuminate\Contracts\Validation\ValidationRule;
use Illuminate\Foundation\Http\FormRequest;

class VehicleRegisterRequest extends FormRequest
{
    /**
     * Determine if the user is authorized to make this request.
     */
    public function authorize(): bool
    {
        return true;
    }

    /**
     * Get the validation rules that apply to the request.
     *
     * @return array<string, ValidationRule|array<mixed>|string>
     */
    public function rules(): array
    {
        return [
            'truck_type'          => 'required|string|max:255',
            'vehicle_category'    => 'nullable|in:heavy,light',
            'plate_number'        => 'required|string|max:255|unique:vehicles,plate_number',
            'capacity'            => 'required|numeric',
            'current_city'        => 'nullable|string|max:255',
            'latitude'            => 'nullable|numeric|between:-90,90',
            'longitude'           => 'nullable|numeric|between:-180,180',
            'availability_status' => 'sometimes|string',
            'rating'              => 'sometimes|numeric|min:0|max:5',
        ];
    }
}
