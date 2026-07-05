<?php

use Illuminate\Support\Facades\Auth;
use Illuminate\Support\Facades\Route;
use Illuminate\Http\Request;
use App\Http\Middleware\AdminMiddleware;

Route::aliasMiddleware('admin', AdminMiddleware::class);

Route::get('/', function () {
    return view('welcome');
});

Route::middleware('guest')->group(function () {
    Route::get('/admin/login', function () {
        return view('admin.login');
    })->name('admin.login');

    Route::post('/admin/login', function (Request $request) {
        $credentials = $request->validate([
            'email' => ['required', 'email'],
            'password' => ['required', 'string'],
        ]);

        if (!Auth::attempt($credentials)) {
            return back()->withErrors(['email' => 'Invalid credentials'])->withInput();
        }

        $user = Auth::user();
        if (!$user->is_admin) {
            Auth::logout();
            return back()->withErrors(['email' => 'Access denied. Admin account required.'])->withInput();
        }

        $token = $user->createToken('admin-panel')->plainTextToken;
        session(['admin_api_token' => $token]);
        $request->session()->regenerate();

        return redirect()->intended('/admin');
    });
});

Route::post('/admin/logout', function (Request $request) {
    Auth::logout();
    $request->session()->forget('admin_api_token');
    $request->session()->invalidate();
    $request->session()->regenerateToken();
    return redirect('/admin/login');
})->name('admin.logout');

Route::middleware(['auth', 'admin'])->group(function () {
    Route::get('/admin', function () {
        return view('admin.layout');
    });
});

Route::get('/login', function () {
    return redirect('/admin/login');
})->name('login');

require __DIR__.'/admin.php';
