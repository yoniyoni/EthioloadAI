<?php

namespace Database\Seeders;

use App\Models\User;
use Illuminate\Database\Seeder;
use Illuminate\Support\Facades\Hash;

class DatabaseSeeder extends Seeder
{
    /**
     * Seed the application's database with test accounts for all roles.
     *
     * Credentials (all use password: password123):
     *   Shipper  → shipper@ethioload.test
     *   Driver   → driver@ethioload.test
     *   Admin    → admin@ethioload.test
     */
    public function run(): void
    {
        $users = [
            // Legacy test accounts (password: password123)
            [
                'full_name' => 'Test Shipper',
                'email'     => 'shipper@ethioload.test',
                'phone'     => '+251911000001',
                'role'      => 'shipper',
                'password'  => 'password123',
            ],
            [
                'full_name' => 'Test Driver',
                'email'     => 'driver@ethioload.test',
                'phone'     => '+251911000002',
                'role'      => 'driver',
                'password'  => 'password123',
            ],
            [
                'full_name' => 'Test Admin',
                'email'     => 'admin@ethioload.test',
                'phone'     => '+251911000003',
                'role'      => 'admin',
                'password'  => 'password123',
            ],
            // React admin panel demo accounts (shown on login page)
            [
                'full_name' => 'Admin',
                'email'     => 'admin@freightlink.et',
                'phone'     => '+251911000011',
                'role'      => 'admin',
                'password'  => 'admin123',
            ],
            [
                'full_name' => 'Tigist Mesfin',
                'email'     => 'tigist@shipper.et',
                'phone'     => '+251911000012',
                'role'      => 'shipper',
                'password'  => 'shipper123',
            ],
            [
                'full_name' => 'Bekele Girma',
                'email'     => 'bekele@driver.et',
                'phone'     => '+251911000013',
                'role'      => 'driver',
                'password'  => 'driver123',
            ],
        ];

        foreach ($users as $data) {
            User::firstOrCreate(
                ['email' => $data['email']],
                [
                    'full_name' => $data['full_name'],
                    'phone'     => $data['phone'],
                    'role'      => $data['role'],
                    'password'  => Hash::make($data['password']),
                ]
            );
        }
    }
}
