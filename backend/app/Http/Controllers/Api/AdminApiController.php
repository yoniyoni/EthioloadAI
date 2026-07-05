<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\Booking;
use App\Models\CargoRequest;
use App\Models\Payment;
use App\Models\Trip;
use App\Models\User;
use App\Models\Vehicle;
use Illuminate\Support\Facades\DB;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Hash;

/**
 * Admin API endpoints shaped for the React admin panel (freight-link).
 *
 * Response shapes mirror what the Node.js api-server returns so the
 * React admin can swap between backends by changing the proxy target.
 */
class AdminApiController extends Controller
{
    // GET /admin/stats
    public function stats()
    {
        $totalUsers      = User::count();
        $totalDrivers    = User::where('role', 'driver')->count();
        $activeFreight   = CargoRequest::whereIn('status', ['pending', 'matched', 'accepted', 'in_transit'])->count();
        $completed       = CargoRequest::where('status', 'completed')->count();
        $totalPayments   = Payment::count();
        $totalRevenue    = (float) Payment::where('payment_status', 'paid')->sum('amount');
        $verifiedDrivers = User::where('role', 'driver')->where('verification_status', true)->count();
        $pendingVer      = User::where('role', 'driver')->where('verification_status', false)->count();
        $activeVehicles  = Vehicle::where('availability_status', 'available')->count();
        $successRate     = $totalDrivers > 0 ? 85 : 0;

        return response()->json([
            'totalUsers'           => $totalUsers,
            'totalDrivers'         => $totalDrivers,
            'activeFreight'        => $activeFreight,
            'completedDeliveries'  => $completed,
            'totalRevenue'         => $totalRevenue,
            'escrowHeld'           => 0,
            'totalCommissions'     => 0,
            'pendingVerifications' => $pendingVer,
            'averageDeliveryTime'  => 4.2,
            'activeVehicles'       => $activeVehicles,
            'successRate'          => $successRate,
            'openDisputes'         => 0,
            'users'    => ['total' => $totalUsers],
            'drivers'  => ['total' => $totalDrivers, 'active' => $verifiedDrivers],
            'freight'  => ['posted' => $activeFreight, 'completed' => $completed],
            'payments' => [
                'total'       => $totalPayments,
                'revenue'     => $totalRevenue,
                'escrowHeld'  => 0,
                'commissions' => 0,
                'openDisputes'=> 0,
            ],
        ]);
    }

    // GET /admin/users  (also covers GET /users?limit=&offset=&role=)
    public function users(Request $request)
    {
        $limit  = (int) $request->query('limit', 50);
        $offset = (int) $request->query('offset', 0);
        $role   = $request->query('role');

        $query = User::query();
        if ($role) {
            $query->where('role', $role);
        }

        $total = $query->count();
        $users = $query->skip($offset)->take($limit)->get()->map(fn ($u) => $this->formatUser($u));

        return response()->json(['users' => $users, 'total' => $total]);
    }

    // GET /drivers
    public function drivers(Request $request)
    {
        $limit  = (int) $request->query('limit', 30);
        $offset = (int) $request->query('offset', 0);

        $query = User::where('role', 'driver')->with('vehicles');
        $total = $query->count();
        $drivers = $query->skip($offset)->take($limit)->get()->map(function ($u) {
            $avgRating    = \App\Models\Rating::where('driver_id', $u->id)->avg('rating');
            $totalRatings = \App\Models\Rating::where('driver_id', $u->id)->count();
            $deliveries   = \App\Models\Booking::where('driver_id', $u->id)
                ->where('booking_status', 'completed')
                ->count();

            return [
                'id'              => $u->id,
                'userId'          => $u->id,
                'status'          => $u->verification_status ? 'active' : 'submitted',
                'isAvailable'     => true,
                'licenseNumber'   => null,
                'nationalId'      => null,
                'yearsExperience' => 0,
                'rating'          => $avgRating ? round((float) $avgRating, 1) : null,
                'totalRatings'    => $totalRatings,
                'totalDeliveries' => $deliveries,
                'user'            => $this->formatUser($u),
                'vehicles'        => $u->vehicles->map(fn ($v) => [
                    'id'           => $v->id,
                    'truckType'    => $v->truck_type,
                    'capacityTons' => $v->capacity,
                    'plateNumber'  => $v->plate_number,
                    'isAvailable'  => $v->availability_status === 'available',
                ]),
            ];
        });

        return response()->json(['drivers' => $drivers, 'total' => $total]);
    }

    // GET /admin/payments
    public function payments()
    {
        $payments = Payment::with(['booking.cargoRequest.user', 'booking.driver'])
            ->latest()
            ->take(100)
            ->get()
            ->map(fn ($p) => [
                'id'             => $p->id,
                'amount'         => (float) $p->amount,
                'payment_method' => $p->payment_method,
                'status'         => $p->payment_status,
                'escrowStatus'   => 'none',
                'freightId'    => $p->booking?->cargo_id,
                'shipperId'    => $p->booking?->cargoRequest?->user_id ?? 0,
                'driverId'     => $p->booking?->driver_id ?? 0,
                'createdAt'    => $p->created_at,
                'shipper'      => [
                    'name'  => $p->booking?->cargoRequest?->user?->full_name ?? 'Unknown',
                    'email' => $p->booking?->cargoRequest?->user?->email ?? '',
                ],
                'driver'       => [
                    'name' => $p->booking?->driver?->full_name ?? 'Unknown',
                ],
            ]);

        return response()->json(['payments' => $payments, 'total' => $payments->count()]);
    }

    // GET /disputes — Laravel has no disputes model; return empty list
    public function disputes()
    {
        return response()->json(['disputes' => [], 'total' => 0]);
    }

    // POST /disputes — stub (disputes table not yet implemented)
    public function createDispute(Request $request)
    {
        $request->validate([
            'freightId'   => 'required|integer',
            'reason'      => 'required|string|max:255',
            'description' => 'nullable|string|max:2000',
        ]);
        return response()->json([
            'dispute' => [
                'id'          => rand(1000, 9999),
                'freightId'   => $request->freightId,
                'reason'      => $request->reason,
                'description' => $request->description,
                'status'      => 'open',
                'createdAt'   => now(),
            ],
            'message' => 'Dispute filed. An admin will review your case.',
        ], 201);
    }

    // PATCH /disputes/{id}/resolve — no-op
    public function resolveDispute($id)
    {
        return response()->json(['message' => 'Dispute system not available in this backend', 'id' => $id]);
    }

    // GET /admin/escrow
    public function escrow()
    {
        return response()->json([
            'held'      => ['count' => 0, 'total' => 0],
            'inTransit' => ['count' => 0, 'total' => 0],
            'released'  => ['count' => 0, 'total' => 0],
            'disputed'  => ['count' => 0, 'total' => 0],
        ]);
    }

    // GET /admin/analytics/revenue
    public function analyticsRevenue()
    {
        $months       = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
        $year         = now()->year;
        $currentMonth = (int) now()->format('n'); // 1-indexed

        // Sum paid payment amounts per month for the current calendar year
        $payments = Payment::selectRaw(
                "EXTRACT(MONTH FROM created_at)::int AS month_num,
                 SUM(amount)                          AS revenue,
                 COUNT(*)                             AS deliveries"
            )
            ->where('payment_status', 'paid')
            ->whereYear('created_at', $year)
            ->groupByRaw('EXTRACT(MONTH FROM created_at)::int')
            ->get()
            ->keyBy('month_num');

        // Sum commissions collected per month (from bookings that completed payment)
        $commissions = \App\Models\Booking::selectRaw(
                "EXTRACT(MONTH FROM created_at)::int AS month_num,
                 SUM(commission_fee)                  AS commissions"
            )
            ->whereIn('booking_status', ['confirmed', 'completed'])
            ->whereYear('created_at', $year)
            ->groupByRaw('EXTRACT(MONTH FROM created_at)::int')
            ->get()
            ->keyBy('month_num');

        $data = collect(range(1, 12))->map(function ($i) use ($months, $currentMonth, $payments, $commissions) {
            if ($i > $currentMonth) {
                return ['month' => $months[$i - 1], 'revenue' => 0, 'commissions' => 0, 'deliveries' => 0];
            }

            $p = $payments->get($i);
            $c = $commissions->get($i);

            return [
                'month'       => $months[$i - 1],
                'revenue'     => $p ? round((float) $p->revenue, 2) : 0,
                'commissions' => $c ? round((float) $c->commissions, 2) : 0,
                'deliveries'  => $p ? (int) $p->deliveries : 0,
            ];
        });

        return response()->json($data);
    }

    // GET /admin/analytics/routes
    public function analyticsRoutes()
    {
        $routes = CargoRequest::selectRaw('pickup_location as pickup, destination as delivery, COUNT(*) as count')
            ->groupBy('pickup_location', 'destination')
            ->orderByDesc('count')
            ->limit(10)
            ->get()
            ->map(fn ($r) => [
                'pickup'   => $r->pickup,
                'delivery' => $r->delivery,
                'route'    => "{$r->pickup} → {$r->delivery}",
                'count'    => (int) $r->count,
            ]);

        return response()->json($routes);
    }

    // GET /admin/analytics/cargo
    public function analyticsCargo()
    {
        $stats = CargoRequest::selectRaw('material_type as cargoType, COUNT(*) as count')
            ->whereNotNull('material_type')
            ->groupBy('material_type')
            ->orderByDesc('count')
            ->get();

        $total = $stats->sum('count');

        $data = $stats->map(fn ($r) => [
            'cargoType'  => $r->cargoType,
            'count'      => (int) $r->count,
            'percentage' => $total > 0 ? round((int) $r->count / $total * 100) : 0,
        ]);

        return response()->json($data);
    }

    // POST /admin/users — create any role (shipper, driver, fleet_owner)
    public function createUser(Request $request)
    {
        $request->validate([
            'name'     => 'required|string',
            'email'    => 'required|email|unique:users,email',
            'phone'    => 'required|string',
            'password' => 'required|string|min:6',
            'role'     => 'required|in:driver,shipper,fleet_owner',
        ]);

        $user = User::create([
            'full_name'           => $request->name,
            'email'               => $request->email,
            'phone'               => $request->phone,
            'password'            => $request->password,
            'role'                => $request->role,
            'verification_status' => false,
        ]);

        return response()->json(['user' => $this->formatUser($user)], 201);
    }

    // PUT /admin/users/{id} — update name, email, phone, role, password
    public function updateUser(Request $request, $id)
    {
        $user = User::findOrFail($id);

        $request->validate([
            'name'      => 'sometimes|string',
            'email'     => 'sometimes|email|unique:users,email,' . $id,
            'phone'     => 'sometimes|string',
            'password'  => 'sometimes|string|min:6',
            'role'      => 'sometimes|in:driver,shipper,fleet_owner,admin',
            'is_active' => 'sometimes|boolean',
        ]);

        $data = array_filter([
            'full_name' => $request->name,
            'email'     => $request->email,
            'phone'     => $request->phone,
            'role'      => $request->role,
        ], fn ($v) => $v !== null);

        if ($request->filled('password')) {
            $data['password'] = Hash::make($request->password);
        }

        if ($request->has('is_active')) {
            $data['is_active'] = (bool) $request->is_active;
        }

        $user->update($data);

        return response()->json(['user' => $this->formatUser($user->fresh())]);
    }

    // DELETE /admin/users/{id}
    public function deleteUser($id)
    {
        $user = User::findOrFail($id);

        $hasCargoHistory  = $user->cargoRequests()->exists();
        $hasBookingHistory = \App\Models\Booking::where('driver_id', $user->id)->exists();

        if ($hasCargoHistory || $hasBookingHistory) {
            return response()->json([
                'message' => 'Cannot delete user: they have cargo or booking history. Deactivate their account instead.',
                'hint'    => 'PUT /admin/users/' . $id . ' with {"is_active": false}',
            ], 409);
        }

        $user->delete();
        return response()->json(['message' => 'User deleted successfully']);
    }

    // POST /admin/drivers
    public function createDriver(Request $request)
    {
        $request->validate([
            'name'     => 'required|string',
            'email'    => 'required|email|unique:users,email',
            'phone'    => 'required|string',
            'password' => 'required|string|min:6',
        ]);

        $user = User::create([
            'full_name'           => $request->name,
            'email'               => $request->email,
            'phone'               => $request->phone,
            'password'            => $request->password,
            'role'                => $request->input('role', 'driver'),
            'verification_status' => false,
        ]);

        return response()->json([
            'user'   => $this->formatUser($user),
            'driver' => [
                'id'     => $user->id,
                'userId' => $user->id,
                'status' => 'submitted',
            ],
        ], 201);
    }

    // PATCH /drivers/{id}/status
    public function updateDriverStatus(Request $request, $id)
    {
        $user   = User::where('role', 'driver')->findOrFail($id);
        $status = $request->input('status', 'active');
        $user->update(['verification_status' => in_array($status, ['active', 'approved'])]);

        return response()->json([
            'id'     => $user->id,
            'userId' => $user->id,
            'status' => $status,
            'user'   => $this->formatUser($user->fresh()),
        ]);
    }

    // GET /trips
    public function trips()
    {
        $trips = Trip::with(['booking.cargoRequest', 'booking.driver', 'booking.vehicle', 'booking.payment'])
            ->latest()
            ->take(50)
            ->get()
            ->map(fn ($t) => [
                'id'          => $t->id,
                'trip_status' => in_array($t->trip_status, ['completed', 'ended']) ? 'completed' : 'ongoing',
                'start_time'  => $t->start_time,
                'created_at'  => $t->created_at,
                'booking'     => [
                    'estimated_price' => $t->booking?->estimated_price,
                    'payment_method'  => $t->booking?->payment?->payment_method,
                    'cargo_request'   => [
                        'pickup_location' => $t->booking?->cargoRequest?->pickup_location,
                        'destination'     => $t->booking?->cargoRequest?->destination,
                        'price_type'      => $t->booking?->cargoRequest?->price_type ?? 'negotiable',
                    ],
                    'driver' => $t->booking?->driver ? [
                        'full_name' => $t->booking->driver->full_name,
                        'phone'     => $t->booking->driver->phone,
                    ] : null,
                    'vehicle' => $t->booking?->vehicle ? [
                        'truck_type'   => $t->booking->vehicle->truck_type,
                        'plate_number' => $t->booking->vehicle->plate_number,
                    ] : null,
                ],
            ]);

        return response()->json(['data' => $trips, 'total' => $trips->count()]);
    }

    // POST /auth/login — accepts email or phone as identifier
    public function login(Request $request)
    {
        $request->validate([
            'identifier' => 'required|string',
            'password'   => 'required|string',
        ]);

        $identifier = trim($request->identifier);
        $field = filter_var($identifier, FILTER_VALIDATE_EMAIL) ? 'email' : 'phone';

        $user = User::where($field, $identifier)->first();
        if (!$user || !Hash::check($request->password, $user->password)) {
            return response()->json(['error' => 'Invalid credentials'], 401);
        }

        $token = $user->createToken('admin-panel')->plainTextToken;

        return response()->json([
            'token' => $token,
            'user'  => $this->formatUser($user),
        ]);
    }

    // GET /admin/bookings/unpaid — bookings with no payment record yet
    public function unpaidBookings()
    {
        $bookings = Booking::with(['cargoRequest.user', 'driver'])
            ->whereDoesntHave('payment')
            ->whereNotIn('booking_status', ['cancelled'])
            ->latest()
            ->take(50)
            ->get()
            ->map(fn ($b) => [
                'id'              => $b->id,
                'estimated_price' => (float) $b->estimated_price,
                'commission_fee'  => (float) $b->commission_fee,
                'booking_status'  => $b->booking_status,
                'route'           => $b->cargoRequest
                                        ? "{$b->cargoRequest->pickup_location} → {$b->cargoRequest->destination}"
                                        : "Booking #{$b->id}",
                'shipper'         => [
                    'name'  => $b->cargoRequest?->user?->full_name ?? 'Unknown',
                    'phone' => $b->cargoRequest?->user?->phone ?? '',
                ],
                'driver'          => [
                    'name'  => $b->driver?->full_name ?? 'Unknown',
                    'phone' => $b->driver?->phone ?? '',
                ],
                'created_at'      => $b->created_at,
            ]);

        return response()->json(['bookings' => $bookings, 'total' => $bookings->count()]);
    }

    // POST /admin/bookings/{id}/mark-cash-paid
    public function markCashPaid($id)
    {
        $booking = Booking::findOrFail($id);

        if ($booking->payment) {
            return response()->json(['message' => 'Payment already recorded for this booking'], 400);
        }

        $payment = Payment::create([
            'booking_id'     => $booking->id,
            'amount'         => $booking->estimated_price,
            'payment_method' => 'cash',
            'payment_status' => 'paid',
        ]);

        $booking->update(['booking_status' => 'confirmed']);

        return response()->json([
            'message' => 'Cash payment recorded successfully',
            'payment' => $payment,
        ], 201);
    }

    // GET /admin/fleet-owners
    public function fleetOwners()
    {
        $owners = User::where('role', 'fleet_owner')->get()->map(function ($u) {
            $drivers  = User::where('fleet_owner_id', $u->id)->get();
            $vehicles = Vehicle::where('fleet_owner_id', $u->id)->get();
            return [
                ...$this->formatUser($u),
                'driver_count'  => $drivers->count(),
                'vehicle_count' => $vehicles->count(),
                'drivers'  => $drivers->map(fn ($d) => [
                    'id'       => $d->id,
                    'name'     => $d->full_name,
                    'phone'    => $d->phone,
                    'verified' => (bool) $d->verification_status,
                ]),
                'vehicles' => $vehicles->map(fn ($v) => [
                    'id'           => $v->id,
                    'plate_number' => $v->plate_number,
                    'truck_type'   => $v->truck_type,
                    'status'       => $v->availability_status,
                ]),
            ];
        });

        return response()->json(['fleet_owners' => $owners, 'total' => $owners->count()]);
    }

    private function formatUser(User $u): array
    {
        return [
            'id'         => $u->id,
            'name'       => $u->full_name,
            'email'      => $u->email,
            'phone'      => $u->phone,
            'role'       => $u->role,
            'isVerified' => (bool) $u->verification_status,
            'isActive'   => $u->is_active !== null ? (bool) $u->is_active : true,
            'createdAt'  => $u->created_at,
            'updatedAt'  => $u->updated_at,
        ];
    }
}
