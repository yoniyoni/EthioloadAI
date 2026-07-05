<?php

return [

    /*
    |--------------------------------------------------------------------------
    | Cross-Origin Resource Sharing (CORS) Configuration
    |--------------------------------------------------------------------------
    |
    | Flutter web (Chrome) runs on a random localhost port and calls
    | http://backend.test/api — so we must allow all localhost origins.
    |
    */

    'paths' => ['api/*', 'sanctum/csrf-cookie'],

    'allowed_methods' => ['*'],

    'allowed_origins' => [
        'http://backend.test',
        'http://localhost',
        'http://127.0.0.1',
    ],

    // Covers any port Flutter web dev server uses (e.g. localhost:55036)
    'allowed_origins_patterns' => [
        '#^http://localhost(:\d+)?$#',
        '#^http://127\.0\.0\.1(:\d+)?$#',
    ],

    'allowed_headers' => ['*'],

    'exposed_headers' => [],

    'max_age' => 0,

    'supports_credentials' => false,

];
