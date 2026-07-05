<?php

namespace App\Http\Requests;

use Illuminate\Foundation\Http\FormRequest;

class DocumentUploadRequest extends FormRequest
{
    public function authorize(): bool
    {
        return auth()->check() && auth()->user()->role === 'driver';
    }

    public function rules(): array
    {
        return [
            'document_type' => [
                'required',
                'string',
                'in:license,national_id,vehicle_registration,insurance,tin',
            ],
            // Accept images or PDFs, max 5 MB
            'file' => 'required|file|mimes:jpg,jpeg,png,pdf|max:5120',
        ];
    }
}