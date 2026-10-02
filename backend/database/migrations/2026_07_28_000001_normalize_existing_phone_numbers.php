<?php

use App\Support\PhoneNumber;
use Illuminate\Database\Migrations\Migration;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Log;

/**
 * Backfills existing `users.phone` values to the canonical local format
 * (App\Support\PhoneNumber::normalize()). Before this, accounts created
 * through different clients ended up with the same logical number stored in
 * different shapes (e.g. "0912122323" vs "+251911000301"), which silently
 * broke phone-based login for any account not already in local format,
 * since AuthController::login/AdminApiController::login do a raw
 * `where('phone', $identifier)` lookup. This migration only changes the
 * *shape* of existing values, never the underlying number, and skips a row
 * if normalizing it would collide with another user's existing phone
 * (logged, not thrown) rather than aborting the whole backfill.
 */
return new class extends Migration
{
    public function up(): void
    {
        DB::table('users')->select('id', 'phone')->orderBy('id')->chunkById(200, function ($users) {
            foreach ($users as $user) {
                $normalized = PhoneNumber::normalize($user->phone);
                if ($normalized === $user->phone) {
                    continue;
                }

                $collision = DB::table('users')->where('phone', $normalized)->where('id', '!=', $user->id)->exists();
                if ($collision) {
                    Log::warning("Skipped phone normalization for user {$user->id}: "
                        . "'{$user->phone}' -> '{$normalized}' collides with an existing account.");
                    continue;
                }

                DB::table('users')->where('id', $user->id)->update(['phone' => $normalized]);
            }
        });
    }

    public function down(): void
    {
        // Original raw values are not preserved — this is a one-way shape
        // normalization, not a data change, so there is nothing to revert to.
    }
};
