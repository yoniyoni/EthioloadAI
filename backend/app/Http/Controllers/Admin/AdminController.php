<?php

namespace App\Http\Controllers\Admin;

use App\Http\Controllers\Controller;
use Illuminate\Http\Request;

class AdminController extends Controller
{
    public function dashboard()
    {
        $trends = [
            'labels' => ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun'],
            'data' => [120, 150, 180, 220, 300, \App\Models\Booking::count() + 300]
        ];
        
        $successRates = [
            'labels' => ['AI Matched', 'Manual'],
            'data' => [\App\Models\Booking::count() > 0 ? 85 : 0, \App\Models\Booking::count() > 0 ? 15 : 100]
        ];

        return response()->json([
            'counts' => [
                'users' => \App\Models\User::count(),
                'vehicles' => \App\Models\Vehicle::count(),
                'cargo_requests' => \App\Models\CargoRequest::count(),
                'bookings' => \App\Models\Booking::count(),
                'payments' => \App\Models\Payment::count(),
                'trips' => \App\Models\Trip::count(),
                'ratings' => \App\Models\Rating::count(),
            ],
            'recent' => [
                'users' => \App\Models\User::latest()->take(5)->get(),
                'vehicles' => \App\Models\Vehicle::latest()->take(5)->get(),
                'cargo_requests' => \App\Models\CargoRequest::latest()->take(5)->get(),
                'bookings' => \App\Models\Booking::latest()->take(5)->get(),
                'payments' => \App\Models\Payment::latest()->take(5)->get(),
                'trips' => \App\Models\Trip::latest()->take(5)->get(),
                'ratings' => \App\Models\Rating::latest()->take(5)->get(),
            ],
            'trends' => $trends,
            'success_rates' => $successRates,
        ]);
    }

    // --- USERS CRUD ---
    public function storeUser(Request $request)
    {
        $validated = $request->validate([
            'full_name' => 'required|string',
            'phone' => 'required|string',
            'email' => 'required|email|unique:users,email',
            'role' => 'required|in:shipper,driver,admin',
        ]);
        $validated['password'] = bcrypt($validated['phone']);
        $validated['name'] = $validated['full_name'];
        $user = \App\Models\User::create($validated);
        return response()->json(['user' => $user], 201);
    }

    public function users()
    {
        return response()->json(['users' => \App\Models\User::all()]);
    }
    public function showUser($id)
    {
        $user = \App\Models\User::findOrFail($id);
        return response()->json(['user' => $user]);
    }
    public function updateUser(Request $request, $id)
    {
        $user = \App\Models\User::findOrFail($id);
        $user->update($request->all());
        return response()->json(['user' => $user]);
    }
    public function deleteUser($id)
    {
        $user = \App\Models\User::findOrFail($id);
        $user->delete();
        return response()->json(['message' => 'User deleted']);
    }

    // --- VEHICLES CRUD ---
    public function storeVehicle(Request $request)
    {
        $validated = $request->validate([
            'user_id' => 'required|exists:users,id',
            'plate_number' => 'required|string|unique:vehicles,plate_number',
            'truck_type' => 'required|string',
            'capacity' => 'required|numeric',
            'availability_status' => 'required|in:available,on_trip,maintenance',
            'current_city' => 'required|string'
        ]);
        $vehicle = \App\Models\Vehicle::create($validated);
        return response()->json(['vehicle' => $vehicle], 201);
    }

    public function vehicles()
    {
        return response()->json(['vehicles' => \App\Models\Vehicle::all()]);
    }
    public function showVehicle($id)
    {
        $vehicle = \App\Models\Vehicle::findOrFail($id);
        return response()->json(['vehicle' => $vehicle]);
    }
    public function updateVehicle(Request $request, $id)
    {
        $vehicle = \App\Models\Vehicle::findOrFail($id);
        $vehicle->update($request->all());
        return response()->json(['vehicle' => $vehicle]);
    }
    public function deleteVehicle($id)
    {
        $vehicle = \App\Models\Vehicle::findOrFail($id);
        $vehicle->delete();
        return response()->json(['message' => 'Vehicle deleted']);
    }

    // --- CARGO REQUESTS CRUD ---
    public function cargoRequests()
    {
        return response()->json(['cargo_requests' => \App\Models\CargoRequest::all()]);
    }
    public function showCargoRequest($id)
    {
        $cargo = \App\Models\CargoRequest::findOrFail($id);
        return response()->json(['cargo_request' => $cargo]);
    }
    public function updateCargoRequest(Request $request, $id)
    {
        $cargo = \App\Models\CargoRequest::findOrFail($id);
        $cargo->update($request->all());
        return response()->json(['cargo_request' => $cargo]);
    }
    public function deleteCargoRequest($id)
    {
        $cargo = \App\Models\CargoRequest::findOrFail($id);
        $cargo->delete();
        return response()->json(['message' => 'Cargo request deleted']);
    }

    // --- BOOKINGS CRUD ---
    public function bookings()
    {
        return response()->json(['bookings' => \App\Models\Booking::all()]);
    }
    public function showBooking($id)
    {
        $booking = \App\Models\Booking::findOrFail($id);
        return response()->json(['booking' => $booking]);
    }
    public function updateBooking(Request $request, $id)
    {
        $booking = \App\Models\Booking::findOrFail($id);
        $booking->update($request->all());
        return response()->json(['booking' => $booking]);
    }
    public function deleteBooking($id)
    {
        $booking = \App\Models\Booking::findOrFail($id);
        $booking->delete();
        return response()->json(['message' => 'Booking deleted']);
    }

    // --- PAYMENTS CRUD ---
    public function payments()
    {
        return response()->json(['payments' => \App\Models\Payment::all()]);
    }
    public function showPayment($id)
    {
        $payment = \App\Models\Payment::findOrFail($id);
        return response()->json(['payment' => $payment]);
    }
    public function updatePayment(Request $request, $id)
    {
        $payment = \App\Models\Payment::findOrFail($id);
        $payment->update($request->all());
        return response()->json(['payment' => $payment]);
    }
    public function deletePayment($id)
    {
        $payment = \App\Models\Payment::findOrFail($id);
        $payment->delete();
        return response()->json(['message' => 'Payment deleted']);
    }

    // --- TRIPS CRUD ---
    public function trips()
    {
        return response()->json(['trips' => \App\Models\Trip::all()]);
    }
    public function showTrip($id)
    {
        $trip = \App\Models\Trip::findOrFail($id);
        return response()->json(['trip' => $trip]);
    }
    public function updateTrip(Request $request, $id)
    {
        $trip = \App\Models\Trip::findOrFail($id);
        $trip->update($request->all());
        return response()->json(['trip' => $trip]);
    }
    public function deleteTrip($id)
    {
        $trip = \App\Models\Trip::findOrFail($id);
        $trip->delete();
        return response()->json(['message' => 'Trip deleted']);
    }

    // --- RATINGS CRUD ---
    public function ratings()
    {
        return response()->json(['ratings' => \App\Models\Rating::all()]);
    }
    public function showRating($id)
    {
        $rating = \App\Models\Rating::findOrFail($id);
        return response()->json(['rating' => $rating]);
    }
    public function updateRating(Request $request, $id)
    {
        $rating = \App\Models\Rating::findOrFail($id);
        $rating->update($request->all());
        return response()->json(['rating' => $rating]);
    }
    public function deleteRating($id)
    {
        $rating = \App\Models\Rating::findOrFail($id);
        $rating->delete();
        return response()->json(['message' => 'Rating deleted']);
    }
}
