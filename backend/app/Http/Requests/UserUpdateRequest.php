<?php

namespace App\Http\Requests;

use App\Support\PhoneNumber;
use Illuminate\Contracts\Validation\ValidationRule;
use Illuminate\Foundation\Http\FormRequest;

class UserUpdateRequest extends FormRequest
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
        $userId = $this->route('user');
        return [
            'full_name' => 'sometimes|string|max:255',
            'phone' => ['sometimes', 'string', 'regex:' . PhoneNumber::FORMAT_REGEX, 'unique:users,phone,' . $userId],
            'email' => 'sometimes|email|unique:users,email,' . $userId,
            'password' => 'sometimes|string|min:6',
            'role' => 'sometimes|in:shipper,driver,admin',
        ];
    }
}
