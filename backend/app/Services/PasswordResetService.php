<?php

namespace App\Services;

use App\Models\User;
use App\Support\PhoneNumber;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Hash;
use Illuminate\Support\Facades\Log;
use Illuminate\Support\Facades\Mail;

class PasswordResetService
{
    public function __construct(private SmsService $sms) {}

    /**
     * Always completes the same way regardless of whether the identifier
     * matches an account — deliberately no "no account found" branch that a
     * caller could distinguish, so this can't be used to enumerate which
     * phones/emails are registered.
     */
    public function requestReset(string $identifier): void
    {
        [$field, $normalized] = $this->detectChannel($identifier);

        $user = User::where($field, $normalized)->first();
        if (!$user) {
            return;
        }

        $code = (string) random_int(100000, 999999);

        DB::table('password_reset_codes')->insert([
            'identifier'  => $normalized,
            'channel'     => $field,
            'code_hash'   => Hash::make($code),
            'expires_at'  => now()->addMinutes(10),
            'attempts'    => 0,
            'created_at'  => now(),
            'updated_at'  => now(),
        ]);

        $message = "Your EthioLoadAI password reset code is {$code}. It expires in 10 minutes.";

        if ($field === 'phone') {
            $this->sms->send($normalized, $message);
        } else {
            Mail::raw($message, function ($mail) use ($normalized) {
                $mail->to($normalized)->subject('Your EthioLoadAI password reset code');
            });

            // MAIL_MAILER=log writes the email via Laravel's own internal
            // ::info() call, which this project's LOG_LEVEL=error silently
            // drops (found the same way as MockSmsService's identical
            // issue — by testing, the log stayed empty). Only fires while
            // mail is actually in log-only mode, so this never runs once a
            // real mailer is configured.
            if (config('mail.default') === 'log') {
                Log::error("[PasswordResetService] Email code for {$normalized}: {$code}");
            }
        }
    }

    /**
     * @throws \RuntimeException with a message safe to show the user —
     *         deliberately the same generic text for "no code requested",
     *         "expired", "too many attempts", and "wrong code", so none of
     *         those states can be distinguished from the outside either.
     */
    public function resetPassword(string $identifier, string $code, string $newPassword): void
    {
        [$field, $normalized] = $this->detectChannel($identifier);
        $invalid = fn () => throw new \RuntimeException('Invalid or expired code.');

        // Deliberately outside the transaction below: DB::transaction()
        // rolls back everything in its closure when it throws, which would
        // silently undo the attempts increment on a wrong guess — exactly
        // the counter this method depends on to cap brute-force attempts.
        // A plain UPDATE here commits immediately regardless of what
        // happens next.
        $row = DB::table('password_reset_codes')
            ->where('identifier', $normalized)
            ->whereNull('consumed_at')
            ->orderByDesc('id')
            ->first();

        if (!$row || $row->attempts >= 5 || now()->gt($row->expires_at)) {
            $invalid();
        }

        if (!Hash::check($code, $row->code_hash)) {
            DB::table('password_reset_codes')->where('id', $row->id)->increment('attempts');
            $invalid();
        }

        DB::transaction(function () use ($field, $normalized, $row, $newPassword, $invalid) {
            // Atomic claim: only succeeds if still unconsumed, so two
            // concurrent resetPassword() calls racing on the same code
            // can't both get through — the loser's affected-row count is 0.
            $claimed = DB::table('password_reset_codes')
                ->where('id', $row->id)
                ->whereNull('consumed_at')
                ->update(['consumed_at' => now()]);

            if (!$claimed) {
                $invalid();
            }

            $user = User::where($field, $normalized)->first();
            if (!$user) {
                $invalid();
            }

            $user->update(['password' => Hash::make($newPassword)]);
            // Force re-login everywhere — a password reset is exactly the
            // moment an old, possibly-compromised session should stop working.
            $user->tokens()->delete();
        });
    }

    /**
     * @return array{0: 'email'|'phone', 1: string} the User column to match
     *         on and the normalized value to match with — same detection
     *         AuthController::login() already uses.
     */
    private function detectChannel(string $identifier): array
    {
        $identifier = trim($identifier);
        if (filter_var($identifier, FILTER_VALIDATE_EMAIL)) {
            return ['email', $identifier];
        }
        return ['phone', PhoneNumber::normalize($identifier)];
    }
}
