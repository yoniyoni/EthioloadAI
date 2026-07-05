<!DOCTYPE html>
<html lang="en" data-theme="light">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Admin Login - EthioLoadAI</title>
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
    <style>
        body { background: #f3f4f6; font-family: 'Inter', sans-serif; }
        .login-card { max-width: 440px; margin: 80px auto; border-radius: 18px; box-shadow: 0 18px 50px rgba(15, 23, 42, 0.08); }
        .login-card .card-body { padding: 40px; }
        .brand-title { font-weight: 800; color: #111827; }
        .brand-title span { color: #4361ee; }
    </style>
</head>
<body>
    <div class="container">
        <div class="card login-card">
            <div class="card-body">
                <div class="text-center mb-4">
                    <div class="display-6 brand-title">EthioLoad<span>AI</span></div>
                    <p class="text-muted">Administrator login for the logistics admin dashboard.</p>
                </div>

                @if($errors->any())
                    <div class="alert alert-danger">
                        {{ $errors->first() }}
                    </div>
                @endif

                <form method="POST" action="{{ url('/admin/login') }}">
                    @csrf
                    <div class="mb-3">
                        <label for="email" class="form-label">Email address</label>
                        <input type="email" class="form-control" id="email" name="email" value="{{ old('email') }}" required autofocus>
                    </div>
                    <div class="mb-4">
                        <label for="password" class="form-label">Password</label>
                        <input type="password" class="form-control" id="password" name="password" required>
                    </div>
                    <button type="submit" class="btn btn-primary w-100">Sign in</button>
                </form>
                <div class="text-center mt-4 text-muted">
                    <small>Use your admin credentials to manage users, vehicles, cargo and AI workflow.</small>
                </div>
            </div>
        </div>
    </div>
</body>
</html>
