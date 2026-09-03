<?php

namespace App\Services;

use Illuminate\Support\Facades\Log;

/**
 * Development stand-in for SmsService — logs the message instead of calling
 * a real gateway, exactly mirroring MockChapaService's role for payments.
 * Lets the whole forgot-password flow be built and tested end-to-end today
 * with zero external dependencies; flip SMS_GATEWAY_DRIVER to swap in a
 * real provider later with no change to PasswordResetService.
 */
class MockSmsService extends SmsService
{
    public function send(string $phone, string $message): bool
    {
        // .env has LOG_LEVEL=error in this project, which silently drops
        // Log::info() — found by testing, not assumption (an earlier
        // version of this used ::info and the code never actually showed
        // up anywhere). ::error() is what actually reaches the log file
        // under the current config; same reason MockChapaService's
        // sibling "Chapa secret key not configured" notice is also logged
        // at error level despite not being a real error.
        Log::error("[MockSmsService] SMS to {$phone}: {$message}");
        return true;
    }
}
