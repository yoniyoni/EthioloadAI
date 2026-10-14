<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Http\Requests\RegisterRequest;
use App\Http\Resources\UserResource;
use App\Services\PasswordResetService;
use App\Support\PhoneNumber;
use Illuminate\Http\Request;

class AuthController extends Controller
{
    public function register(RegisterRequest $request)
    {
        $validated = $request->validated();
        $isDriver = $validated['role'] === 'driver';

        $user = \App\Models\User::create([
            'name'                => $validated['full_name'],
            'full_name'           => $validated['full_name'],
            'phone'               => $validated['phone'],
            'email'               => $validated['email'] ?? null,
            'password'            => $validated['password'],
            'role'                => $validated['role'],
            // Drivers start inactive until all 5 documents are admin-approved.
            // Shippers and fleet owners are active immediately.
            'is_active'           => !$isDriver,
            'verification_status' => !$isDriver,
        ]);
        $token = $user->createToken('api-token')->plainTextToken;
        return response()->json([
            'user' => new UserResource($user),
            'token' => $token,
        ], 201);
    }

    public function login(Request $request)
    {
        $request->validate([
            'identifier' => 'required|string',
            'password'   => 'required|string',
        ]);

        $identifier = trim($request->identifier);
        $field = filter_var($identifier, FILTER_VALIDATE_EMAIL) ? 'email' : 'phone';
        if ($field === 'phone') {
            $identifier = PhoneNumber::normalize($identifier);
        }

        $user = \App\Models\User::where($field, $identifier)->first();

        if (!$user || !\Illuminate\Support\Facades\Hash::check($request->password, $user->password)) {
            return response()->json(['message' => 'Invalid credentials. Check your email/phone and password.'], 401);
        }

        $token = $user->createToken('api-token')->plainTextToken;
        return response()->json([
            'user'  => new UserResource($user),
            'token' => $token,
        ]);
    }

    /**
     * POST /forgot-password — public. Always returns the same generic
     * message regardless of whether the identifier matches an account
     * (PasswordResetService::requestReset enforces this too — belt and
     * suspenders, same reasoning as login's uniform "Invalid credentials").
     */
    public function forgotPassword(Request $request, PasswordResetService $service)
    {
        $request->validate(['identifier' => 'required|string']);

        $service->requestReset(trim($request->identifier));

        return response()->json([
            'message' => 'If an account exists for that email or phone, a reset code has been sent.',
        ]);
    }

    /**
     * POST /reset-password — public. Pairs with forgotPassword(); see
     * PasswordResetService::resetPassword for the code/expiry/attempts checks.
     */
    public function resetPassword(Request $request, PasswordResetService $service)
    {
        $request->validate([
            'identifier'              => 'required|string',
            'code'                    => 'required|string',
            'new_password'            => 'required|string|min:6|confirmed',
        ]);

        try {
            $service->resetPassword(
                trim($request->identifier),
                trim($request->code),
                $request->new_password,
            );
        } catch (\RuntimeException $e) {
            return response()->json(['message' => $e->getMessage()], 422);
        }

        return response()->json(['success' => true, 'message' => 'Password reset successfully. Please log in.']);
    }

    public function logout(Request $request)
    {
        $token = $request->user()->currentAccessToken();
        if ($token) {
            $token->delete();
        }
        return response()->json(['message' => 'Logged out successfully']);
    }

    public function me(Request $request)
    {
        return response()->json(new UserResource($request->user()));
    }

    public function changePassword(Request $request)
    {
        $request->validate([
            'current_password' => 'required|string',
            'new_password'     => 'required|string|min:6',
        ]);

        $user = $request->user();

        if (!\Illuminate\Support\Facades\Hash::check($request->current_password, $user->password)) {
            return response()->json(['message' => 'Current password is incorrect.'], 422);
        }

        $user->update(['password' => \Illuminate\Support\Facades\Hash::make($request->new_password)]);

        return response()->json(['success' => true, 'message' => 'Password changed successfully.']);
    }

    public function updateProfile(Request $request)
    {
        $user = $request->user();

        if ($request->has('phone')) {
            $request->merge(['phone' => PhoneNumber::normalize($request->input('phone'))]);
        }

        $validated = $request->validate([
            'name'          => 'sometimes|string|max:255',
            'full_name'     => 'sometimes|string|max:255',
            'phone'         => ['sometimes', 'string', 'regex:' . PhoneNumber::FORMAT_REGEX, 'unique:users,phone,' . $user->id],
            'address'       => 'sometimes|nullable|string|max:255',
            'business_name' => 'sometimes|nullable|string|max:255',
        ], [
            'phone.regex' => 'Enter a valid Ethiopian phone number, e.g. 0912345678.',
            'phone.unique' => 'That phone number is already in use by another account.',
        ]);

        if (isset($validated['name']) && !isset($validated['full_name'])) {
            $validated['full_name'] = $validated['name'];
        }
        unset($validated['name']);

        $user->update($validated);
        return response()->json(new UserResource($user));
    }

    /**
     * PATCH /driver/payout-details — driver-only. The bank destination
     * PaymentService::releaseToDriver() pays out to once a shipper confirms
     * arrival. Deliberately its own endpoint/validation rather than folded
     * into updateProfile() — a wrong account number here means real money
     * goes to the wrong place, so it gets an explicit, narrow surface.
     */
    public function updatePayoutDetails(Request $request)
    {
        $user = $request->user();

        if ($user->role !== 'driver') {
            return response()->json(['message' => 'Only drivers have payout details.'], 403);
        }

        $validated = $request->validate([
            'payout_bank_code'      => 'required|string|max:50',
            'payout_account_number' => 'required|string|max:50',
            'payout_account_name'   => 'required|string|max:255',
        ]);

        $user->update($validated);
        return response()->json(new UserResource($user));
    }
}
