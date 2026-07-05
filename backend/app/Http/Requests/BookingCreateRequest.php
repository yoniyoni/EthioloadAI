<?php

namespace App\Http\Requests;

use App\Models\CargoRequest;
use Illuminate\Contracts\Validation\ValidationRule;
use Illuminate\Foundation\Http\FormRequest;

class BookingCreateRequest extends FormRequest
{
    /**
     * Determine if the user is authorized to make this request.
     */
    public function authorize(): bool
    {
        $user = auth()->user();
        if (!$user) {
            return false;
        }

        if ($user->is_admin) {
            return true;
        }

        $cargoRequest = CargoRequest::find($this->input('cargo_id'));
        if ($cargoRequest && $cargoRequest->user_id === $user->id) {
            return true;
        }

        return $user->role === 'driver' && intval($this->input('driver_id')) === $user->id;
    }

    /**
     * Get the validation rules that apply to the request.
     *
     * @return array<string, ValidationRule|array<mixed>|string>
     */
    public function rules(): array
    {
        return [
            'cargo_id' => 'required|exists:cargo_requests,id',
            'vehicle_id' => 'required|exists:vehicles,id',
            'driver_id' => 'required|exists:users,id',
            'booking_status' => 'required|string',
            'estimated_price' => 'required|numeric',
        ];
    }
}
