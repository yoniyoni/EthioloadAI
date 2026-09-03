<?php

namespace App\Http\Requests;

use App\Support\PhoneNumber;
use Illuminate\Contracts\Validation\ValidationRule;
use Illuminate\Foundation\Http\FormRequest;

class RegisterRequest extends FormRequest
{
    /**
     * Determine if the user is authorized to make this request.
     */
    public function authorize(): bool
    {
        return true;
    }

    /**
     * Normalize phone to the canonical local format before validation runs,
     * so the uniqueness check and every later lookup (login) agree on shape.
     */
    protected function prepareForValidation(): void
    {
        if ($this->has('phone')) {
            $this->merge(['phone' => PhoneNumber::normalize($this->input('phone'))]);
        }
    }

    /**
     * Get the validation rules that apply to the request.
     *
     * @return array<string, ValidationRule|array<mixed>|string>
     */
    public function rules(): array
    {
        return [
            'full_name' => 'required|string|max:255',
            'phone' => ['required', 'string', 'regex:' . PhoneNumber::FORMAT_REGEX, 'unique:users,phone'],
            'email' => 'nullable|email|unique:users,email',
            'password' => 'required|string|min:6',
            // 'admin' is intentionally excluded — admin accounts must never be
            // self-assignable via public registration (mirrors
            // AdminApiController::createUser's role whitelist, which also
            // excludes 'admin' even for authenticated admins creating users).
            'role' => 'required|in:shipper,driver,fleet_owner',
        ];
    }

    public function messages(): array
    {
        return [
            'phone.regex' => 'Enter a valid Ethiopian phone number, e.g. 0912345678.',
        ];
    }
}
