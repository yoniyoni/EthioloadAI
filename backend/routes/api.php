<?php

use Illuminate\Support\Facades\Route;
use App\Http\Controllers\Api\AuthController;
use App\Http\Controllers\Api\VehicleController;
use App\Http\Controllers\Api\CargoRequestController;
use App\Http\Controllers\Api\BookingController;
use App\Http\Controllers\Api\PaymentController;
use App\Http\Controllers\Api\ChapaWebhookController;
use App\Http\Controllers\Api\MockPaymentController;
use App\Http\Controllers\Api\TripController;
use App\Http\Controllers\Api\AiController;
use App\Http\Controllers\Api\UserController;
use App\Http\Middleware\AdminMiddleware;

use App\Http\Controllers\Api\BidController;
use App\Http\Controllers\Api\NotificationController;
use App\Http\Controllers\Api\DocumentController;
use App\Http\Controllers\Api\VehicleDocumentController;
use App\Http\Controllers\Api\FleetController;
use App\Http\Controllers\Api\RatingController;
use App\Http\Controllers\Api\AdminApiController;
use App\Http\Controllers\Api\TripStopController;
use App\Http\Controllers\Api\AdminSettingsController;
use App\Http\Controllers\Api\BackhaulRecommendationController;
use App\Http\Controllers\Api\GeocodingController;
use App\Http\Controllers\Api\RoutingController;

Route::post('/register', [AuthController::class, 'register'])->middleware('throttle:30,1');
Route::middleware('throttle:login')->group(function () {
    Route::post('/login',       [AuthController::class,      'login']);
    // Admin panel login
    Route::post('/auth/login',  [AdminApiController::class,  'login']);
});

// Password reset — public, unauthenticated by nature. Own rate limiter
// (see AppServiceProvider::boot); the code's own hash/expiry/attempts are
// the real defense, this just caps request volume.
Route::middleware('throttle:password-reset')->group(function () {
    Route::post('/forgot-password', [AuthController::class, 'forgotPassword']);
    Route::post('/reset-password',  [AuthController::class, 'resetPassword']);
});

// Chapa payment gateway — public, unauthenticated (Chapa's own servers hit
// these). Never trusted as proof of payment on their own; both re-verify
// with Chapa before doing anything. See ChapaWebhookController.
Route::middleware('throttle:60,1')->group(function () {
    Route::post('/webhooks/chapa', [ChapaWebhookController::class, 'webhook'])->name('chapa.webhook');
    Route::get('/payments/chapa/return', [ChapaWebhookController::class, 'returnPage'])->name('chapa.return');

    // Mock payment gateway — only reachable in practice while
    // PAYMENT_GATEWAY_DRIVER=mock (see AppServiceProvider::register()).
    // Public for the same reason the real checkout page is: it's opened in
    // a WebView, not an authenticated API call.
    Route::get('/payments/mock/checkout', [MockPaymentController::class, 'checkout'])->name('mock.checkout');
    Route::post('/payments/mock/confirm', [MockPaymentController::class, 'confirm'])->name('mock.confirm');
});

Route::middleware('auth:sanctum')->group(function () {
    Route::post('/logout', [AuthController::class, 'logout']);
    Route::get('/me', [AuthController::class, 'me']);

    // Freight endpoints (camelCase shape for React frontend — accessible to all roles)
    Route::get('/freight',      [CargoRequestController::class, 'freightIndex']);
    Route::get('/freight/{id}', [CargoRequestController::class, 'freightShow']);
    Route::post('/freight',     [CargoRequestController::class, 'store']);

    // AI GET aliases — frontend uses useQuery (GET); these proxy the POST AI engine endpoints
    Route::get('/ai/price-prediction',       [AiController::class, 'predictPrice']);
    Route::get('/ai/vehicle-recommendation', [AiController::class, 'recommendTruck']);
    Route::get('/ai/driver-recommendations', [AiController::class, 'recommendTruck']);

    // Profile self-update (all authenticated users can update their own profile)
    Route::patch('/me', [AuthController::class, 'updateProfile']);
    Route::patch('/me/password', [AuthController::class, 'changePassword']);
    Route::patch('/driver/payout-details', [AuthController::class, 'updatePayoutDetails']);

    // Current user's own vehicles
    Route::get('/my-vehicles', [VehicleController::class, 'myVehicles']);

    // Disputes — accessible to all authenticated users (not just admins)
    Route::get('/disputes',                [AdminApiController::class, 'disputes']);
    Route::post('/disputes',               [AdminApiController::class, 'createDispute']);
    Route::patch('/disputes/{id}/resolve', [AdminApiController::class, 'resolveDispute'])->middleware(AdminMiddleware::class);

    Route::post('/geocode/nearest-city', [GeocodingController::class, 'nearestCity']);

    // Routing & geocoding proxy (OSRM + Nominatim — all calls go through Laravel)
    Route::get('/routing/route',   [RoutingController::class, 'route']);
    Route::get('/routing/search',  [RoutingController::class, 'search']);
    Route::get('/routing/reverse', [RoutingController::class, 'reverse']);

    // Nearby trucks map
    Route::get('/nearby-trucks', [VehicleController::class, 'nearbyTrucks']);

    Route::post('/vehicle/register', [VehicleController::class, 'register']);
    Route::patch('/vehicles/{vehicle}/location', [VehicleController::class, 'updateLocation']);
    Route::get('/vehicle/nearby', [VehicleController::class, 'nearby']);
    Route::post('/cargo/create', [CargoRequestController::class, 'store']);
    Route::post('/booking/create', [BookingController::class, 'store']);

    // -------------------------------------------------------------------------
    // Admin API — returns data shaped for the React admin panel (freight-link).
    // Flat routes are declared BEFORE apiResource so they take precedence.
    // -------------------------------------------------------------------------
    Route::middleware(AdminMiddleware::class)->group(function () {
        // /admin/* prefixed endpoints
        Route::prefix('admin')->group(function () {
            Route::get('/stats',               [AdminApiController::class, 'stats']);
            Route::get('/users',               [AdminApiController::class, 'users']);
            Route::get('/payments',            [AdminApiController::class, 'payments']);
            Route::get('/escrow',              [AdminApiController::class, 'escrow']);
            Route::post('/drivers',            [AdminApiController::class, 'createDriver']);
            Route::get('/analytics/revenue',   [AdminApiController::class, 'analyticsRevenue']);
            Route::get('/analytics/routes',    [AdminApiController::class, 'analyticsRoutes']);
            Route::get('/analytics/cargo',     [AdminApiController::class, 'analyticsCargo']);
            Route::get('/bookings/unpaid',                [AdminApiController::class, 'unpaidBookings']);
            Route::post('/bookings/{id}/mark-cash-paid',  [AdminApiController::class, 'markCashPaid']);
            Route::get('/fleet-owners',        [AdminApiController::class, 'fleetOwners']);
            Route::post('/users',              [AdminApiController::class, 'createUser']);
            Route::put('/users/{id}',          [AdminApiController::class, 'updateUser']);
            Route::delete('/users/{id}',       [AdminApiController::class, 'deleteUser']);
        });

        // Flat routes the admin panel calls without /admin prefix
        Route::get('/users',                   [AdminApiController::class, 'users']);
        Route::get('/drivers',                 [AdminApiController::class, 'drivers']);
        Route::patch('/drivers/{id}/status',   [AdminApiController::class, 'updateDriverStatus']);
        Route::get('/trips',                   [AdminApiController::class, 'trips']);
    });

    // Standard resource routes (index excluded where AdminApiController already handles it)
    Route::apiResource('users', UserController::class)->except(['create', 'edit', 'index'])->middleware(AdminMiddleware::class);
    Route::apiResource('vehicles', VehicleController::class)->except(['create', 'edit']);
    Route::apiResource('cargo-requests', CargoRequestController::class)->except(['create', 'edit']);
    Route::apiResource('bookings', BookingController::class)->except(['create', 'edit']);
    
    // Payments
    Route::post('/payments', [PaymentController::class, 'store']);
    Route::post('/payments/chapa/initialize', [PaymentController::class, 'initializeChapa']);
    Route::get('/chapa/banks', [PaymentController::class, 'listBanks']);
    Route::get('/payments/{booking_id}', [PaymentController::class, 'show']);

    // Bids
    Route::post('/cargo-requests/{cargo}/book-direct',  [CargoRequestController::class, 'bookDirect']);
    Route::post('/cargo-requests/{cargo}/accept-price', [CargoRequestController::class, 'acceptPrice']);
    Route::post('/cargo-requests/{cargo}/repost',       [CargoRequestController::class, 'repost']);
    Route::get('/cargo-requests/{cargo}/bids',  [BidController::class, 'index']);
    Route::post('/cargo-requests/{cargo}/bids', [BidController::class, 'store']);
    Route::patch('/bids/{bid}',                [BidController::class, 'update']);
    Route::patch('/bids/{bid}/accept',         [BidController::class, 'accept']);
    Route::patch('/bids/{bid}/reject',         [BidController::class, 'reject']);
    Route::patch('/bids/{bid}/withdraw',       [BidController::class, 'withdraw']);
    Route::patch('/bids/{bid}/counter',        [BidController::class, 'counter']);
    Route::patch('/bids/{bid}/accept-counter', [BidController::class, 'acceptCounter']);
    Route::patch('/bookings/{booking}/cancel', [BookingController::class, 'cancel']);
    Route::patch('/bookings/{booking}/confirm-arrival', [BookingController::class, 'confirmArrival']);
    Route::get('/driver/bids',                 [BidController::class, 'myBids']);
    Route::get('/driver/return-cargo',         [CargoRequestController::class, 'returnCargo']);
    Route::patch('/driver/current-city',       [VehicleController::class, 'updateCurrentCity']);
    Route::post('/driver/location',            [VehicleController::class, 'driverLocation']);
    Route::get('/trips/{trip}/location',       [TripController::class,   'getLocation']);
    Route::get('/nearby-drivers/by-location', [CargoRequestController::class, 'nearbyDriversByLocation']);
    Route::get('/cargo-requests/{cargo}/nearby-drivers', [CargoRequestController::class, 'nearbyDrivers']);

    // Trips (GET /trips is handled by AdminApiController above; other trip actions below)
    Route::post('/trips', [TripController::class, 'store']);
    Route::get('/trips/{id}', [TripController::class, 'show']);
    Route::patch('/trips/{id}/status', [TripController::class, 'updateStatus']);
    Route::patch('/trips/{id}/confirm-pickup', [TripController::class, 'confirmPickup']);
    Route::patch('/trips/{id}/location', [TripController::class, 'updateLocation']);

    // Backhaul recommendations
    Route::get('/trips/{trip}/backhaul-recommendations', [BackhaulRecommendationController::class, 'index']);
    Route::patch('/recommendations/{recommendation}/dismiss', [BackhaulRecommendationController::class, 'dismiss']);

    // Multi-stop trip stops
    Route::prefix('trips/{trip}')->group(function () {
        Route::get('/stops',                          [TripStopController::class, 'index']);
        Route::post('/stops',                         [TripStopController::class, 'store']);
        Route::get('/consolidation-candidates',       [TripStopController::class, 'candidates']);
        Route::patch('/stops/{stop}/arrive',          [TripStopController::class, 'arrive']);
        Route::patch('/stops/{stop}/load',            [TripStopController::class, 'load']);
        Route::patch('/stops/{stop}/complete',        [TripStopController::class, 'complete']);
        Route::delete('/stops/{stop}',                [TripStopController::class, 'destroy']);
    });

    // Driver documents
    Route::get('/driver/documents', [DocumentController::class, 'index']);
    Route::post('/driver/documents', [DocumentController::class, 'upload']);
    Route::get('/driver/documents/{document}/file', [DocumentController::class, 'download']);

    // Admin document review
    Route::get('/admin/driver-documents', [DocumentController::class, 'adminIndex'])->middleware(AdminMiddleware::class);
    Route::patch('/admin/driver-documents/{document}/review', [DocumentController::class, 'review'])->middleware(AdminMiddleware::class);

    // Vehicle documents
    Route::get('/vehicles/{vehicle}/documents', [VehicleDocumentController::class, 'index']);
    Route::post('/vehicles/{vehicle}/documents', [VehicleDocumentController::class, 'upload']);
    Route::get('/vehicle-documents/{document}/file', [VehicleDocumentController::class, 'download']);
    Route::get('/admin/vehicle-documents', [VehicleDocumentController::class, 'adminIndex'])->middleware(AdminMiddleware::class);
    Route::patch('/admin/vehicle-documents/{document}/review', [VehicleDocumentController::class, 'review'])->middleware(AdminMiddleware::class);

    // Admin settings
    Route::get('/admin/settings/pricing', [AdminSettingsController::class, 'pricingShow'])->middleware(AdminMiddleware::class);
    Route::patch('/admin/settings/pricing', [AdminSettingsController::class, 'pricingUpdate'])->middleware(AdminMiddleware::class);
    Route::get('/admin/settings/spatial', [AdminSettingsController::class, 'spatialShow'])->middleware(AdminMiddleware::class);
    Route::patch('/admin/settings/spatial', [AdminSettingsController::class, 'spatialUpdate'])->middleware(AdminMiddleware::class);

    // Ratings
    Route::post('/ratings', [RatingController::class, 'store']);
    Route::get('/ratings/{booking_id}', [RatingController::class, 'show']);
    Route::get('/driver/my-ratings', [RatingController::class, 'myRatings']);

    // Fleet Management
    Route::prefix('fleet')->group(function () {
        Route::get('/dashboard',                      [FleetController::class, 'dashboard']);
        Route::get('/drivers',                        [FleetController::class, 'drivers']);
        Route::get('/vehicles',                       [FleetController::class, 'vehicles']);
        Route::get('/available-cargo',                [FleetController::class, 'availableCargo']);
        Route::get('/cargo/{cargo}/candidates',       [FleetController::class, 'cargoCandidates']);
        Route::post('/drivers/add',                   [FleetController::class, 'addDriver']);
        Route::post('/drivers',                       [FleetController::class, 'createDriver']);
        Route::delete('/drivers/{driverId}',          [FleetController::class, 'removeDriver']);
        Route::post('/vehicles',                      [FleetController::class, 'addVehicle']);
        Route::patch('/vehicles/{vehicleId}/assign',  [FleetController::class, 'assignVehicle']);
        Route::post('/bookings',                      [FleetController::class, 'createBooking']);
        Route::patch('/bookings/{bookingId}/dispatch',[FleetController::class, 'dispatchBooking']);
        Route::get('/drivers/{driverId}',                    [FleetController::class, 'driverDetail']);
        Route::patch('/drivers/{driverId}',                  [FleetController::class, 'updateDriver']);
        Route::delete('/drivers/{driverId}/permanently',     [FleetController::class, 'deleteDriverAccount']);
        Route::get('/drivers/{driverId}/documents',          [DocumentController::class, 'fleetIndex']);
        Route::post('/drivers/{driverId}/documents',         [DocumentController::class, 'fleetUpload']);
        Route::get('/vehicles/{vehicleId}',                  [FleetController::class, 'vehicleDetail']);
        Route::get('/live-map',                              [FleetController::class, 'liveMap']);
    });

    // Notifications
    Route::get('/notifications',              [NotificationController::class, 'index']);
    Route::patch('/notifications/read-all',   [NotificationController::class, 'markAllRead']);
    Route::patch('/notifications/{id}/read',  [NotificationController::class, 'markRead']);

    // AI Engine Proxy Endpoints
    Route::post('/ai/recommend-truck', [AiController::class, 'recommendTruck']);
    Route::post('/ai/backhaul-opportunities', [AiController::class, 'backhaulOpportunities']);
    Route::post('/ai/predict-price', [AiController::class, 'predictPrice']);
    Route::post('/ai/predict-empty-return', [AiController::class, 'predictEmptyReturn']);
    Route::post('/ai/optimize-route', [AiController::class, 'optimizeRoute']);
});
