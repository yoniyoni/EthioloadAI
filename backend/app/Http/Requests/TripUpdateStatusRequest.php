<?php

namespace App\Http\Requests;

use Illuminate\Foundation\Http\FormRequest;

class TripUpdateStatusRequest extends FormRequest
{
    public function authorize(): bool
    {
        return true;
    }

    public function rules(): array
    {
        return [
            'trip_status' => 'required|string|in:ongoing,completed',
        ];
    }
}
