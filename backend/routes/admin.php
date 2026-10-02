<?php

use Illuminate\Support\Facades\Route;
use App\Http\Controllers\Admin\AdminController;
use App\Http\Middleware\AdminMiddleware;

Route::middleware(['auth', AdminMiddleware::class])->prefix('admin')->group(function () {
    Route::get('/dashboard', [AdminController::class, 'dashboard']);

    // Users
    Route::post('/users', [AdminController::class, 'storeUser']);
    Route::get('/users', [AdminController::class, 'users']);
    Route::get('/users/{id}', [AdminController::class, 'showUser']);
    Route::put('/users/{id}', [AdminController::class, 'updateUser']);
    Route::delete('/users/{id}', [AdminController::class, 'deleteUser']);

    // Vehicles
    Route::post('/vehicles', [AdminController::class, 'storeVehicle']);
    Route::get('/vehicles', [AdminController::class, 'vehicles']);
    Route::get('/vehicles/{id}', [AdminController::class, 'showVehicle']);
    Route::put('/vehicles/{id}', [AdminController::class, 'updateVehicle']);
    Route::delete('/vehicles/{id}', [AdminController::class, 'deleteVehicle']);

    // Cargo Requests
    Route::get('/cargo-requests', [AdminController::class, 'cargoRequests']);
    Route::get('/cargo-requests/{id}', [AdminController::class, 'showCargoRequest']);
    Route::put('/cargo-requests/{id}', [AdminController::class, 'updateCargoRequest']);
    Route::delete('/cargo-requests/{id}', [AdminController::class, 'deleteCargoRequest']);

    // Bookings
    Route::get('/bookings', [AdminController::class, 'bookings']);
    Route::get('/bookings/{id}', [AdminController::class, 'showBooking']);
    Route::put('/bookings/{id}', [AdminController::class, 'updateBooking']);
    Route::delete('/bookings/{id}', [AdminController::class, 'deleteBooking']);

    // Payments
    Route::get('/payments', [AdminController::class, 'payments']);
    Route::get('/payments/{id}', [AdminController::class, 'showPayment']);
    Route::put('/payments/{id}', [AdminController::class, 'updatePayment']);
    Route::delete('/payments/{id}', [AdminController::class, 'deletePayment']);

    // Trips
    Route::get('/trips', [AdminController::class, 'trips']);
    Route::get('/trips/{id}', [AdminController::class, 'showTrip']);
    Route::put('/trips/{id}', [AdminController::class, 'updateTrip']);
    Route::delete('/trips/{id}', [AdminController::class, 'deleteTrip']);

    // Ratings
    Route::get('/ratings', [AdminController::class, 'ratings']);
    Route::get('/ratings/{id}', [AdminController::class, 'showRating']);
    Route::put('/ratings/{id}', [AdminController::class, 'updateRating']);
    Route::delete('/ratings/{id}', [AdminController::class, 'deleteRating']);
});