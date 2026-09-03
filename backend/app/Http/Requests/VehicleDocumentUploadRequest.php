<?php

namespace App\Http\Requests;

use Illuminate\Foundation\Http\FormRequest;

class VehicleDocumentUploadRequest extends FormRequest
{
    public function authorize(): bool
    {
        return auth()->check() && in_array(auth()->user()->role, ['driver', 'fleet_owner']);
    }

    public function rules(): array
    {
        return [
            'document_type' => [
                'required',
                'string',
                'in:registration_certificate,insurance_certificate,roadworthiness_certificate,ownership_document',
            ],
            // Accept images or PDFs, max 5 MB
            'file' => 'required|file|mimes:jpg,jpeg,png,pdf|max:5120',
        ];
    }
}
