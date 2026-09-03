<?php

namespace App\Providers;

use App\Services\ChapaService;
use App\Services\MockChapaService;
use App\Services\MockSmsService;
use App\Services\SmsService;
use Illuminate\Cache\RateLimiting\Limit;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\RateLimiter;
use Illuminate\Support\ServiceProvider;

class AppServiceProvider extends ServiceProvider
{
    public function register(): void
    {
        // Every existing PaymentService/PaymentController call site
        // type-hints ChapaService — swapping the payment gateway later is
        // just flipping PAYMENT_GATEWAY_DRIVER to 'chapa', no other change.
        $this->app->bind(ChapaService::class, function () {
            return config('services.payment_gateway.driver') === 'mock'
                ? new MockChapaService()
                : new ChapaService();
        });

        // Same pattern for SMS (used by PasswordResetService): flip
        // SMS_GATEWAY_DRIVER once a real provider is wired into SmsService,
        // no other call site changes.
        $this->app->bind(SmsService::class, function () {
            return config('services.sms_gateway.driver') === 'mock'
                ? new MockSmsService()
                : new SmsService();
        });
    }

    public function boot(): void
    {
        // General API: 60 req/min per authenticated user or IP
        RateLimiter::for('api', function (Request $request) {
            return Limit::perMinute(60)->by(
                $request->user()?->id ?: $request->ip()
            );
        });

        // Login: max 10 attempts/min per IP to prevent brute-force
        RateLimiter::for('login', function (Request $request) {
            return Limit::perMinute(10)->by($request->ip());
        });

        // Password reset request/verify: 5/min per IP. The real defense
        // against brute-forcing the 6-digit code itself is the `attempts`
        // counter on the password_reset_codes row (PasswordResetService);
        // this is the secondary layer, same relationship as `login` has to
        // the password hash it's protecting.
        RateLimiter::for('password-reset', function (Request $request) {
            return Limit::perMinute(5)->by($request->ip());
        });
    }
}
