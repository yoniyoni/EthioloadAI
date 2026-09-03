<?php

return [

    /*
    |--------------------------------------------------------------------------
    | Third Party Services
    |--------------------------------------------------------------------------
    |
    | This file is for storing the credentials for third party services such
    | as Mailgun, Postmark, AWS and more. This file provides the de facto
    | location for this type of information, allowing packages to have
    | a conventional file to locate the various service credentials.
    |
    */

    'postmark' => [
        'token' => env('POSTMARK_TOKEN'),
    ],

    'ses' => [
        'key' => env('AWS_ACCESS_KEY_ID'),
        'secret' => env('AWS_SECRET_ACCESS_KEY'),
        'region' => env('AWS_DEFAULT_REGION', 'us-east-1'),
    ],

    'resend' => [
        'key' => env('RESEND_KEY'),
    ],

    'slack' => [
        'notifications' => [
            'bot_user_oauth_token' => env('SLACK_BOT_USER_OAUTH_TOKEN'),
            'channel' => env('SLACK_BOT_USER_DEFAULT_CHANNEL'),
        ],
    ],

    'ai_engine' => [
        'url' => env('AI_ENGINE_URL', 'http://localhost:8000'),
    ],

    'osrm' => [
        'url' => env('OSRM_BASE_URL', 'http://localhost:5001'),
    ],

    // Self-hosted Nominatim (docker/docker-compose.yml `nominatim` service)
    // — replaces the public nominatim.openstreetmap.org instance in
    // RoutingService.php, same reasoning as osrm above.
    'nominatim' => [
        'url' => env('NOMINATIM_BASE_URL', 'http://localhost:8088'),
    ],

    'chapa' => [
        'secret_key' => env('CHAPA_SECRET_KEY'),
    ],

    // 'mock' (default) simulates payment/payout instantly via an internal
    // checkout page — lets the whole escrow flow be verified with zero
    // external dependency. Flip to 'chapa' once a real merchant account
    // (checkout key + Transfers approval) exists — see MockChapaService /
    // ChapaService and the binding in AppServiceProvider::register().
    'payment_gateway' => [
        'driver' => env('PAYMENT_GATEWAY_DRIVER', 'mock'),
    ],

    // Same swap-later shape as payment_gateway above. 'mock' (default) logs
    // the SMS instead of sending it — see MockSmsService / SmsService and
    // the binding in AppServiceProvider::register(). No real provider is
    // wired up yet; flip this once one is chosen.
    'sms_gateway' => [
        'driver' => env('SMS_GATEWAY_DRIVER', 'mock'),
    ],

    'commission_rate' => (float) env('COMMISSION_RATE', 0.02),
];
