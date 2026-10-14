<?php

namespace App\Console\Commands;

use App\Services\CargoExpiryService;
use Illuminate\Console\Command;

class ExpireOverdueCargo extends Command
{
    protected $signature = 'cargo:expire-overdue';

    protected $description = 'Close bidding on pending cargo whose bid_deadline has passed without a booking, and notify the shipper + affected drivers.';

    public function handle(CargoExpiryService $service): int
    {
        $count = $service->expireOverdue();

        $this->info($count === 1 ? '1 cargo request expired.' : "{$count} cargo requests expired.");

        return self::SUCCESS;
    }
}
