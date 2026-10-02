<?php

use Illuminate\Foundation\Inspiring;
use Illuminate\Support\Facades\Artisan;
use Illuminate\Support\Facades\Schedule;

Artisan::command('inspire', function () {
    $this->comment(Inspiring::quote());
})->purpose('Display an inspiring quote');

// Closes bidding on cargo past its bid_deadline with no booking made — see
// CargoExpiryService. This only runs if something calls `artisan schedule:run`
// every minute (a real cron entry in production). Nothing in this dev
// environment does that yet, which is why CargoRequestController also calls
// the same service lazily on the browse/list endpoints — see there for why.
Schedule::command('cargo:expire-overdue')->everyMinute();
