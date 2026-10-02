<?php

namespace App\Support;

/**
 * Ethiopian phone numbers arrive in inconsistent shapes depending on which
 * client wrote them: the mobile app's plain input yields local format
 * ("0912345678"), the web app's decorative-but-unwired "+251" prefix field
 * yields a bare 9-digit string, and manually entered numbers sometimes
 * include the country code ("+251912345678" / "251912345678"). Both
 * AuthController::login and AdminApiController::login do a raw
 * `where('phone', $identifier)` lookup, so unless every write path and every
 * lookup converge on one shape, otherwise-identical numbers silently fail to
 * match — which is exactly what was happening (confirmed against live data:
 * the same logical account shape stored as both "0912122323" and
 * "+251911000301" style values). Local 10-digit format is the canonical
 * shape: it's what the mobile app's own hint ("e.g. 0912 345 678") asks for
 * and what the majority of existing accounts already use.
 */
class PhoneNumber
{
    public const FORMAT_REGEX = '/^0\d{9}$/';

    public static function normalize(?string $raw): ?string
    {
        if ($raw === null) {
            return null;
        }

        $digits = preg_replace('/\D+/', '', $raw);

        if ($digits === '') {
            return $raw; // not numeric at all — let the format rule reject it as-is
        }

        if (str_starts_with($digits, '251') && strlen($digits) === 12) {
            $digits = '0' . substr($digits, 3);
        } elseif (strlen($digits) === 9) {
            $digits = '0' . $digits;
        }

        return $digits;
    }
}
