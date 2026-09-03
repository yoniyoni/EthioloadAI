<?php

namespace App\Services;

/**
 * No real SMS gateway is wired up yet — this is an honest stub, not a
 * guessed integration (unlike ChapaService, no provider was named for this
 * one). Swap in a real implementation the same way MockChapaService ↔
 * ChapaService already works: bind this class in AppServiceProvider based
 * on config('services.sms_gateway.driver'), point SMS_GATEWAY_DRIVER at
 * something other than 'mock', and nothing calling send() needs to change.
 *
 * To wire up a real provider: extend or replace this class, implement
 * send() with the provider's HTTP call, and add its credentials to
 * config/services.php + .env — same shape as ChapaService's constructor.
 */
class SmsService
{
    public function send(string $phone, string $message): bool
    {
        throw new \RuntimeException(
            'SMS gateway not configured. Set SMS_GATEWAY_DRIVER=mock for development, '
            . 'or implement SmsService::send() for a real provider before using SMS_GATEWAY_DRIVER=live.'
        );
    }
}
