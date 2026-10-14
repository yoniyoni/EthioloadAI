<?php

namespace App\Http\Requests;

use App\Models\User;
use Illuminate\Foundation\Http\FormRequest;

class FleetDriverDocumentUploadRequest extends FormRequest
{
    public function authorize(): bool
    {
        $user = auth()->user();
        if (!$user || $user->role !== 'fleet_owner') {
            return false;
        }
        $driver = User::find($this->route('driverId'));
        return $driver !== null && $driver->fleet_owner_id === $user->id;
    }

    public function rules(): array
    {
        return [
            'document_type' => ['required', 'string', 'in:license,national_id,vehicle_registration,insurance,tin,professional_permit'],
            'file' => 'required|file|mimes:jpg,jpeg,png,pdf|max:5120',
        ];
    }
}
