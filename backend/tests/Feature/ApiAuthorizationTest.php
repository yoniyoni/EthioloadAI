<?php

namespace Tests\Feature;

use App\Models\Booking;
use App\Models\CargoRequest;
use App\Models\User;
use App\Models\Vehicle;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Http;
use Tests\TestCase;

class ApiAuthorizationTest extends TestCase
{
    use RefreshDatabase;

    public function test_admin_can_access_user_management_routes(): void
    {
        $admin = User::create([
            'full_name' => 'Admin User',
            'phone' => '0911000000',
            'email' => 'admin@example.com',
            'password' => bcrypt('password'),
            'role' => 'admin',
        ]);

        $response = $this->actingAs($admin, 'sanctum')->getJson('/api/users');
        $response->assertStatus(200);
    }

    public function test_non_admin_cannot_access_user_management_routes(): void
    {
        $user = User::create([
            'full_name' => 'Normal User',
            'phone' => '0911000001',
            'email' => 'user@example.com',
            'password' => bcrypt('password'),
            'role' => 'shipper',
        ]);

        $response = $this->actingAs($user, 'sanctum')->getJson('/api/users');
        $response->assertStatus(403);
    }

    public function test_authenticated_user_can_create_cargo_request(): void
    {
        $user = User::create([
            'full_name' => 'Cargo User',
            'phone' => '0911000002',
            'email' => 'cargo@example.com',
            'password' => bcrypt('password'),
            'role' => 'shipper',
        ]);

        $payload = [
            'pickup_location' => 'Addis Ababa',
            'destination' => 'Bahir Dar',
            'material_type' => 'general',
            'weight' => 10.5,
            'urgency_level' => 'normal',
            'budget' => 15000,
        ];

        $response = $this->actingAs($user, 'sanctum')->postJson('/api/cargo-requests', $payload);
        $response->assertStatus(201)->assertJsonPath('data.pickup_location', 'Addis Ababa');
    }

    public function test_shipper_can_create_booking_for_own_cargo_request(): void
    {
        $shipper = User::create([
            'full_name' => 'Cargo Owner',
            'phone' => '0911000003',
            'email' => 'owner@example.com',
            'password' => bcrypt('password'),
            'role' => 'shipper',
        ]);

        $driver = User::create([
            'full_name' => 'Driver User',
            'phone' => '0911000004',
            'email' => 'driver@example.com',
            'password' => bcrypt('password'),
            'role' => 'driver',
        ]);

        $vehicle = Vehicle::create([
            'user_id' => $driver->id,
            'truck_type' => 'Flatbed',
            'plate_number' => 'ET8888Z',
            'capacity' => 25,
            'current_city' => 'Addis Ababa',
            'latitude' => 9.03,
            'longitude' => 38.74,
            'availability_status' => 'available',
            'rating' => 4.6,
        ]);

        $cargoRequest = CargoRequest::create([
            'user_id' => $shipper->id,
            'pickup_location' => 'Addis Ababa',
            'destination' => 'Hawassa',
            'material_type' => 'general',
            'weight' => 12.5,
            'urgency_level' => 'normal',
            'budget' => 10000,
            'status' => 'pending',
        ]);

        $payload = [
            'cargo_id' => $cargoRequest->id,
            'vehicle_id' => $vehicle->id,
            'driver_id' => $driver->id,
            'booking_status' => 'confirmed',
            'estimated_price' => 14000,
            'commission_fee' => 1000,
        ];

        $response = $this->actingAs($shipper, 'sanctum')->postJson('/api/bookings', $payload);
        $response->assertStatus(201)
            ->assertJsonPath('data.cargo_id', $cargoRequest->id)
            ->assertJsonPath('data.driver_id', $driver->id);
    }

    public function test_shipper_cannot_create_booking_for_other_cargo_request(): void
    {
        $shipper = User::create([
            'full_name' => 'Cargo Owner',
            'phone' => '0911000005',
            'email' => 'owner2@example.com',
            'password' => bcrypt('password'),
            'role' => 'shipper',
        ]);

        $otherUser = User::create([
            'full_name' => 'Other Shipper',
            'phone' => '0911000006',
            'email' => 'other@example.com',
            'password' => bcrypt('password'),
            'role' => 'shipper',
        ]);

        $driver = User::create([
            'full_name' => 'Driver User 2',
            'phone' => '0911000007',
            'email' => 'driver2@example.com',
            'password' => bcrypt('password'),
            'role' => 'driver',
        ]);

        $vehicle = Vehicle::create([
            'user_id' => $driver->id,
            'truck_type' => 'Covered',
            'plate_number' => 'ET9999Y',
            'capacity' => 20,
            'current_city' => 'Bahir Dar',
            'latitude' => 11.6,
            'longitude' => 37.4,
            'availability_status' => 'available',
            'rating' => 4.3,
        ]);

        $cargoRequest = CargoRequest::create([
            'user_id' => $otherUser->id,
            'pickup_location' => 'Gondar',
            'destination' => 'Addis Ababa',
            'material_type' => 'construction',
            'weight' => 20.0,
            'urgency_level' => 'high',
            'budget' => 18000,
            'status' => 'pending',
        ]);

        $payload = [
            'cargo_id' => $cargoRequest->id,
            'vehicle_id' => $vehicle->id,
            'driver_id' => $driver->id,
            'booking_status' => 'confirmed',
            'estimated_price' => 17500,
            'commission_fee' => 1500,
        ];

        $response = $this->actingAs($shipper, 'sanctum')->postJson('/api/bookings', $payload);
        $response->assertStatus(403);
    }

    public function test_driver_can_create_booking_for_self(): void
    {
        $shipper = User::create([
            'full_name' => 'Cargo Owner',
            'phone' => '0911000010',
            'email' => 'owner3@example.com',
            'password' => bcrypt('password'),
            'role' => 'shipper',
        ]);

        $driver = User::create([
            'full_name' => 'Driver User 3',
            'phone' => '0911000011',
            'email' => 'driver3@example.com',
            'password' => bcrypt('password'),
            'role' => 'driver',
        ]);

        $vehicle = Vehicle::create([
            'user_id' => $driver->id,
            'truck_type' => 'Refrigerated',
            'plate_number' => 'ET3333F',
            'capacity' => 30,
            'current_city' => 'Addis Ababa',
            'latitude' => 9.03,
            'longitude' => 38.74,
            'availability_status' => 'available',
            'rating' => 4.8,
        ]);

        $cargoRequest = CargoRequest::create([
            'user_id' => $shipper->id,
            'pickup_location' => 'Addis Ababa',
            'destination' => 'Hawassa',
            'material_type' => 'perishable',
            'weight' => 8.0,
            'urgency_level' => 'express',
            'budget' => 20000,
            'status' => 'pending',
        ]);

        $payload = [
            'cargo_id' => $cargoRequest->id,
            'vehicle_id' => $vehicle->id,
            'driver_id' => $driver->id,
            'booking_status' => 'confirmed',
            'estimated_price' => 18000,
            'commission_fee' => 1200,
        ];

        $response = $this->actingAs($driver, 'sanctum')->postJson('/api/bookings', $payload);
        $response->assertStatus(201)
            ->assertJsonPath('data.driver_id', $driver->id)
            ->assertJsonPath('data.cargo_id', $cargoRequest->id);
    }

    public function test_unrelated_user_cannot_view_booking(): void
    {
        $driver = User::create([
            'full_name' => 'Driver User 4',
            'phone' => '0911000012',
            'email' => 'driver4@example.com',
            'password' => bcrypt('password'),
            'role' => 'driver',
        ]);

        $shipper = User::create([
            'full_name' => 'Cargo Owner 2',
            'phone' => '0911000013',
            'email' => 'owner4@example.com',
            'password' => bcrypt('password'),
            'role' => 'shipper',
        ]);

        $vehicle = Vehicle::create([
            'user_id' => $driver->id,
            'truck_type' => 'Open Bed',
            'plate_number' => 'ET4444G',
            'capacity' => 22,
            'current_city' => 'Bahir Dar',
            'latitude' => 11.6,
            'longitude' => 37.4,
            'availability_status' => 'available',
            'rating' => 4.2,
        ]);

        $cargoRequest = CargoRequest::create([
            'user_id' => $shipper->id,
            'pickup_location' => 'Gondar',
            'destination' => 'Addis Ababa',
            'material_type' => 'construction',
            'weight' => 17.0,
            'urgency_level' => 'normal',
            'budget' => 16000,
            'status' => 'pending',
        ]);

        $booking = Booking::create([
            'cargo_id' => $cargoRequest->id,
            'vehicle_id' => $vehicle->id,
            'driver_id' => $driver->id,
            'booking_status' => 'confirmed',
            'estimated_price' => 16500,
            'commission_fee' => 1100,
        ]);

        $otherUser = User::create([
            'full_name' => 'Other User',
            'phone' => '0911000014',
            'email' => 'other2@example.com',
            'password' => bcrypt('password'),
            'role' => 'shipper',
        ]);

        $response = $this->actingAs($otherUser, 'sanctum')->getJson("/api/bookings/{$booking->id}");
        $response->assertStatus(403);
    }

    public function test_ai_recommend_truck_proxy_endpoint_returns_response(): void
    {
        Http::fake([
            'http://localhost:8000/ai/recommend-truck' => Http::response([
                'recommended_trucks' => [
                    [
                        'truck_id' => 1,
                        'driver_name' => 'Test Driver',
                        'plate_number' => 'ET5555H',
                        'capacity' => 40.0,
                        'distance_km' => 120.0,
                        'estimated_price' => 22000,
                        'score' => 0.91,
                    ],
                ],
            ], 200),
        ]);

        $user = User::create([
            'full_name' => 'AI User',
            'phone' => '0911000015',
            'email' => 'aiuser@example.com',
            'password' => bcrypt('password'),
            'role' => 'shipper',
        ]);

        $payload = [
            'pickup_location' => 'Addis Ababa',
            'destination' => 'Bahir Dar',
            'material_type' => 'general',
            'weight' => 15.0,
            'urgency_level' => 'normal',
        ];

        $response = $this->actingAs($user, 'sanctum')->postJson('/api/ai/recommend-truck', $payload);
        $response->assertStatus(200)
            ->assertJsonCount(1, 'recommended_trucks')
            ->assertJsonPath('recommended_trucks.0.truck_id', 1);
    }

    public function test_ai_backhaul_opportunities_proxy_endpoint_returns_response(): void
    {
        Http::fake([
            'http://localhost:8000/ai/backhaul-opportunities' => Http::response([
                'opportunities' => [
                    [
                        'cargo_id' => 301,
                        'pickup_location' => 'Bahir Dar',
                        'destination' => 'Addis Ababa',
                        'weight' => 14.0,
                        'price' => 11500,
                        'score' => 0.87,
                    ],
                ],
            ], 200),
        ]);

        $user = User::create([
            'full_name' => 'AI User 2',
            'phone' => '0911000016',
            'email' => 'aiuser2@example.com',
            'password' => bcrypt('password'),
            'role' => 'driver',
        ]);

        $payload = [
            'truck_id' => 1,
            'current_location' => 'Bahir Dar',
            'destination' => 'Addis Ababa',
        ];

        $response = $this->actingAs($user, 'sanctum')->postJson('/api/ai/backhaul-opportunities', $payload);
        $response->assertStatus(200)
            ->assertJsonCount(1, 'opportunities')
            ->assertJsonPath('opportunities.0.cargo_id', 301);
    }
    public function test_ai_predict_price_proxy_endpoint_returns_response(): void
    {
        Http::fake([
            'http://localhost:8000/ai/predict-price' => Http::response([
                'estimated_price' => 14500,
                'confidence' => 0.94,
            ], 200),
        ]);

        $user = User::create([
            'full_name' => 'AI User 3',
            'phone' => '0911000017',
            'email' => 'aiuser3@example.com',
            'password' => bcrypt('password'),
            'role' => 'shipper',
        ]);

        $payload = [
            'pickup_location' => 'Addis Ababa',
            'destination' => 'Bahir Dar',
            'weight' => 18.0,
            'material_type' => 'general',
        ];

        $response = $this->actingAs($user, 'sanctum')->postJson('/api/ai/predict-price', $payload);
        $response->assertStatus(200)
            ->assertJsonPath('estimated_price', 14500)
            ->assertJsonPath('confidence', 0.94);
    }

    public function test_driver_can_update_vehicle_location(): void
    {
        $driver = User::create([
            'full_name' => 'Location Driver',
            'phone' => '0911000018',
            'email' => 'locationdriver@example.com',
            'password' => bcrypt('password'),
            'role' => 'driver',
        ]);

        $vehicle = Vehicle::create([
            'user_id' => $driver->id,
            'truck_type' => 'Tanker',
            'plate_number' => 'ET7777J',
            'capacity' => 28,
            'current_city' => 'Addis Ababa',
            'latitude' => 9.03,
            'longitude' => 38.74,
            'availability_status' => 'available',
            'rating' => 4.9,
        ]);

        $payload = [
            'latitude' => 9.013,
            'longitude' => 38.745,
            'accuracy' => 5.4,
        ];

        $response = $this->actingAs($driver, 'sanctum')->patchJson("/api/vehicles/{$vehicle->id}/location", $payload);
        $response->assertStatus(200)
            ->assertJsonPath('data.latitude', 9.013)
            ->assertJsonPath('data.longitude', 38.745);
    }}
