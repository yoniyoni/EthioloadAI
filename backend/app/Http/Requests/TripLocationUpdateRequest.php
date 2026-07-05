<?php

namespace App\Http\Requests;

use Illuminate\Foundation\Http\FormRequest;

class TripLocationUpdateRequest extends FormRequest
{
    public function authorize(): bool
    {
        return true;
    }

    public function rules(): array
    {
        return [
            'lat' => 'required|numeric',
            'lng' => 'required|numeric',
        ];
    }
}
