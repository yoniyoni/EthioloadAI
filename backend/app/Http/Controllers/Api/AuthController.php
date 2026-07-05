<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Http\Requests\RegisterRequest;
use App\Http\Resources\UserResource;
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
        $validated = $request->validate([
            'name'          => 'sometimes|string|max:255',
            'full_name'     => 'sometimes|string|max:255',
            'phone'         => 'sometimes|string|max:50',
            'address'       => 'sometimes|nullable|string|max:255',
            'business_name' => 'sometimes|nullable|string|max:255',
        ]);

        if (isset($validated['name']) && !isset($validated['full_name'])) {
            $validated['full_name'] = $validated['name'];
        }
        unset($validated['name']);

        $user->update($validated);
        return response()->json(new UserResource($user));
    }
}
