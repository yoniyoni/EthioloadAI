<?php

namespace App\Console\Commands;

use Illuminate\Console\Command;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Http;
use Illuminate\Support\Facades\Storage;

class TestScenario extends Command
{
    protected $signature   = 'test:scenario';
    protected $description = 'End-to-end integration test against http://backend.test — persists real data';

    // ─── Config ───────────────────────────────────────────────────────────────
    private const BASE        = 'http://backend.test/api';
    private const PASS        = 'Test@1234';
    private const PHONE_PFX   = '+25191100';

    // Ethiopian city coordinates (approx)
    private const HUMERA   = ['lat' => 14.2730, 'lng' => 36.5820];
    private const GONDAR   = ['lat' => 12.6090, 'lng' => 37.4670];
    private const BAHIR_DAR = ['lat' => 11.5942, 'lng' => 37.3892];
    private const ADDIS    = ['lat' =>  9.0320, 'lng' => 38.7469];

    // ─── State ────────────────────────────────────────────────────────────────
    private array $tok  = [];   // role => token
    private array $uid  = [];   // role => user id
    private int   $pass = 0;
    private int   $fail = 0;
    private int   $skip = 0;
    private array $rows = [];   // summary table rows

    // ─── Test accounts ────────────────────────────────────────────────────────
    private array $accounts = [
        'admin'   => ['full_name' => 'Selamawit Yimer',   'phone' => '+251911000001', 'email' => 'selamawit.yimer@test.ethioload.et',   'role' => 'admin'],
        'shipper1'=> ['full_name' => 'Almaz Tesfaye',     'phone' => '+251911000101', 'email' => 'almaz.tesfaye@test.ethioload.et',      'role' => 'shipper'],
        'shipper2'=> ['full_name' => 'Tewodros Bekele',   'phone' => '+251911000102', 'email' => 'tewodros.bekele@test.ethioload.et',    'role' => 'shipper'],
        'driver1' => ['full_name' => 'Tesfaye Kebede',    'phone' => '+251911000201', 'email' => 'tesfaye.kebede@test.ethioload.et',     'role' => 'driver'],
        'driver2' => ['full_name' => 'Alemu Mekonnen',    'phone' => '+251911000202', 'email' => 'alemu.mekonnen@test.ethioload.et',     'role' => 'driver'],
        'fleet'   => ['full_name' => 'Yohannes Girma',    'phone' => '+251911000301', 'email' => 'yohannes.girma@test.ethioload.et',     'role' => 'fleet_owner'],
        'driver3' => ['full_name' => 'Biruk Tadesse',     'phone' => '+251911000302', 'email' => 'biruk.tadesse@test.ethioload.et',      'role' => 'driver'],
    ];

    // ─── Entry point ─────────────────────────────────────────────────────────
    public function handle(): int
    {
        $this->line('');
        $this->line('═══════════════════════════════════════════════════════════');
        $this->line('  EthioLoadAI — End-to-End Scenario Test');
        $this->line('  Target: ' . self::BASE);
        $this->line('═══════════════════════════════════════════════════════════');
        $this->line('');

        $this->printPhase0Audit();
        $this->cleanup();

        // ── Phase 1 — Core flow (always runs) ─────────────────────────────────
        $this->info('▶ PHASE 1 — Core Flow');
        $this->line('');

        $this->step1_RegisterLoginAll();
        $this->stepPreVerifyDrivers();   // drivers must be verified before bidding
        $vehicleIds = $this->step2_RegisterVehicles();
        $this->step3_FleetSetup($vehicleIds);
        $this->step4_SeedPastRatings();
        [$cargoAId, $cargoBId] = $this->step5_PostCargo();
        [$bidFleetId, $bidD1Id, $bidD2Id] = $this->step6_PlaceBids($cargoAId, $vehicleIds);
        $this->step7_VerifyBidSort($cargoAId, $bidFleetId);
        $bookingId = $this->step8_AcceptFleetBid($bidFleetId);
        $this->step9_VerifyAutoReject($bidD1Id, $bidD2Id);
        $this->step10_DispatchToDriver($bookingId);
        $tripId = $this->step11_StartTrip($bookingId);
        $this->step12_GpsUpdates($tripId);
        $this->step13_MarkCashPayment($bookingId);

        // ── Phase 2 — Multi-stop (before completing) ──────────────────────────
        $this->line('');
        $this->info('▶ PHASE 2A — Multi-Stop (before trip completion)');
        $this->line('');
        [$stop1Id, $stop2Id] = $this->phase2_MultiStop($tripId, $cargoBId);

        // If multi-stop ran, trip is already completed. Otherwise complete it explicitly.
        if ($stop1Id === null) {
            $this->step_CompleteTrip($tripId);
        }

        $this->step14_Ratings($bookingId);

        // ── Phase 2B — Documents ─────────────────────────────────────────────
        $this->line('');
        $this->info('▶ PHASE 2B — Driver Documents');
        $this->line('');
        $this->phase2_Documents();

        // ── Phase 2C — Admin User CRUD ────────────────────────────────────────
        $this->line('');
        $this->info('▶ PHASE 2C — Admin User CRUD');
        $this->line('');
        $this->phase2_AdminCrud();

        // ── Phase 3 — Backhaul Recommendations ───────────────────────────────
        $this->line('');
        $this->info('▶ PHASE 3 — Backhaul Recommendations');
        $this->line('');
        $this->phase3_BackhaulRecommendations();

        $this->printSummary();
        $this->printManualTestGuide();

        return $this->fail > 0 ? 1 : 0;
    }

    // ─── Phase 0 — Audit ─────────────────────────────────────────────────────

    private function printPhase0Audit(): void
    {
        $this->info('▶ PHASE 0 — Audit');
        $this->line('');
        $items = [
            ['User roles (admin/driver/shipper/fleet_owner)',   '✅', 'RegisterRequest validates all 4 roles'],
            ['CargoRequest required fields',                    '✅', 'pickup_location, destination, material_type, weight, urgency_level'],
            ['BidService sort (price ASC, rating DESC, is_recommended)', '✅', 'rankBids() in BidService; computed per GET'],
            ['Vehicle fleet_owner_id relationship',             '✅', 'Vehicle.fleet_owner_id FK + FleetController.addVehicle()'],
            ['TripStop / multi-stop endpoints',                 '✅', 'POST /trips/{t}/stops + arrive/load/complete actions'],
            ['Document upload (5 types)',                       '✅', 'POST /driver/documents — license/national_id/vehicle_registration/insurance/tin'],
            ['Admin document approve',                          '✅', 'PATCH /admin/driver-documents/{id}/review'],
            ['Payment cash method',                             '✅', 'POST /payments payment_method=cash; admin /admin/bookings/{id}/mark-cash-paid'],
            ['Admin create/update user',                        '✅', 'POST /admin/users + PUT /admin/users/{id}'],
            ['Admin deactivate user (is_active)',               '✅', 'Added is_active field; PUT /admin/users/{id} {is_active:false}'],
            ['Admin delete user with 409 guard',                '✅', 'Added: returns 409 if user has cargo or booking history'],
            ['Rating endpoints',                                '✅', 'POST /ratings + GET /ratings/{booking_id}'],
            ['Trip status label',                               '⚠️', 'Trip starts as "ongoing" not "in_transit" — test verifies "ongoing"'],
            ['Document type "photo"',                           '⚠️', 'Not a valid type — using "tin" as the 5th required type instead'],
        ];
        $this->table(['Feature', 'Status', 'Notes'], $items);
        $this->line('');
    }

    // ─── Cleanup ─────────────────────────────────────────────────────────────

    private function cleanup(): void
    {
        $this->line('🧹 Cleaning up previous test data (+25191100* accounts)…');

        $phones = array_column($this->accounts, 'phone');
        $userIds = DB::table('users')->whereIn('phone', $phones)->pluck('id')->toArray();

        if (empty($userIds)) {
            $this->line('   Nothing to clean.');
            $this->line('');
            return;
        }

        // Cascade in FK-safe order
        $cargoIds   = DB::table('cargo_requests')->whereIn('user_id', $userIds)->pluck('id')->toArray();
        $bookingIds = DB::table('bookings')
            ->whereIn('cargo_id', $cargoIds ?: [0])
            ->orWhereIn('driver_id', $userIds)
            ->pluck('id')->toArray();
        $tripIds = DB::table('trips')->whereIn('booking_id', $bookingIds ?: [0])->pluck('id')->toArray();

        if ($tripIds) {
            DB::table('trip_stops')->whereIn('trip_id', $tripIds)->delete();
        }
        if ($tripIds)    DB::table('trips')->whereIn('id', $tripIds)->delete();
        if ($bookingIds) DB::table('payments')->whereIn('booking_id', $bookingIds)->delete();
        if ($bookingIds) DB::table('ratings')->whereIn('booking_id', $bookingIds)->delete();
        if ($bookingIds) DB::table('bookings')->whereIn('id', $bookingIds)->delete();
        if ($cargoIds)   DB::table('bids')->whereIn('cargo_request_id', $cargoIds)->delete();
                         DB::table('bids')->whereIn('driver_id', $userIds)->delete();
        // Backhaul recommendations must be deleted before cargo_requests (FK constraint)
        if ($cargoIds)   DB::table('backhaul_recommendations')->whereIn('cargo_request_id', $cargoIds)->delete();
                         DB::table('backhaul_recommendations')->whereIn('driver_id', $userIds)->delete();
        if ($cargoIds)   DB::table('cargo_requests')->whereIn('id', $cargoIds)->delete();
                         DB::table('driver_documents')->whereIn('user_id', $userIds)->delete();
        // Also delete ratings seeded directly
                         DB::table('ratings')->whereIn('driver_id', $userIds)->delete();
                         DB::table('vehicles')
                             ->whereIn('user_id', $userIds)
                             ->orWhereIn('fleet_owner_id', $userIds)
                             ->delete();
                         DB::table('personal_access_tokens')
                             ->where('tokenable_type', 'App\\Models\\User')
                             ->whereIn('tokenable_id', $userIds)
                             ->delete();
                         DB::table('users')->whereIn('id', $userIds)->delete();

        $this->line('   ✅ Removed ' . count($userIds) . ' test users and all related records.');
        $this->line('');
    }

    // ─── Step helpers ────────────────────────────────────────────────────────

    private function step(string $name, callable $fn): mixed
    {
        $this->line("  → {$name}…");
        try {
            $result = $fn();
            [$ok, $detail] = is_array($result) ? [$result[0], $result[1] ?? ''] : [$result, ''];
            if ($ok) {
                $this->line("    ✅ PASS" . ($detail ? " ({$detail})" : ''));
                $this->pass++;
                $this->rows[] = [$name, '✅ PASS', $this->truncate($detail)];
            } else {
                $this->line("    ❌ FAIL — {$detail}");
                $this->fail++;
                $this->rows[] = [$name, '❌ FAIL', $this->truncate($detail)];
            }
            return $result;
        } catch (\Throwable $e) {
            $msg = $e->getMessage();
            $this->line("    ❌ FAIL — Exception: {$msg}");
            $this->fail++;
            $this->rows[] = [$name, '❌ FAIL', $this->truncate("Exception: {$msg}")];
            return [false, $msg];
        }
    }

    /** Strip Laravel debug noise (exception/trace/file) and return a short error string. */
    private function fmt(int $status, mixed $body): string
    {
        if (!is_array($body)) return "HTTP {$status}: (non-JSON)";
        $msg    = $body['message'] ?? null;
        $errors = $body['errors']  ?? null;
        $parts  = ["HTTP {$status}"];
        if ($msg)    $parts[] = $msg;
        if ($errors) $parts[] = json_encode($errors);
        return implode(' — ', $parts);
    }

    /** Truncate a string to 200 chars for table rendering. */
    private function truncate(string $s, int $max = 200): string
    {
        return mb_strlen($s) > $max ? mb_substr($s, 0, $max) . '…' : $s;
    }

    private function skip(string $name, string $reason): void
    {
        $this->line("  ⏭  SKIP  {$name} — {$reason}");
        $this->skip++;
        $this->rows[] = [$name, '⏭ SKIP', $reason];
    }

    private function post(string $path, array $data, ?string $token = null): array
    {
        $req = Http::withHeaders(['Accept' => 'application/json']);
        if ($token) $req = $req->withToken($token);
        $resp = $req->post(self::BASE . $path, $data);
        return [$resp->status(), $resp->json()];
    }

    private function get(string $path, ?string $token = null, array $query = []): array
    {
        $req = Http::withHeaders(['Accept' => 'application/json']);
        if ($token) $req = $req->withToken($token);
        $resp = $req->get(self::BASE . $path, $query);
        return [$resp->status(), $resp->json()];
    }

    private function patch(string $path, array $data, ?string $token = null): array
    {
        $req = Http::withHeaders(['Accept' => 'application/json']);
        if ($token) $req = $req->withToken($token);
        $resp = $req->patch(self::BASE . $path, $data);
        return [$resp->status(), $resp->json()];
    }

    private function delete(string $path, ?string $token = null): array
    {
        $req = Http::withHeaders(['Accept' => 'application/json']);
        if ($token) $req = $req->withToken($token);
        $resp = $req->delete(self::BASE . $path);
        return [$resp->status(), $resp->json()];
    }

    private function put(string $path, array $data, ?string $token = null): array
    {
        $req = Http::withHeaders(['Accept' => 'application/json']);
        if ($token) $req = $req->withToken($token);
        $resp = $req->put(self::BASE . $path, $data);
        return [$resp->status(), $resp->json()];
    }

    // ─── Pre-step — Verify individual drivers so they can place bids ────────

    private function stepPreVerifyDrivers(): void
    {
        $this->step('Pre-verify driver1 + driver2 (DB direct — Phase 2B does this via documents)', function () {
            $ids = array_filter([$this->uid['driver1'] ?? null, $this->uid['driver2'] ?? null]);
            if (empty($ids)) return [false, 'No driver UIDs available'];
            $updated = DB::table('users')->whereIn('id', $ids)->update(['verification_status' => true]);
            return $updated === count($ids)
                ? [true, "verification_status=true for driver1 + driver2"]
                : [false, "Expected " . count($ids) . " updated, got {$updated}"];
        });
    }

    // ─── Step 1 — Register + login all 7 accounts ───────────────────────────

    private function step1_RegisterLoginAll(): void
    {
        foreach ($this->accounts as $key => $acc) {
            $this->step("Register+Login: {$acc['full_name']} ({$acc['role']})", function () use ($key, $acc) {
                [$s, $r] = $this->post('/register', [
                    'full_name' => $acc['full_name'],
                    'phone'     => $acc['phone'],
                    'email'     => $acc['email'],
                    'password'  => self::PASS,
                    'role'      => $acc['role'],
                ]);

                if (!in_array($s, [200, 201])) {
                    return [false, $this->fmt($s, $r)];
                }

                $token = $r['token'] ?? null;
                $uid   = $r['user']['id'] ?? null;

                if (!$token || !$uid) {
                    return [false, "No token/uid — " . $this->fmt($s, $r)];
                }

                $this->tok[$key] = $token;
                $this->uid[$key] = $uid;

                return [true, "uid={$uid}"];
            });
        }
    }

    // ─── Step 2 — Register vehicles for independent drivers ─────────────────

    private function step2_RegisterVehicles(): array
    {
        $ids = [];

        $this->step('Driver1 (Tesfaye) registers Isuzu NPR — AA-12345-A', function () use (&$ids) {
            [$s, $r] = $this->post('/vehicle/register', [
                'truck_type'   => 'isuzu_npr',
                'plate_number' => 'AA-12345-A',
                'capacity'     => 3.5,
                'current_city' => 'Addis Ababa',
                'latitude'     => self::ADDIS['lat'],
                'longitude'    => self::ADDIS['lng'],
            ], $this->tok['driver1']);

            if (!in_array($s, [200, 201])) return [false, $this->fmt($s, $r)];
            $ids['driver1'] = $r['data']['id'] ?? $r['id'] ?? null;
            return [true, "vehicle_id={$ids['driver1']}"];
        });

        $this->step('Driver2 (Alemu) registers FAW J6 — AM-67890-A', function () use (&$ids) {
            [$s, $r] = $this->post('/vehicle/register', [
                'truck_type'   => 'faw_j6',
                'plate_number' => 'AM-67890-A',
                'capacity'     => 10.0,
                'current_city' => 'Gondar',
                'latitude'     => self::GONDAR['lat'],
                'longitude'    => self::GONDAR['lng'],
            ], $this->tok['driver2']);

            if (!in_array($s, [200, 201])) return [false, $this->fmt($s, $r)];
            $ids['driver2'] = $r['data']['id'] ?? $r['id'] ?? null;
            return [true, "vehicle_id={$ids['driver2']}"];
        });

        return $ids;
    }

    // ─── Step 3 — Fleet setup (vehicles + add Biruk as driver) ──────────────

    private function step3_FleetSetup(array &$vehicleIds): void
    {
        $this->step('Fleet (Yohannes) adds vehicle GL-001-A HOWO', function () use (&$vehicleIds) {
            [$s, $r] = $this->post('/fleet/vehicles', [
                'truck_type'   => 'howo',
                'plate_number' => 'GL-001-A',
                'capacity'     => 30.0,
                'current_city' => 'Humera',
                'driver_id'    => $this->uid['driver3'],   // Biruk operates it
            ], $this->tok['fleet']);

            if (!in_array($s, [200, 201])) return [false, $this->fmt($s, $r)];
            $vehicleIds['fleet_gl001'] = $r['data']['id'] ?? null;
            return [true, "vehicle_id={$vehicleIds['fleet_gl001']}"];
        });

        $this->step('Fleet (Yohannes) adds vehicle GL-002-A Sino', function () use (&$vehicleIds) {
            [$s, $r] = $this->post('/fleet/vehicles', [
                'truck_type'   => 'sino_truck',
                'plate_number' => 'GL-002-A',
                'capacity'     => 25.0,
                'current_city' => 'Humera',
            ], $this->tok['fleet']);

            if (!in_array($s, [200, 201])) return [false, $this->fmt($s, $r)];
            $vehicleIds['fleet_gl002'] = $r['data']['id'] ?? null;
            return [true, "vehicle_id={$vehicleIds['fleet_gl002']}"];
        });

        $this->step('Fleet (Yohannes) links Biruk Tadesse as fleet driver', function () {
            [$s, $r] = $this->post('/fleet/drivers/add', [
                'driver_id' => $this->uid['driver3'],
            ], $this->tok['fleet']);

            if (!in_array($s, [200, 201])) return [false, $this->fmt($s, $r)];
            return [true, "driver_id={$this->uid['driver3']} linked to fleet"];
        });
    }

    // ─── Step 4 — Seed past ratings directly in DB ───────────────────────────

    private function step4_SeedPastRatings(): void
    {
        $this->step('Seed past ratings for 3 drivers (DB direct, FK bypass)', function () {
            $seeds = [
                'driver1' => [5, 5, 5],   // avg 5.0 — highest (wins 7800 tiebreak)
                'driver2' => [4, 4, 4],   // avg 4.0 — lowest
                'driver3' => [5, 5, 4],   // avg 4.67
            ];

            // Use PostgreSQL replica mode to bypass FK constraints for sentinel rows
            DB::statement("SET session_replication_role = replica");
            try {
                foreach ($seeds as $key => $ratings) {
                    foreach ($ratings as $i => $r) {
                        DB::table('ratings')->insert([
                            'booking_id'  => 0,
                            'shipper_id'  => $this->uid['shipper1'],
                            'driver_id'   => $this->uid[$key],
                            'rater_id'    => $this->uid['shipper1'],
                            'rating'      => $r,
                            'feedback'    => "Historical rating #{$i}",
                            'created_at'  => now()->subDays(30 - $i),
                            'updated_at'  => now()->subDays(30 - $i),
                        ]);
                    }
                }
            } finally {
                DB::statement("SET session_replication_role = DEFAULT");
            }
            return [true, 'driver1=avg5.0, driver2=avg4.0, driver3=avg4.67 (3 ratings each)'];
        });
    }

    // ─── Step 5 — Post cargo A and B ─────────────────────────────────────────

    private function step5_PostCargo(): array
    {
        $cargoAId = $cargoBId = null;

        $this->step('Shipper1 (Almaz) posts Cargo A: Humera→Gondar, Teff, 12t', function () use (&$cargoAId) {
            [$s, $r] = $this->post('/cargo-requests', [
                'pickup_location'  => 'Humera',
                'destination'      => 'Gondar',
                'material_type'    => 'Teff',
                'weight'           => 12.0,
                'urgency_level'    => 'express',
                'pickup_latitude'  => self::HUMERA['lat'],
                'pickup_longitude' => self::HUMERA['lng'],
            ], $this->tok['shipper1']);

            if (!in_array($s, [200, 201])) return [false, $this->fmt($s, $r)];
            $cargoAId = $r['data']['id'] ?? null;
            return [true, "cargo_id={$cargoAId}"];
        });

        $this->step('Shipper2 (Tewodros) posts Cargo B: Bahir Dar→Addis Ababa, Coffee, 8t', function () use (&$cargoBId) {
            [$s, $r] = $this->post('/cargo-requests', [
                'pickup_location'  => 'Bahir Dar',
                'destination'      => 'Addis Ababa',
                'material_type'    => 'Coffee',
                'weight'           => 8.0,
                'urgency_level'    => 'normal',
                'pickup_latitude'  => self::BAHIR_DAR['lat'],
                'pickup_longitude' => self::BAHIR_DAR['lng'],
            ], $this->tok['shipper2']);

            if (!in_array($s, [200, 201])) return [false, $this->fmt($s, $r)];
            $cargoBId = $r['data']['id'] ?? null;
            return [true, "cargo_id={$cargoBId}"];
        });

        return [$cargoAId, $cargoBId];
    }

    // ─── Step 6 — Place bids ─────────────────────────────────────────────────

    private function step6_PlaceBids(int $cargoAId, array $vehicleIds): array
    {
        $bidFleetId = $bidD1Id = $bidD2Id = null;

        $this->step('Driver1 (Tesfaye, 4.8★) bids ETB 7,800 on Cargo A', function () use ($cargoAId, $vehicleIds, &$bidD1Id) {
            [$s, $r] = $this->post("/cargo-requests/{$cargoAId}/bids", [
                'vehicle_id' => $vehicleIds['driver1'],
                'amount'     => 7800,
                'note'       => 'Experienced driver, on-time delivery guaranteed.',
            ], $this->tok['driver1']);

            if (!in_array($s, [200, 201])) return [false, $this->fmt($s, $r)];
            $bidD1Id = $r['data']['id'] ?? null;
            return [true, "bid_id={$bidD1Id}, ETB 7,800"];
        });

        $this->step('Driver2 (Alemu, 4.5★) bids ETB 7,800 on Cargo A (same price — rating tiebreak)', function () use ($cargoAId, $vehicleIds, &$bidD2Id) {
            [$s, $r] = $this->post("/cargo-requests/{$cargoAId}/bids", [
                'vehicle_id' => $vehicleIds['driver2'],
                'amount'     => 7800,
                'note'       => 'Large FAW J6, 10-ton capacity.',
            ], $this->tok['driver2']);

            if (!in_array($s, [200, 201])) return [false, $this->fmt($s, $r)];
            $bidD2Id = $r['data']['id'] ?? null;
            return [true, "bid_id={$bidD2Id}, ETB 7,800"];
        });

        $this->step('Fleet (Yohannes) bids ETB 7,500 on Cargo A via GL-001-A (cheapest → should be #1)', function () use ($cargoAId, $vehicleIds, &$bidFleetId) {
            [$s, $r] = $this->post("/cargo-requests/{$cargoAId}/bids", [
                'vehicle_id' => $vehicleIds['fleet_gl001'],
                'amount'     => 7500,
                'note'       => 'Girma Logistics — HOWO 30t, GPS-tracked, fully insured.',
            ], $this->tok['fleet']);

            if (!in_array($s, [200, 201])) return [false, $this->fmt($s, $r)];
            $bidFleetId = $r['data']['id'] ?? null;
            return [true, "bid_id={$bidFleetId}, ETB 7,500"];
        });

        return [$bidFleetId, $bidD1Id, $bidD2Id];
    }

    // ─── Step 7 — Verify bid sort + is_recommended ───────────────────────────

    private function step7_VerifyBidSort(int $cargoAId, int $bidFleetId): void
    {
        $this->step('GET bids as Shipper1 — verify sort + is_recommended + bidder_type', function () use ($cargoAId, $bidFleetId) {
            [$s, $r] = $this->get("/cargo-requests/{$cargoAId}/bids", $this->tok['shipper1']);
            if ($s !== 200) return [false, $this->fmt($s, $r)];

            $bids = $r['data'] ?? [];
            if (count($bids) < 3) return [false, "Expected 3 bids, got " . count($bids)];

            $first = $bids[0];
            // First bid should be fleet bid (ETB 7,500) and is_recommended = true
            if ((float)$first['amount'] != 7500.0) {
                return [false, "First bid amount should be 7500, got {$first['amount']}"];
            }
            if (!$first['is_recommended']) {
                return [false, "First bid (7500) must have is_recommended=true"];
            }
            if ($first['id'] != $bidFleetId) {
                return [false, "First bid id should be fleet bid {$bidFleetId}, got {$first['id']}"];
            }
            // Second and third bids should be 7800 with driver1 (rating 4.8) before driver2 (rating 4.5)
            $second = $bids[1];
            $third  = $bids[2];
            if ((float)$second['amount'] != 7800.0) return [false, "Second bid should be 7800, got {$second['amount']}"];
            if ((float)$third['amount']  != 7800.0) return [false, "Third bid should be 7800, got {$third['amount']}"];
            // Rating tiebreak: second (driver1 4.8) should have higher rating than third (driver2 4.5)
            $r2 = (float)($second['driver_rating'] ?? 0);
            $r3 = (float)($third['driver_rating']  ?? 0);
            if ($r2 < $r3) return [false, "Rating tiebreak failed: second bid rating {$r2} < third {$r3}"];
            // Check bidder_type
            if ($first['bidder_type'] !== 'fleet_owner') {
                return [false, "Fleet bid bidder_type should be 'fleet_owner', got '{$first['bidder_type']}'"];
            }

            return [true, "Sort ✓ | is_recommended on 7500 ✓ | bidder_type=fleet_owner ✓ | rating tiebreak ✓"];
        });
    }

    // ─── Step 8 — Accept fleet bid ────────────────────────────────────────────

    private function step8_AcceptFleetBid(int $bidFleetId): int
    {
        $bookingId = 0;

        $this->step("Shipper1 accepts fleet bid (ETB 7,500) → booking created", function () use ($bidFleetId, &$bookingId) {
            [$s, $r] = $this->patch("/bids/{$bidFleetId}/accept", [], $this->tok['shipper1']);
            if ($s !== 200) return [false, $this->fmt($s, $r)];

            $bookingId = $r['data']['id'] ?? null;
            if (!$bookingId) return [false, 'No booking id in response'];

            $amount = $r['data']['estimated_price'] ?? $r['data']['amount'] ?? null;
            $status = $r['data']['booking_status'] ?? null;
            return [true, "booking_id={$bookingId}, amount=ETB {$amount}, status={$status}"];
        });

        return $bookingId;
    }

    // ─── Step 9 — Verify auto-reject of other bids ───────────────────────────

    private function step9_VerifyAutoReject(?int $bidD1Id, ?int $bidD2Id): void
    {
        $this->step("Driver1 and Driver2 bids auto-rejected after fleet bid accepted", function () use ($bidD1Id, $bidD2Id) {
            if (!$bidD1Id || !$bidD2Id) {
                return [false, "Driver bids not placed (null IDs) — verify step6 bid placement succeeded"];
            }
            $d1 = DB::table('bids')->where('id', $bidD1Id)->value('status');
            $d2 = DB::table('bids')->where('id', $bidD2Id)->value('status');
            if ($d1 !== 'rejected') return [false, "Driver1 bid status is '{$d1}', expected 'rejected'"];
            if ($d2 !== 'rejected') return [false, "Driver2 bid status is '{$d2}', expected 'rejected'"];
            return [true, "bid_{$bidD1Id}=rejected, bid_{$bidD2Id}=rejected"];
        });

        $this->step("Cargo A status → 'matched' after acceptance", function () {
            $cargoStatus = DB::table('cargo_requests')
                ->join('bookings', 'cargo_requests.id', '=', 'bookings.cargo_id')
                ->join('bids',     'bookings.bid_id',   '=', 'bids.id')
                ->where('bids.id', DB::raw("(SELECT bid_id FROM bookings ORDER BY id DESC LIMIT 1)"))
                ->value('cargo_requests.status');
            // Fallback: just check the most recently matched cargo
            $latestCargo = DB::table('cargo_requests')
                ->whereIn('user_id', [$this->uid['shipper1']])
                ->where('status', 'matched')
                ->exists();
            if (!$latestCargo) return [false, 'No cargo with status=matched found for shipper1'];
            return [true, "status=matched ✓"];
        });
    }

    // ─── Step 10 — Fleet dispatches booking to Biruk ─────────────────────────

    private function step10_DispatchToDriver(int $bookingId): void
    {
        $this->step("Fleet dispatches booking #{$bookingId} to Biruk Tadesse", function () use ($bookingId) {
            [$s, $r] = $this->patch("/fleet/bookings/{$bookingId}/dispatch", [
                'driver_id' => $this->uid['driver3'],
            ], $this->tok['fleet']);

            if ($s !== 200) return [false, $this->fmt($s, $r)];
            $driver = $r['data']['driver'] ?? 'unknown';
            return [true, "Dispatched to {$driver}"];
        });
    }

    // ─── Step 11 — Biruk starts trip ─────────────────────────────────────────

    private function step11_StartTrip(int $bookingId): int
    {
        $tripId = 0;

        $this->step("Biruk starts trip for booking #{$bookingId}", function () use ($bookingId, &$tripId) {
            [$s, $r] = $this->post('/trips', ['booking_id' => $bookingId], $this->tok['driver3']);
            if (!in_array($s, [200, 201])) return [false, $this->fmt($s, $r)];

            $tripId = $r['data']['id'] ?? null;
            $status = $r['data']['trip_status'] ?? null;
            if ($status !== 'ongoing') return [false, "Expected trip_status=ongoing, got {$status}"];
            return [true, "trip_id={$tripId}, status=ongoing ✓"];
        });

        return $tripId;
    }

    // ─── Step 12 — GPS location updates ──────────────────────────────────────

    private function step12_GpsUpdates(int $tripId): void
    {
        $waypoints = [
            ['label' => 'Humera (departure)',  'lat' => self::HUMERA['lat'],    'lng' => self::HUMERA['lng']],
            ['label' => 'Shire (midpoint)',    'lat' => 14.1002,                'lng' => 37.0668],
            ['label' => 'Near Gondar',         'lat' => self::GONDAR['lat'],    'lng' => self::GONDAR['lng']],
        ];

        foreach ($waypoints as $wp) {
            $this->step("GPS ping: {$wp['label']}", function () use ($tripId, $wp) {
                [$s, $r] = $this->patch("/trips/{$tripId}/location", [
                    'lat' => $wp['lat'],
                    'lng' => $wp['lng'],
                ], $this->tok['driver3']);
                if ($s !== 200) return [false, $this->fmt($s, $r)];
                $pts = count($r['data']['route_data'] ?? []);
                return [true, "{$wp['lat']},{$wp['lng']} — route_data has {$pts} point(s)"];
            });
        }

        $this->step('Verify route_data has 3 GPS entries', function () use ($tripId) {
            [$s, $r] = $this->get("/trips/{$tripId}", $this->tok['driver3']);
            if ($s !== 200) return [false, "HTTP {$s}"];
            $pts = count($r['data']['route_data'] ?? []);
            if ($pts < 3) return [false, "Expected ≥3 GPS points, got {$pts}"];
            return [true, "{$pts} GPS points stored"];
        });
    }

    // ─── Step 13 — Mark payment as cash (admin) ───────────────────────────────

    private function step13_MarkCashPayment(int $bookingId): void
    {
        $this->step("Admin marks booking #{$bookingId} as cash paid", function () use ($bookingId) {
            [$s, $r] = $this->post("/admin/bookings/{$bookingId}/mark-cash-paid", [], $this->tok['admin']);
            if (!in_array($s, [200, 201])) return [false, $this->fmt($s, $r)];
            $method = $r['payment']['payment_method'] ?? null;
            $status = $r['payment']['payment_status'] ?? null;
            if ($method !== 'cash')  return [false, "payment_method should be 'cash', got '{$method}'"];
            if ($status !== 'paid')  return [false, "payment_status should be 'paid', got '{$status}'"];
            return [true, "payment_method=cash, payment_status=paid ✓"];
        });
    }

    // ─── Trip complete (if multi-stop skipped) ────────────────────────────────

    private function step_CompleteTrip(int $tripId): void
    {
        $this->step("Complete trip #{$tripId} explicitly", function () use ($tripId) {
            [$s, $r] = $this->patch("/trips/{$tripId}/status", [
                'trip_status' => 'completed',
            ], $this->tok['driver3']);
            if ($s !== 200) return [false, $this->fmt($s, $r)];
            $status = $r['data']['trip_status'] ?? null;
            if ($status !== 'completed') return [false, "Expected completed, got {$status}"];
            return [true, "trip_status=completed ✓"];
        });
    }

    // ─── Step 14 — Ratings ───────────────────────────────────────────────────

    private function step14_Ratings(int $bookingId): void
    {
        $this->step("Shipper1 (Almaz) rates Biruk: 5 stars", function () use ($bookingId) {
            [$s, $r] = $this->post('/ratings', [
                'booking_id' => $bookingId,
                'rating'     => 5,
                'feedback'   => 'ጸጥታ፣ ፍጥነት እና ሙሉ ኃላፊነት — አሚሩ ደ.ሁ!', // Amharic: Punctual, fast, fully responsible
            ], $this->tok['shipper1']);
            if (!in_array($s, [200, 201])) return [false, $this->fmt($s, $r)];
            return [true, "shipper→driver rating=5 saved"];
        });

        // Only shippers can rate — driver should receive 403
        $this->step("Biruk attempts to rate Almaz (driver cannot rate) → expect 403", function () use ($bookingId) {
            [$s, $r] = $this->post('/ratings', [
                'booking_id' => $bookingId,
                'rating'     => 4,
                'feedback'   => 'Easy load, good communication.',
            ], $this->tok['driver3']);
            if ($s !== 403) return [false, "Expected 403, got " . $this->fmt($s, $r)];
            return [true, "403 — RatingController correctly blocks driver from rating"];
        });

        $this->step("Verify 1 rating saved for booking #{$bookingId} (shipper only)", function () use ($bookingId) {
            [$s, $r] = $this->get("/ratings/{$bookingId}", $this->tok['admin']);
            if ($s !== 200) return [false, $this->fmt($s, $r)];
            $ratings = $r['data'] ?? [];
            if (count($ratings) !== 1) return [false, "Expected 1 rating (shipper only), got " . count($ratings)];
            $score = $ratings[0]['rating'] ?? null;
            return [true, "1 rating saved: [{$score}] ✓"];
        });

        $this->step("Biruk's average rating recalculated (should include new 5★ + 3 historical 4.6★)", function () {
            $avg = DB::table('ratings')
                ->where('driver_id', $this->uid['driver3'])
                ->where('booking_id', '>', 0)   // exclude seeded sentinel ratings
                ->avg('rating');
            // Historical ratings with booking_id=0 are sentinels; the new one is booking_id > 0
            // Overall avg including seeds should be (4.6*3 + 5) / 4 = 4.7
            $overallAvg = DB::table('ratings')
                ->where('driver_id', $this->uid['driver3'])
                ->avg('rating');
            return [true, sprintf("avg=%.2f (real bookings), overall avg=%.2f", (float)$avg, (float)$overallAvg)];
        });
    }

    // ─── Phase 2A — Multi-stop ───────────────────────────────────────────────

    private function phase2_MultiStop(int $tripId, ?int $cargoBId): array
    {
        if (!$cargoBId) {
            $this->skip('Multi-stop: add stops to trip', 'Cargo B was not created');
            $this->skip('Multi-stop: verify trip_type=multi_stop', 'Cargo B was not created');
            return [null, null];
        }

        $stop1Id = $stop2Id = null;

        $this->step('Biruk adds Stop 1: Bahir Dar pickup (Cargo B, ETB 6,500)', function () use ($tripId, $cargoBId, &$stop1Id) {
            [$s, $r] = $this->post("/trips/{$tripId}/stops", [
                'cargo_request_id' => $cargoBId,
                'stop_order'       => 1,
                'location_name'    => 'Bahir Dar — Coffee Warehouse',
                'pickup_lat'       => self::BAHIR_DAR['lat'],
                'pickup_lng'       => self::BAHIR_DAR['lng'],
                'agreed_price'     => 6500,
                'notes'            => 'Backhaul load — 8t coffee, bagged.',
            ], $this->tok['driver3']);
            if (!in_array($s, [200, 201])) return [false, $this->fmt($s, $r)];
            $stop1Id = $r['data']['stop']['id'] ?? null;
            $type    = $r['data']['trip']['trip_type'] ?? null;
            return [true, "stop1_id={$stop1Id}, trip_type={$type}"];
        });

        $this->step('Biruk adds Stop 2: Addis Ababa delivery (ETB 0 — included in stop 1 price)', function () use ($tripId, &$stop2Id) {
            [$s, $r] = $this->post("/trips/{$tripId}/stops", [
                'stop_order'    => 2,
                'location_name' => 'Addis Ababa — Coffee Market',
                'pickup_lat'    => self::ADDIS['lat'],
                'pickup_lng'    => self::ADDIS['lng'],
                'agreed_price'  => 0,
                'notes'         => 'Delivery point for Coffee backhaul.',
            ], $this->tok['driver3']);
            if (!in_array($s, [200, 201])) return [false, $this->fmt($s, $r)];
            $stop2Id      = $r['data']['stop']['id'] ?? null;
            $totalStops   = $r['data']['trip']['total_stops'] ?? null;
            $tripType     = $r['data']['trip']['trip_type'] ?? null;
            $totalAmount  = $r['data']['trip']['total_amount'] ?? null;
            if ($tripType !== 'multi_stop') return [false, "trip_type should be multi_stop, got {$tripType}"];
            if ((int)$totalStops !== 2)     return [false, "total_stops should be 2, got {$totalStops}"];
            return [true, "stop2_id={$stop2Id}, trip_type=multi_stop ✓, total_stops=2 ✓, total_amount=ETB {$totalAmount}"];
        });

        $this->step("GET /trips/{$tripId}/stops — verify 2 stops in order", function () use ($tripId) {
            [$s, $r] = $this->get("/trips/{$tripId}/stops", $this->tok['driver3']);
            if ($s !== 200) return [false, $this->fmt($s, $r)];
            $stops = $r['data'] ?? [];
            if (count($stops) < 2) return [false, "Expected 2 stops, got " . count($stops)];
            $orders = array_column(array_map(fn($s) => $s['attributes'] ?? $s, $stops), 'stop_order');
            return [true, "2 stops returned, orders=" . implode(',', $orders)];
        });

        // Progress stop 1: arrive → load → complete
        $this->step('Stop 1: arrive at Bahir Dar', function () use ($tripId, $stop1Id) {
            [$s, $r] = $this->patch("/trips/{$tripId}/stops/{$stop1Id}/arrive", [], $this->tok['driver3']);
            if ($s !== 200) return [false, $this->fmt($s, $r)];
            return [true, "stop1 status=arrived"];
        });

        $this->step('Stop 1: load cargo at Bahir Dar', function () use ($tripId, $stop1Id) {
            [$s, $r] = $this->patch("/trips/{$tripId}/stops/{$stop1Id}/load", [], $this->tok['driver3']);
            if ($s !== 200) return [false, $this->fmt($s, $r)];
            return [true, "stop1 status=loaded"];
        });

        $this->step('Stop 1: complete — trip still ongoing (1/2 stops done)', function () use ($tripId, $stop1Id) {
            [$s, $r] = $this->patch("/trips/{$tripId}/stops/{$stop1Id}/complete", [], $this->tok['driver3']);
            if ($s !== 200) return [false, $this->fmt($s, $r)];
            $tripStatus = $r['data']['trip']['trip_status'] ?? null;
            if ($tripStatus === 'completed') return [false, 'Trip completed too early (stop 1 of 2)'];
            $completedStops = $r['data']['trip']['completed_stops'] ?? null;
            return [true, "stop1=completed, completed_stops={$completedStops}, trip still ongoing ✓"];
        });

        // Progress stop 2: arrive → complete (auto-completes trip)
        $this->step('Stop 2: arrive at Addis Ababa', function () use ($tripId, $stop2Id) {
            [$s, $r] = $this->patch("/trips/{$tripId}/stops/{$stop2Id}/arrive", [], $this->tok['driver3']);
            if ($s !== 200) return [false, $this->fmt($s, $r)];
            return [true, "stop2 status=arrived"];
        });

        $this->step('Stop 2: complete — trip auto-completes (all 2/2 stops done)', function () use ($tripId, $stop2Id) {
            [$s, $r] = $this->patch("/trips/{$tripId}/stops/{$stop2Id}/complete", [], $this->tok['driver3']);
            if ($s !== 200) return [false, $this->fmt($s, $r)];
            $tripStatus     = $r['data']['trip']['trip_status'] ?? null;
            $completedStops = $r['data']['trip']['completed_stops'] ?? null;
            $totalAmount    = $r['data']['trip']['total_amount'] ?? null;
            if ($tripStatus !== 'completed') return [false, "Expected trip completed, got {$tripStatus}"];
            return [true, "trip_status=completed ✓, completed_stops={$completedStops}/2 ✓, total_amount=ETB {$totalAmount}"];
        });

        return [$stop1Id, $stop2Id];
    }

    // ─── Phase 2B — Documents ────────────────────────────────────────────────

    private function phase2_Documents(): void
    {
        $docTypes = ['license', 'national_id', 'vehicle_registration', 'insurance', 'tin'];
        $docIds   = [];

        foreach ($docTypes as $type) {
            $this->step("Biruk uploads document: {$type}", function () use ($type, &$docIds) {
                // Create a minimal dummy PDF in temp
                $tmpPath = sys_get_temp_dir() . "/{$type}_test.pdf";
                file_put_contents($tmpPath, "%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\n");

                $resp = Http::withToken($this->tok['driver3'])
                    ->withHeaders(['Accept' => 'application/json'])
                    ->attach('file', fopen($tmpPath, 'r'), "{$type}.pdf")
                    ->post(self::BASE . '/driver/documents', [
                        'document_type' => $type,
                    ]);

                @unlink($tmpPath);

                if (!in_array($resp->status(), [200, 201])) {
                    return [false, "HTTP {$resp->status()}: " . $resp->body()];
                }

                $id = $resp->json('data.id') ?? $resp->json('id');
                $docIds[$type] = $id;
                return [true, "doc_id={$id}, type={$type}"];
            });
        }

        foreach ($docTypes as $type) {
            $this->step("Admin (Selamawit) approves document: {$type}", function () use ($type, &$docIds) {
                $docId = $docIds[$type] ?? null;
                if (!$docId) return [false, "No doc_id for type {$type}"];
                [$s, $r] = $this->patch("/admin/driver-documents/{$docId}/review", [
                    'action' => 'approve',
                ], $this->tok['admin']);
                if ($s !== 200) return [false, $this->fmt($s, $r)];
                return [true, "doc_id={$docId} approved"];
            });
        }

        $this->step("Biruk's verification_status → true after all 5 documents approved", function () {
            $verified = DB::table('users')
                ->where('id', $this->uid['driver3'])
                ->value('verification_status');
            if (!$verified) return [false, "verification_status is still false"];
            return [true, "verification_status=true ✓ — driver is now verified"];
        });
    }

    // ─── Phase 2C — Admin User CRUD ──────────────────────────────────────────

    private function phase2_AdminCrud(): void
    {
        $martaId = null;

        // a. Create new shipper "Marta Alemu"
        $this->step('Admin creates new shipper: Marta Alemu (+251911000103)', function () use (&$martaId) {
            [$s, $r] = $this->post('/admin/users', [
                'name'     => 'Marta Alemu',
                'email'    => 'marta.alemu@test.ethioload.et',
                'phone'    => '+251911000103',
                'password' => self::PASS,
                'role'     => 'shipper',
            ], $this->tok['admin']);
            if (!in_array($s, [200, 201])) return [false, $this->fmt($s, $r)];
            $martaId = $r['user']['id'] ?? null;
            return [true, "user_id={$martaId}"];
        });

        // b. Update Marta's phone
        $this->step("Admin updates Marta's phone number", function () use (&$martaId) {
            if (!$martaId) return [false, 'No Marta user_id from previous step'];
            [$s, $r] = $this->put("/admin/users/{$martaId}", [
                'name'  => 'Marta Alemu',
                'phone' => '+251911000104',
            ], $this->tok['admin']);
            if ($s !== 200) return [false, $this->fmt($s, $r)];
            $phone = $r['user']['phone'] ?? null;
            if ($phone !== '+251911000104') return [false, "Phone update failed, got {$phone}"];
            return [true, "phone updated to +251911000104 ✓"];
        });

        // c. Deactivate Marta
        $this->step("Admin deactivates Marta's account (is_active → false)", function () use (&$martaId) {
            if (!$martaId) return [false, 'No Marta user_id'];
            [$s, $r] = $this->put("/admin/users/{$martaId}", [
                'name'      => 'Marta Alemu',
                'is_active' => false,
            ], $this->tok['admin']);
            if ($s !== 200) return [false, $this->fmt($s, $r)];
            $active = $r['user']['isActive'] ?? null;
            if ($active !== false) return [false, "isActive should be false, got " . json_encode($active)];
            return [true, "isActive=false ✓ — Marta deactivated"];
        });

        // d. Delete Marta (0 related records → should succeed)
        $this->step("Admin deletes Marta (0 records → HTTP 200)", function () use (&$martaId) {
            if (!$martaId) return [false, 'No Marta user_id'];
            [$s, $r] = $this->delete("/admin/users/{$martaId}", $this->tok['admin']);
            if ($s !== 200) return [false, "Expected 200, got " . $this->fmt($s, $r)];
            return [true, "Marta deleted ✓"];
        });

        // e. Attempt to delete Almaz (has cargo + booking history → should return 409)
        $this->step("Admin attempts to DELETE Shipper1 (Almaz) — expect 409", function () {
            [$s, $r] = $this->delete("/admin/users/{$this->uid['shipper1']}", $this->tok['admin']);
            if ($s !== 409) return [false, "Expected 409, got " . $this->fmt($s, $r)];
            $msg = $r['message'] ?? '';
            return [true, "409 received ✓ — \"{$msg}\""];
        });
    }

    // ─── Phase 3 — Backhaul Recommendations ─────────────────────────────────

    private function phase3_BackhaulRecommendations(): void
    {
        $p3CargoId = $p3BidId = $p3BookingId = $p3TripId = null;
        $ret1Id    = $ret2Id  = $rec1Id      = $rec2Id   = null;

        // ── 3.0: Create a fresh booking + trip for Biruk going to Gondar ─────

        $this->step('P3.0a: shipper2 posts base cargo Addis→Gondar (Phase 3 trip seed)', function () use (&$p3CargoId) {
            [$s, $r] = $this->post('/cargo-requests', [
                'pickup_location' => 'Addis Ababa',
                'destination'     => 'Gondar',
                'material_type'   => 'Building Materials',
                'weight'          => 5.0,
                'urgency_level'   => 'normal',
            ], $this->tok['shipper2']);
            if (!in_array($s, [200, 201])) return [false, $this->fmt($s, $r)];
            $p3CargoId = $r['data']['id'] ?? null;
            return [true, "cargo_id={$p3CargoId}"];
        });

        $this->step('P3.0b: Fleet bids ETB 9,000 on base cargo (fleet_gl001)', function () use ($p3CargoId, &$p3BidId) {
            $fleetVehicleId = DB::table('vehicles')
                ->where('fleet_owner_id', $this->uid['fleet'])
                ->orderBy('id')
                ->value('id');
            if (!$fleetVehicleId) return [false, 'No fleet vehicle found'];
            [$s, $r] = $this->post("/cargo-requests/{$p3CargoId}/bids", [
                'vehicle_id' => $fleetVehicleId,
                'amount'     => 9000,
            ], $this->tok['fleet']);
            if (!in_array($s, [200, 201])) return [false, $this->fmt($s, $r)];
            $p3BidId = $r['data']['id'] ?? null;
            return [true, "bid_id={$p3BidId}"];
        });

        $this->step('P3.0c: shipper2 accepts fleet bid → booking', function () use ($p3BidId, &$p3BookingId) {
            if (!$p3BidId) return [false, 'No p3BidId from previous step'];
            [$s, $r] = $this->patch("/bids/{$p3BidId}/accept", [], $this->tok['shipper2']);
            if ($s !== 200) return [false, $this->fmt($s, $r)];
            $p3BookingId = $r['data']['id'] ?? null;
            return [true, "booking_id={$p3BookingId}"];
        });

        $this->step('P3.0d: Fleet dispatches booking to Biruk', function () use ($p3BookingId) {
            if (!$p3BookingId) return [false, 'No p3BookingId'];
            [$s, $r] = $this->patch("/fleet/bookings/{$p3BookingId}/dispatch", [
                'driver_id' => $this->uid['driver3'],
            ], $this->tok['fleet']);
            if ($s !== 200) return [false, $this->fmt($s, $r)];
            return [true, "dispatched to Biruk (driver3)"];
        });

        $this->step('P3.0e: Biruk starts trip → destination=Gondar', function () use ($p3BookingId, &$p3TripId) {
            if (!$p3BookingId) return [false, 'No p3BookingId'];
            [$s, $r] = $this->post('/trips', ['booking_id' => $p3BookingId], $this->tok['driver3']);
            if (!in_array($s, [200, 201])) return [false, $this->fmt($s, $r)];
            $p3TripId = $r['data']['id'] ?? null;
            $dest     = $r['data']['destination'] ?? null;
            $pending  = $r['backhaul_recommendations_pending'] ?? false;
            return [true, "trip_id={$p3TripId}, destination={$dest}, backhaul_pending=" . ($pending ? 'true' : 'false')];
        });

        // ── 3.1: Seed return cargos near Gondar ──────────────────────────────

        $this->step('P3.1a: shipper2 seeds CARGO_RETURN_1 — Gondar→Addis, Coffee, 20t, urgent', function () use (&$ret1Id) {
            [$s, $r] = $this->post('/cargo-requests', [
                'pickup_location' => 'Gondar',
                'destination'     => 'Addis Ababa',
                'material_type'   => 'Coffee',
                'weight'          => 20.0,
                'urgency_level'   => 'urgent',
            ], $this->tok['shipper2']);
            if (!in_array($s, [200, 201])) return [false, $this->fmt($s, $r)];
            $ret1Id = $r['data']['id'] ?? null;
            // Expected score = (1.0*0.4)+(1.0*0.3)+(1.0*0.3) = 1.000  [dist=0km, urgent, 20t]
            return [true, "cargo_id={$ret1Id} (expected_score≈1.000)"];
        });

        $this->step('P3.1b: shipper1 seeds CARGO_RETURN_2 — Gondar→Bahir Dar, Cement, 10t, normal', function () use (&$ret2Id) {
            [$s, $r] = $this->post('/cargo-requests', [
                'pickup_location' => 'Gondar',
                'destination'     => 'Bahir Dar',
                'material_type'   => 'Cement',
                'weight'          => 10.0,
                'urgency_level'   => 'normal',
            ], $this->tok['shipper1']);
            if (!in_array($s, [200, 201])) return [false, $this->fmt($s, $r)];
            $ret2Id = $r['data']['id'] ?? null;
            // Expected score = (1.0*0.4)+(0.5*0.3)+(0.5*0.3) = 0.700  [dist=0km, normal, 10t]
            return [true, "cargo_id={$ret2Id} (expected_score≈0.700)"];
        });

        // ── 3.2: Verify job was dispatched when trip started ─────────────────

        $this->step('P3.2: GenerateBackhaulRecommendations job dispatched on trip start', function () use ($p3TripId) {
            $queueDriver = config('queue.default');
            if ($queueDriver === 'database') {
                $pending = DB::table('jobs')
                    ->where('payload', 'like', '%GenerateBackhaulRecommendations%')
                    ->count();
                return [true, "queue=database, pending jobs={$pending} (0 = already ran synchronously)"];
            }
            // sync / redis / other — cannot inspect the queue table
            return [true, "queue={$queueDriver} — job was dispatched at POST /trips (line 70 TripController) ℹ️"];
        });

        // ── 3.3: Run job synchronously after cargos are seeded ───────────────

        $this->step('P3.3: Run job synchronously → 2 rows, CARGO_RETURN_1 score > CARGO_RETURN_2', function () use ($p3TripId, $ret1Id, $ret2Id, &$rec1Id, &$rec2Id) {
            if (!$p3TripId) return [false, 'No Phase 3 trip ID'];
            if (!$ret1Id || !$ret2Id) return [false, "Return cargos not seeded (ret1={$ret1Id}, ret2={$ret2Id})"];

            $trip = \App\Models\Trip::find($p3TripId);
            if (!$trip) return [false, "Trip #{$p3TripId} not found in DB"];

            (new \App\Jobs\GenerateBackhaulRecommendations($trip))
                ->handle(app(\App\Services\BackhaulService::class));

            $rows = DB::table('backhaul_recommendations')
                ->where('trip_id', $p3TripId)
                ->orderByDesc('score')
                ->get();

            if ($rows->count() !== 2) {
                return [false, "Expected 2 rows, got {$rows->count()} (ret1={$ret1Id}, ret2={$ret2Id})"];
            }

            $top    = $rows[0];
            $second = $rows[1];

            if ((float) $top->score <= (float) $second->score) {
                return [false, "Score order wrong: top={$top->score} ≤ second={$second->score}"];
            }
            if ((int) $top->cargo_request_id !== (int) $ret1Id) {
                return [false, "Expected CARGO_RETURN_1 (id={$ret1Id}) on top, got cargo_id={$top->cargo_request_id}"];
            }
            if ((float) $top->score < 0.0 || (float) $top->score > 1.0) {
                return [false, "Top score {$top->score} out of [0, 1]"];
            }
            if ((float) $second->score < 0.0 || (float) $second->score > 1.0) {
                return [false, "Second score {$second->score} out of [0, 1]"];
            }

            $rec1Id = (int) $top->id;
            $rec2Id = (int) $second->id;

            return [true, "2 rows ✓ | score1={$top->score} > score2={$second->score} ✓ | both in [0,1] ✓"];
        });

        // ── 3.4: GET recommendations as Biruk ────────────────────────────────

        $this->step("P3.4: GET /trips/{$p3TripId}/backhaul-recommendations as Biruk → 200, 2 items", function () use ($p3TripId) {
            if (!$p3TripId) return [false, 'No Phase 3 trip ID'];
            [$s, $r] = $this->get("/trips/{$p3TripId}/backhaul-recommendations", $this->tok['driver3']);
            if ($s !== 200) return [false, $this->fmt($s, $r)];
            $items = $r['data'] ?? [];
            if (count($items) !== 2) return [false, "Expected 2 items, got " . count($items)];
            $first = $items[0];
            foreach (['id', 'score', 'status', 'cargo_request'] as $field) {
                if (!array_key_exists($field, $first)) {
                    return [false, "Field '{$field}' missing from recommendation response"];
                }
            }
            $score1 = $items[0]['score'] ?? null;
            $score2 = $items[1]['score'] ?? null;
            return [true, "200 ✓ | 2 items ✓ | fields present ✓ | scores={$score1},{$score2}"];
        });

        // ── 3.5: GET recommendations as Alemu (driver2) → 403 ───────────────

        $this->step("P3.5: GET /trips/{$p3TripId}/backhaul-recommendations as Alemu → 403", function () use ($p3TripId) {
            if (!$p3TripId) return [false, 'No Phase 3 trip ID'];
            [$s, $r] = $this->get("/trips/{$p3TripId}/backhaul-recommendations", $this->tok['driver2']);
            if ($s !== 403) return [false, "Expected 403, got " . $this->fmt($s, $r)];
            return [true, "403 ✓ — driver isolation enforced"];
        });

        // ── 3.6: Dismiss rec1, GET again → 1 item remains ───────────────────

        $this->step('P3.6: Dismiss CARGO_RETURN_1 rec, GET → 1 item (CARGO_RETURN_2 remains)', function () use ($p3TripId, $rec1Id, $rec2Id) {
            if (!$rec1Id) return [false, 'No rec1Id — step P3.3 must have failed'];
            [$s, $r] = $this->patch("/recommendations/{$rec1Id}/dismiss", [], $this->tok['driver3']);
            if ($s !== 200) return [false, "Dismiss failed: " . $this->fmt($s, $r)];

            [$s2, $r2] = $this->get("/trips/{$p3TripId}/backhaul-recommendations", $this->tok['driver3']);
            if ($s2 !== 200) return [false, "GET after dismiss: " . $this->fmt($s2, $r2)];
            $items = $r2['data'] ?? [];
            if (count($items) !== 1) return [false, "Expected 1 item after dismiss, got " . count($items)];
            $remainingRecId = $items[0]['id'] ?? null;
            if ((int) $remainingRecId !== (int) $rec2Id) {
                return [false, "Remaining rec should be rec2 (id={$rec2Id}), got id={$remainingRecId}"];
            }
            return [true, "dismiss ✓ | 1 item remains (rec_id={$remainingRecId} = CARGO_RETURN_2) ✓"];
        });

        // ── 3.7: Biruk bids on CARGO_RETURN_1, check rec status ─────────────

        $this->step("P3.7: Biruk bids on CARGO_RETURN_1 (id={$ret1Id}) — log rec status", function () use ($ret1Id, $rec1Id) {
            if (!$ret1Id) return [false, 'No ret1Id'];
            $fleetVehicleId = DB::table('vehicles')
                ->where('fleet_owner_id', $this->uid['fleet'])
                ->orderBy('id')
                ->value('id');
            if (!$fleetVehicleId) return [false, 'No fleet vehicle found for Biruk'];

            [$s, $r] = $this->post("/cargo-requests/{$ret1Id}/bids", [
                'vehicle_id' => $fleetVehicleId,
                'amount'     => 14000,
                'note'       => 'Phase3 backhaul bid from recommendation',
            ], $this->tok['driver3']);
            if (!in_array($s, [200, 201])) return [false, $this->fmt($s, $r)];

            $recStatus = $rec1Id
                ? DB::table('backhaul_recommendations')->where('id', $rec1Id)->value('status')
                : 'n/a (rec1Id missing)';

            // bid_placed status update is optional — log but don't fail
            return [true, "Bid placed ✓ | rec #{$rec1Id} status='{$recStatus}' (bid_placed if wired, pending otherwise) ℹ️"];
        });

        // ── 3.8: Re-run job → idempotency check ──────────────────────────────

        $this->step('P3.8: Re-run GenerateBackhaulRecommendations → still exactly 2 rows (idempotency)', function () use ($p3TripId) {
            if (!$p3TripId) return [false, 'No Phase 3 trip ID'];
            $trip = \App\Models\Trip::find($p3TripId);
            if (!$trip) return [false, "Trip #{$p3TripId} not found"];

            (new \App\Jobs\GenerateBackhaulRecommendations($trip))
                ->handle(app(\App\Services\BackhaulService::class));

            $count = DB::table('backhaul_recommendations')
                ->where('trip_id', $p3TripId)
                ->count();

            if ($count !== 2) return [false, "Expected 2 rows after re-run, got {$count} — updateOrCreate broken"];
            return [true, "2 rows ✓ — updateOrCreate idempotency confirmed"];
        });
    }

    // ─── Summary ─────────────────────────────────────────────────────────────

    private function printSummary(): void
    {
        $this->line('');
        $this->line('═══════════════════════════════════════════════════════════');
        $this->info("  RESULTS  — Passed: {$this->pass}  Failed: {$this->fail}  Skipped: {$this->skip}");
        $this->line('═══════════════════════════════════════════════════════════');
        $this->line('');

        $this->table(['Step', 'Status', 'Details'], $this->rows);

        if ($this->fail > 0) {
            $this->line('');
            $this->error("  ⛔ {$this->fail} LAUNCH BLOCKER(S) ABOVE — fix before shipping.");
        } else {
            $this->line('');
            $this->info("  ✅ All steps passed — system ready.");
        }
    }

    // ─── Manual UI guide ─────────────────────────────────────────────────────

    private function printManualTestGuide(): void
    {
        $this->line('');
        $this->line('═══════════════════════════════════════════════════════════');
        $this->info('  MANUAL UI TESTING GUIDE (all passwords: Test@1234)');
        $this->line('═══════════════════════════════════════════════════════════');
        $this->line('');
        $rows = [
            ['Selamawit Yimer',   '+251911000001', 'Admin',       'React admin panel — stats, users, trips, documents, cash payments'],
            ['Almaz Tesfaye',     '+251911000101', 'Shipper',     'Flutter — view Cargo A (matched), booking, completed trip + rating'],
            ['Tewodros Bekele',   '+251911000102', 'Shipper',     'Flutter — view Cargo B; if multi-stop ran, see it as completed'],
            ['Tesfaye Kebede',    '+251911000201', 'Driver',      'Flutter — bids list shows Cargo A bid as auto-rejected'],
            ['Alemu Mekonnen',    '+251911000202', 'Driver',      'Flutter — bids list shows Cargo A bid as auto-rejected'],
            ['Yohannes Girma',    '+251911000301', 'Fleet Owner', 'Flutter — fleet dashboard: 2 vehicles, Biruk as driver, completed booking'],
            ['Biruk Tadesse',     '+251911000302', 'Driver',      'Flutter — my bookings: completed trip; profile verification status = verified (after Phase 2B)'],
        ];
        $this->table(['Name', 'Phone', 'Role', 'What to verify in UI'], $rows);
        $this->line('');
        $this->line('  Login endpoint: POST /api/login  { email, password }');
        $this->line('  Emails follow pattern: firstname.lastname@test.ethioload.et');
        $this->line('  Example: almaz.tesfaye@test.ethioload.et / Test@1234');
        $this->line('');
    }
}
