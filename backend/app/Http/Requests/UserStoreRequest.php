<?php

namespace App\Http\Requests;

use App\Support\PhoneNumber;
use Illuminate\Contracts\Validation\ValidationRule;
use Illuminate\Foundation\Http\FormRequest;

class UserStoreRequest extends FormRequest
{
    public function authorize(): bool
    {
        return true;
    }

    protected function prepareForValidation(): void
    {
        if ($this->has('phone')) {
            $this->merge(['phone' => PhoneNumber::normalize($this->input('phone'))]);
        }
    }

    public function rules(): array
    {
        return [
            'full_name' => 'required|string|max:255',
            'phone' => ['required', 'string', 'regex:' . PhoneNumber::FORMAT_REGEX, 'unique:users,phone'],
            'email' => 'required|email|unique:users,email',
            'password' => 'required|string|min:6',
            'role' => 'required|in:shipper,driver,admin',
        ];
    }
}
