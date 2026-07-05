<?php

namespace App\Http\Requests;

use Illuminate\Contracts\Validation\ValidationRule;
use Illuminate\Foundation\Http\FormRequest;

class CargoCreateRequest extends FormRequest
{
    public function authorize(): bool
    {
        return true;
    }

    public function rules(): array
    {
        $serviceType = $this->input('service_type', 'intercity');

        if ($serviceType === 'intracity') {
            return [
                'service_type'        => 'nullable|in:intercity,intracity',
                'city'                => 'required|string|max:255',
                'pickup_area'         => 'required|string|max:500',
                'dropoff_area'        => 'required|string|max:500',
                'preferred_date'      => 'required|date|after_or_equal:today',
                'items_description'   => 'required|string|max:2000',
                'vehicle_type_needed' => 'nullable|string|max:100',
                'bid_deadline'        => 'nullable|date|after:now',
                'budget'              => 'nullable|numeric',
                'price_type'          => 'nullable|in:fixed,negotiable',
                'status'              => 'sometimes|in:pending,matched,completed',
                'pickup_lat'          => 'nullable|numeric|between:-90,90',
                'pickup_lng'          => 'nullable|numeric|between:-180,180',
            ];
        }

        // intercity (default)
        return [
            'service_type'    => 'nullable|in:intercity,intracity',
            'pickup_location' => 'required|string|max:255',
            'destination'     => 'required|string|max:255',
            'material_type'   => 'required|string|max:255',
            'weight'          => 'required|numeric',
            'urgency_level'   => 'required|string|max:255',
            'budget'          => 'nullable|numeric',
            'price_type'      => 'nullable|in:fixed,negotiable',
            'bid_deadline'    => 'nullable|date|after:now',
            'status'          => 'sometimes|in:pending,matched,completed',
            'pickup_lat'      => 'nullable|numeric|between:-90,90',
            'pickup_lng'      => 'nullable|numeric|between:-180,180',
        ];
    }
}
