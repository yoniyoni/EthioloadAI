<?php

// Parse comma-separated CORS_ALLOWED_ORIGINS from .env into an array.
// Example .env value:  https://ethioload.ai,https://app.ethioload.ai
$envOrigins = array_filter(
    array_map('trim', explode(',', env('CORS_ALLOWED_ORIGINS', '')))
);

// In development we also allow any localhost port (Flutter web dev server).
$isLocal = env('APP_ENV') === 'local';
$devOrigins = $isLocal ? [
    'http://backend.test',
    'http://localhost',
    'http://127.0.0.1',
] : [];

return [

    /*
    |--------------------------------------------------------------------------
    | Cross-Origin Resource Sharing (CORS) Configuration
    |--------------------------------------------------------------------------
    |
    | Production: set CORS_ALLOWED_ORIGINS in .env (comma-separated).
    |   e.g.  CORS_ALLOWED_ORIGINS=https://ethioload.ai,https://app.ethioload.ai
    |
    | Development: localhost / 127.0.0.1 origins are added automatically when
    |   APP_ENV=local so Flutter web and the React admin panel can call the API.
    |
    */

    'paths' => ['api/*', 'sanctum/csrf-cookie'],

    'allowed_methods' => ['*'],

    'allowed_origins' => array_values(array_unique([...$envOrigins, ...$devOrigins])),

    // Covers any port the Flutter / Vite dev server picks (e.g. localhost:55036).
    'allowed_origins_patterns' => $isLocal ? [
        '#^http://localhost(:\d+)?$#',
        '#^http://127\.0\.0\.1(:\d+)?$#',
    ] : [],

    'allowed_headers' => ['*'],

    'exposed_headers' => [],

    'max_age' => 86400,

    'supports_credentials' => false,

];
