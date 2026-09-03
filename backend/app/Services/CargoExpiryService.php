<?php

namespace App\Services;

use App\Models\Bid;
use App\Models\CargoRequest;
use App\Notifications\BidRejectedNotification;
use App\Notifications\CargoBiddingExpiredNotification;
use Illuminate\Support\Facades\DB;

/**
 * Closes bidding on cargo whose bid_deadline has passed without a booking.
 * Called from two places (both safe to call redundantly — see expireOverdue):
 *  - php artisan cargo:expire-overdue, scheduled via routes/console.php for a
 *    real cron/Task Scheduler hookup.
 *  - Lazily, at the top of CargoRequestController's browse/list endpoints, so
 *    the state is correct on every read even with no scheduler running at all
 *    (true of this dev environment today — see the feature's plan for why).
 */
class CargoExpiryService
{
    /**
     * Expires every 'pending' cargo whose bid_deadline has passed, notifying
     * the shipper once and every driver with a still-open bid on it.
     * Idempotent: a cargo that's already 'expired' no longer matches the
     * query, so calling this repeatedly is always safe.
     */
    public function expireOverdue(): int
    {
        $overdueIds = CargoRequest::where('status', 'pending')
            ->whereNotNull('bid_deadline')
            ->where('bid_deadline', '<', now())
            ->pluck('id');

        $expiredCount = 0;

        foreach ($overdueIds as $cargoId) {
            $expired = DB::transaction(function () use ($cargoId) {
                // Re-check everything under lock — a booking may have been
                // created (or the deadline extended via repost) between the
                // query above and this transaction starting.
                $cargo = CargoRequest::whereKey($cargoId)->lockForUpdate()->first();

                if (
                    !$cargo
                    || $cargo->status !== 'pending'
                    || !$cargo->bid_deadline
                    || now()->lte($cargo->bid_deadline)
                ) {
                    return false;
                }

                $cargo->update(['status' => 'expired']);

                $openBids = Bid::where('cargo_request_id', $cargo->id)
                    ->whereIn('status', ['pending', 'countered'])
                    ->with('driver')
                    ->get();

                foreach ($openBids as $bid) {
                    $bid->update(['status' => 'expired']);
                    $bid->driver?->notify(new BidRejectedNotification($bid, 'deadline_passed'));
                }

                $cargo->user?->notify(new CargoBiddingExpiredNotification($cargo));

                return true;
            });

            if ($expired) {
                $expiredCount++;
            }
        }

        return $expiredCount;
    }
}
